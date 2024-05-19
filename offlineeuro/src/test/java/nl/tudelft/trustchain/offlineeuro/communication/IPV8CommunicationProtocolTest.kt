package nl.tudelft.trustchain.offlineeuro.communication

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.ipv8.Peer
import nl.tudelft.offlineeuro.sqldelight.Database
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
import nl.tudelft.trustchain.offlineeuro.community.message.BilinearGroupCRSReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BilinearGroupCRSRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureRandomnessReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureRandomnessRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionRandomizationElementsReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionRandomizationElementsRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionResultMessage
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.CRSGenerator
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahai
import nl.tudelft.trustchain.offlineeuro.cryptography.PairingTypes
import nl.tudelft.trustchain.offlineeuro.db.AddressBookManager
import nl.tudelft.trustchain.offlineeuro.entity.Address
import nl.tudelft.trustchain.offlineeuro.entity.Bank
import nl.tudelft.trustchain.offlineeuro.entity.CentralAuthority
import nl.tudelft.trustchain.offlineeuro.entity.TransactionDetails
import nl.tudelft.trustchain.offlineeuro.entity.TransactionDetailsBytes
import nl.tudelft.trustchain.offlineeuro.entity.User
import nl.tudelft.trustchain.offlineeuro.enums.Role
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.math.BigInteger


class IPV8CommunicationProtocolTest {

    private val context = null
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).apply {
        Database.Schema.create(this)
    }

    private val community: OfflineEuroCommunity = Mockito.mock(OfflineEuroCommunity::class.java)
    // Setup the TTP
    private val ttpBilinearGroup = BilinearGroup(PairingTypes.FromFile)
    private val ttpCRS = CRSGenerator.generateCRSMap(ttpBilinearGroup)
    private val ttpPK = ttpBilinearGroup.generateRandomElementOfG()
    private val ttpAddress = Address("TTP", Role.Bank, ttpPK, "TTPPublicKey".toByteArray())

    // Set up bank
    private val bankPK = ttpBilinearGroup.generateRandomElementOfG()
    private val bankAddress = Address("Bank", Role.Bank, bankPK, "BankPublicKey".toByteArray())
    private val blindSignatureRandomness = ttpBilinearGroup.generateRandomElementOfG()
    private val blindSignature = BigInteger("1236231234124321421")

    // Set up receiver
    private val receiverPK = ttpBilinearGroup.generateRandomElementOfG()
    private val receiverAddress = Address("Receiver", Role.User, receiverPK, "UserPublicKey".toByteArray())
    private val randomizationElements = GrothSahai.tToRandomizationElements(ttpBilinearGroup.getRandomZr(), ttpBilinearGroup, ttpCRS.first)
    private val transactionResult = "Successful Transaction!"

    private val initialBilinearGroup = BilinearGroup(PairingTypes.FromFile)
    private val addressBookManager = AddressBookManager(context, initialBilinearGroup, driver)

    // Used Mocks
    private val bank = Mockito.mock(Bank::class.java)
    private val userRandomness: HashMap<Element, Element> = hashMapOf()

    private val user = Mockito.mock(User::class.java)
    private val receivingPeer = Mockito.mock(Peer::class.java)

    private val iPV8CommunicationProtocol = IPV8CommunicationProtocol(addressBookManager, community, user, bank)

    @Before
    fun setup() {
        `when` (community.messageList).thenReturn(iPV8CommunicationProtocol.messageList)

        `when`(community.getGroupDescriptionAndCRS()).then {
            val message = BilinearGroupCRSReplyMessage(ttpBilinearGroup.toGroupElementBytes(), ttpCRS.first.toCRSBytes())
            community.messageList.add(message)
        }

        `when`(community.sendGroupDescriptionAndCRS(any(), any(), any())).then {  }

        `when`(community.registerAtTTP(any(), any(), any())).then { }

        `when`(community.sendBlindSignatureRandomnessReply(any(), any())).then { }
        `when`(community.sendBlindSignature(any(), any())).then { }

        `when`(community.getBlindSignatureRandomness(any(), any())).then {
            val message = BlindSignatureRandomnessReplyMessage(blindSignatureRandomness.toBytes())
            community.messageList.add(message)
        }

        `when`(community.getBlindSignature(any(), any(), any())).then {
            val message = BlindSignatureReplyMessage(blindSignature)
            community.messageList.add(message)
        }

        `when`(community.getTransactionRandomizationElements(any())).then {
            val message = TransactionRandomizationElementsReplyMessage(randomizationElements.toRandomizationElementsBytes())
            community.messageList.add(message)
        }

        `when`(community.sendTransactionDetails(any(), any())).then {
            val message = TransactionResultMessage(transactionResult)
            community.messageList.add(message)
        }

        addressBookManager.insertAddress(ttpAddress)

    }

    @Test
    fun getGroupDescriptionAndCRSTest() {
        val (groupDescription, crs) = iPV8CommunicationProtocol.getGroupDescriptionAndCRS()
        Assert.assertEquals(ttpBilinearGroup.pairing, groupDescription.pairing)
        Assert.assertEquals(ttpBilinearGroup.g, groupDescription.g)
        Assert.assertEquals(ttpBilinearGroup.h, groupDescription.h)
        Assert.assertEquals(ttpBilinearGroup.gt, groupDescription.gt)
        Assert.assertEquals("The crs should be correct", ttpCRS.first, crs)
    }

    @Test
    fun sendBilinearGroupAndCRSTest() {
        val message = BilinearGroupCRSRequestMessage(receivingPeer)

        val expectedGroupElements = CentralAuthority.groupDescription.toGroupElementBytes()
        val expectedCRSBytes = CentralAuthority.crs.toCRSBytes()

        community.messageList.add(message)
        verify(community, times(1))
            .sendGroupDescriptionAndCRS(expectedGroupElements, expectedCRSBytes, receivingPeer)
    }

    @Test
    fun registrationTest() {
        val publicKey = ttpBilinearGroup.generateRandomElementOfG()
        val userName = "UserTryingToRegister"
        iPV8CommunicationProtocol.register(userName, publicKey, ttpAddress.name)
        // Assert that the registration request is sent correctly
        verify(community, times(1)).registerAtTTP(userName, publicKey.toBytes(), ttpAddress.peerPublicKey!!)
    }

    @Test
    fun getBlindSignatureRandomnessTest() {
        addressBookManager.insertAddress(bankAddress)
        val publicKey = ttpBilinearGroup.generateRandomElementOfG()
        val randomness = iPV8CommunicationProtocol.getBlindSignatureRandomness(publicKey, bankAddress.name, ttpBilinearGroup)
        verify(community, times(1)).getBlindSignatureRandomness(publicKey.toBytes(), bankAddress.peerPublicKey!!)
        Assert.assertEquals("The randomness should be correct", blindSignatureRandomness, randomness)
    }

    @Test
    fun sendBlindSignatureRandomnessTest() {
        val givenRandomness = CentralAuthority.groupDescription.generateRandomElementOfG()
        val publicKey = CentralAuthority.groupDescription.generateRandomElementOfG()
        `when`(bank.getBlindSignatureRandomness(any())).thenReturn(givenRandomness)

        val requestMessage = BlindSignatureRandomnessRequestMessage(publicKey.toBytes(), receivingPeer)
        community.messageList.add(requestMessage)

        verify(bank, times(1)).getBlindSignatureRandomness(publicKey)
        verify(community, times(1)).sendBlindSignatureRandomnessReply(givenRandomness.toBytes(), receivingPeer)
    }

    @Test
    fun requestBlindSignatureTest() {
        addressBookManager.insertAddress(bankAddress)
        val publicKey = ttpBilinearGroup.generateRandomElementOfG()
        val challenge = BigInteger("12352132521521321521312")
        val signature = iPV8CommunicationProtocol.requestBlindSignature(publicKey, bankAddress.name, challenge)
        verify(community, times(1)).getBlindSignature(challenge, publicKey.toBytes(), bankAddress.peerPublicKey!!)
        Assert.assertEquals("The returned signature should be correct", blindSignature, signature)
    }

    @Test
    fun sendBlindSignatureTest() {
        val challenge = BigInteger("321321521421097502142")
        val publicKey = CentralAuthority.groupDescription.generateRandomElementOfG()
        val signature = BigInteger("2457921903721896428193682163921")
        `when`(bank.createBlindSignature(challenge, publicKey)).thenReturn(signature)

        val blindSignatureRequestMessage = BlindSignatureRequestMessage(challenge, publicKey.toBytes(), receivingPeer)
        community.messageList.add(blindSignatureRequestMessage)

        verify(bank, times(1)).createBlindSignature(challenge, publicKey)
        verify(community, times(1)).sendBlindSignature(signature, receivingPeer)
    }

    @Test
    fun requestTransactionRandomnessTest() {
        addressBookManager.insertAddress(receiverAddress)
        val transactionRandomness = iPV8CommunicationProtocol.requestTransactionRandomness(receiverAddress.name, ttpBilinearGroup)
        verify(community, times(1)).getTransactionRandomizationElements(receiverAddress.peerPublicKey!!)
        Assert.assertEquals("The transaction randomness should be correct", randomizationElements, transactionRandomness)
    }

    @Test
    fun sendTransactionRandomnessTest() {
        val randomT = ttpBilinearGroup.getRandomZr()
        val randomizationElements = GrothSahai.tToRandomizationElements(randomT, ttpBilinearGroup, ttpCRS.first)
        val publicKey = ttpBilinearGroup.generateRandomElementOfG()
        `when`(user.group).thenReturn(ttpBilinearGroup)
        `when`(user.generateRandomizationElements(any())).thenReturn(randomizationElements)
        `when`(community.sendTransactionRandomizationElements(any(), any())).then {  }


        val requestMessage = TransactionRandomizationElementsRequestMessage(publicKey.toBytes(), receivingPeer)
        community.messageList.add(requestMessage)

        verify(user, times(1)).generateRandomizationElements(publicKey)
        verify(community, times(1)).sendTransactionRandomizationElements(randomizationElements.toRandomizationElementsBytes(), receivingPeer)
    }

    @Test
    fun sendTransactionDetailsTest() {
        addressBookManager.insertAddress(receiverAddress)
        val transactionDetails = Mockito.mock(TransactionDetails::class.java)
        val result = iPV8CommunicationProtocol.sendTransactionDetails(receiverAddress.name, transactionDetails)
        verify(community, times(1)).sendTransactionDetails(receiverAddress.peerPublicKey!!, transactionDetails)
        Assert.assertEquals("The returned result should be correct", transactionResult, result)
    }

    @Test
    fun onTransactionDetailsReceived() {
        addressBookManager.insertAddress(bankAddress)
        val transactionDetails = Mockito.mock(TransactionDetails::class.java)
        val transactionDetailsBytes = Mockito.mock(TransactionDetailsBytes::class.java)
        val publicKeySender = ttpBilinearGroup.generateRandomElementOfG()
        val result = "Transaction Mocking"

        `when`(transactionDetailsBytes.toTransactionDetails(ttpBilinearGroup)).thenReturn(transactionDetails)
        `when`(user.group).thenReturn(ttpBilinearGroup)
        `when`(user.onReceivedTransaction(transactionDetails, bankPK, publicKeySender)).thenReturn(result)
        `when`(community.sendTransactionResult(result, receivingPeer)).then {  }

        val message = TransactionMessage(publicKeySender.toBytes(), transactionDetailsBytes, receivingPeer)
        community.messageList.add(message)

        verify(user, times(1)).onReceivedTransaction(transactionDetails, bankPK, publicKeySender)
        verify(community, times(1)).sendTransactionResult(result, receivingPeer)

    }

}
