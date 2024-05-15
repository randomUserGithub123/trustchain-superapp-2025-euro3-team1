package nl.tudelft.trustchain.offlineeuro.communication

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import nl.tudelft.offlineeuro.sqldelight.Database
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
import nl.tudelft.trustchain.offlineeuro.community.message.BilinearGroupCRSMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureRandomnessReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.ICommunityMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionRandomizationElementsMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionResultMessage
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.CRSGenerator
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahai
import nl.tudelft.trustchain.offlineeuro.cryptography.PairingTypes
import nl.tudelft.trustchain.offlineeuro.db.AddressBookManager
import nl.tudelft.trustchain.offlineeuro.entity.Address
import nl.tudelft.trustchain.offlineeuro.entity.TransactionDetails
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
    private val addressBookManager = AddressBookManager(context, ttpBilinearGroup, driver)
    private val userMockMessageList = arrayListOf<ICommunityMessage>()

    private val iPV8CommunicationProtocol = IPV8CommunicationProtocol(addressBookManager, community)

    @Before
    fun setup() {
        `when` (community.messageList).thenReturn(userMockMessageList)

        `when`(community.getGroupDescriptionAndCRS()).then {
            val message = BilinearGroupCRSMessage(ttpBilinearGroup.toGroupElementBytes(), ttpCRS.first.toCRSBytes())
            community.messageList.add(message)
        }

        `when`(community.registerAtTTP(any(), any(), any())).then { }

        `when`(community.getBlindSignatureRandomness(any(), any())).then {
            val message = BlindSignatureRandomnessReplyMessage(blindSignatureRandomness.toBytes())
            community.messageList.add(message)
        }

        `when`(community.getBlindSignature(any(), any(), any())).then {
            val message = BlindSignatureReplyMessage(blindSignature)
            community.messageList.add(message)
        }

        `when`(community.getTransactionRandomizationElements(any())).then {
            val message = TransactionRandomizationElementsMessage(randomizationElements.toRandomizationElementsBytes())
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
    fun requestBlindSignatureTest() {
        addressBookManager.insertAddress(bankAddress)
        val publicKey = ttpBilinearGroup.generateRandomElementOfG()
        val challenge = BigInteger("12352132521521321521312")
        val signature = iPV8CommunicationProtocol.requestBlindSignature(publicKey, bankAddress.name, challenge)
        verify(community, times(1)).getBlindSignature(challenge, publicKey.toBytes(), bankAddress.peerPublicKey!!)
        Assert.assertEquals("The returned signature should be correct", blindSignature, signature)
    }

    @Test
    fun requestTransactionRandomnessTest() {
        addressBookManager.insertAddress(receiverAddress)
        val transactionRandomness = iPV8CommunicationProtocol.requestTransactionRandomness(receiverAddress.name, ttpBilinearGroup)
        verify(community, times(1)).getTransactionRandomizationElements(receiverAddress.peerPublicKey!!)
        Assert.assertEquals("The transaction randomness should be correct", randomizationElements, transactionRandomness)
    }

    @Test
    fun sendTransactionDetailsTest() {
        addressBookManager.insertAddress(receiverAddress)
        val transactionDetails = Mockito.mock(TransactionDetails::class.java)
        val result = iPV8CommunicationProtocol.sendTransactionDetails(receiverAddress.name, transactionDetails)
        verify(community, times(1)).sendTransactionDetails(receiverAddress.peerPublicKey!!, transactionDetails)
        Assert.assertEquals("The returned result should be correct", transactionResult, result)
    }

}
