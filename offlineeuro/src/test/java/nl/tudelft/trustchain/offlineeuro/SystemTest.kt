package nl.tudelft.trustchain.offlineeuro

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import nl.tudelft.ipv8.Peer
import nl.tudelft.offlineeuro.sqldelight.Database
import nl.tudelft.trustchain.offlineeuro.communication.IPV8CommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
import nl.tudelft.trustchain.offlineeuro.community.message.AddressMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BilinearGroupCRSReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureRandomnessReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureRandomnessRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.ICommunityMessage
import nl.tudelft.trustchain.offlineeuro.community.message.MessageList
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionRandomizationElementsReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionRandomizationElementsRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionResultMessage
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahaiProof
import nl.tudelft.trustchain.offlineeuro.cryptography.PairingTypes
import nl.tudelft.trustchain.offlineeuro.cryptography.RandomizationElementsBytes
import nl.tudelft.trustchain.offlineeuro.cryptography.Schnorr
import nl.tudelft.trustchain.offlineeuro.db.AddressBookManager
import nl.tudelft.trustchain.offlineeuro.db.DepositedEuroManager
import nl.tudelft.trustchain.offlineeuro.db.WalletManager
import nl.tudelft.trustchain.offlineeuro.entity.Address
import nl.tudelft.trustchain.offlineeuro.entity.Bank
import nl.tudelft.trustchain.offlineeuro.entity.CentralAuthority
import nl.tudelft.trustchain.offlineeuro.entity.DigitalEuro
import nl.tudelft.trustchain.offlineeuro.entity.Participant
import nl.tudelft.trustchain.offlineeuro.entity.TransactionDetailsBytes
import nl.tudelft.trustchain.offlineeuro.entity.User
import nl.tudelft.trustchain.offlineeuro.enums.Role
import org.junit.Assert
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.math.BigInteger

class SystemTest {


    // Setup the TTP
    private val ca = CentralAuthority
    private val ttpPK = ca.groupDescription.generateRandomElementOfG()
    private val ttpAddress = Address("TTP", Role.Bank, ttpPK, "TTPPublicKey".toByteArray())
    private val registrationNameCaptor = argumentCaptor<String>()
    private val publicKeyCaptor = argumentCaptor<ByteArray>()
    private val userList = hashMapOf<User, OfflineEuroCommunity>()
    private lateinit var bank: Bank
    private lateinit var bankCommunity: OfflineEuroCommunity

    @Test
    fun withdrawSpendDepositDoubleSpendDepositTest() {
        // Initiate
        ca.initializeRegisteredUserManager(null, createDriver())
        createBank()
        val user = createTestUser()

        // Assert that the group descriptions and crs are equal
        Assert.assertEquals("The group descriptions should be equal", bank.group, user.group)
        Assert.assertEquals("The group descriptions should be equal", bank.crs, user.crs)

        val bankAddressMessage = AddressMessage(bank.name, Role.Bank, bank.publicKey.toBytes(), bank.name.toByteArray())
        addMessageToList(user, bankAddressMessage)
        // TODO MAKE THIS UNNECESSARY
        bankCommunity.messageList.add(bankAddressMessage)
        val digitalEuro = withdrawDigitalEuro(user, bank.name)

        // Validations on the wallet
        val allWalletEntries = user.wallet.getAllWalletEntriesToSpend()
        Assert.assertEquals("There should only be one Euro", 1, allWalletEntries.size)

        val walletEntry = allWalletEntries[0]
        Assert.assertEquals("That should be the withdrawn Euro", digitalEuro, walletEntry.digitalEuro)

        val computedTheta1 = user.group.g.powZn(walletEntry.t.mul(-1))
        Assert.assertEquals("The first theta should be correct", digitalEuro.firstTheta1, computedTheta1)
        Assert.assertNull("The walletEntry should not have a previous transaction", walletEntry.transactionSignature)

        val user2 = createTestUser()
        addMessageToList(user2, bankAddressMessage)

        val user2AddressMessage = AddressMessage(user2.name, Role.User, user2.publicKey.toBytes(), user2.name.toByteArray())
        addMessageToList(user, user2AddressMessage)

        // First Spend
        spendEuro(user, user2)

        // Deposit
        spendEuro(user2, bank, "Deposit was successful!")

        // Prepare double spend
        val user3 = createTestUser()
        addMessageToList(user3, bankAddressMessage)

        val user3AddressMessage = AddressMessage(user3.name, Role.User, user3.publicKey.toBytes(), user3.name.toByteArray())
        addMessageToList(user, user3AddressMessage)

        // Double Spend
        spendEuro(user, user3, doubleSpend = true)

        // Deposit double spend Euro
        spendEuro(user3, bank, "Double spending detected. Double spender is ${user.name} with PK: ${user.publicKey}")
    }

    private fun withdrawDigitalEuro(user: User, bankName: String): DigitalEuro {
        // Prepare mock elements
        val byteArrayCaptor = argumentCaptor<ByteArray>()
        val challengeCaptor = argumentCaptor<BigInteger>()
        val userPeer = Mockito.mock(Peer::class.java)


        val userCommunity = userList[user]!!
        val publicKeyBytes = user.publicKey.toBytes()

        // Request the randomness
        `when`(userCommunity.getBlindSignatureRandomness(any(), any())).then {
            val randomnessRequestMessage = BlindSignatureRandomnessRequestMessage(publicKeyBytes, userPeer)
            bankCommunity.messageList.add(randomnessRequestMessage)

            verify(bankCommunity, times(1)).sendBlindSignatureRandomnessReply(byteArrayCaptor.capture(), any())
            val givenRandomness = byteArrayCaptor.lastValue

            val randomnessReplyMessage = BlindSignatureRandomnessReplyMessage(givenRandomness)
            addMessageToList(user, randomnessReplyMessage)

            // Request the signature
            `when`(userCommunity.getBlindSignature(challengeCaptor.capture(), any(), any())).then {
                val challenge = challengeCaptor.lastValue
                val signatureRequestMessage = BlindSignatureRequestMessage(challenge, publicKeyBytes, userPeer)
                bankCommunity.messageList.add(signatureRequestMessage)

                verify(bankCommunity, times(1)).sendBlindSignature(challengeCaptor.capture(), any())
                val signature = challengeCaptor.lastValue

                val signatureMessage = BlindSignatureReplyMessage(signature)
                addMessageToList(user, signatureMessage)
            }


        }

        val withdrawnEuro = user.withdrawDigitalEuro(bankName)

        // User must make two requests
        verify(userCommunity, times(1)).getBlindSignatureRandomness(publicKeyBytes, bank.name.toByteArray())
        verify(userCommunity, times(1)).getBlindSignature(any(), eq(publicKeyBytes), eq(bank.name.toByteArray()))

        // Bank must respond twice
        verify(bankCommunity, times(1)).sendBlindSignatureRandomnessReply(any(), eq(userPeer))
        verify(bankCommunity, times(1)).sendBlindSignature(any(), eq(userPeer))

        // The euro must be valid
        Assert.assertTrue("The signature should be valid for the user", Schnorr.verifySchnorrSignature(withdrawnEuro.signature, bank.publicKey, user.group))
        Assert.assertEquals("There should be no proofs", arrayListOf<GrothSahaiProof>(), withdrawnEuro.proofs)

        return withdrawnEuro

    }

    private fun spendEuro(sender: User, receiver: Participant, expectedResult: String = "Successful transaction", doubleSpend: Boolean = false) {
        val senderCommunity = userList[sender]!!
        val receiverCommunity = if (receiver.name == bank.name) {
            bankCommunity
        } else {
            userList[receiver]!!
        }
        val spenderPeer = Mockito.mock(Peer::class.java)
        val randomizationElementsCaptor = argumentCaptor<RandomizationElementsBytes>()
        val transactionDetailsCaptor = argumentCaptor<TransactionDetailsBytes>()
        val transactionResultCaptor = argumentCaptor<String>()

        `when`(senderCommunity.getTransactionRandomizationElements(receiver.name.toByteArray())).then {
            val requestMessage = TransactionRandomizationElementsRequestMessage(sender.publicKey.toBytes(),spenderPeer)
            receiverCommunity.messageList.add(requestMessage)
            verify(receiverCommunity).sendTransactionRandomizationElements(randomizationElementsCaptor.capture(), eq(spenderPeer))
            val randomizationElementsBytes = randomizationElementsCaptor.lastValue
            val randomizationElementsMessage = TransactionRandomizationElementsReplyMessage(randomizationElementsBytes)
            senderCommunity.messageList.add(randomizationElementsMessage)

            // To send the transaction details
            `when`(senderCommunity.sendTransactionDetails(eq(receiver.name.toByteArray()), transactionDetailsCaptor.capture())).then {
                val transactionDetailsBytes = transactionDetailsCaptor.lastValue
                val transactionMessage = TransactionMessage(sender.publicKey.toBytes(), transactionDetailsBytes, spenderPeer)
                receiverCommunity.messageList.add(transactionMessage)
                verify(receiverCommunity, atLeastOnce()).sendTransactionResult(transactionResultCaptor.capture(), any())
                val result = transactionResultCaptor.lastValue
                val transactionResultMessage = TransactionResultMessage(result)
                senderCommunity.messageList.add(transactionResultMessage)
            }
        }


        val transactionResult = if (doubleSpend) {
            sender.doubleSpendDigitalEuroTo(receiver.name)
        }
         else {
            sender.sendDigitalEuroTo(receiver.name)
        }
        Assert.assertEquals(expectedResult, transactionResult)
    }

    fun createTestUser(): User {
        // Start with a random group
        val group = BilinearGroup(PairingTypes.FromFile)

        val addressBookManager = createAddressManager(group)
        val walletManager = WalletManager(null, group, createDriver())

        // Add the community for later access
        val userName = "User${userList.size}"
        val community = prepareCommunityMock()
        val communicationProtocol = IPV8CommunicationProtocol(addressBookManager, community)

        `when`(community.messageList).thenReturn(communicationProtocol.messageList)
        val user = User(userName, null, walletManager, communicationProtocol)
        userList[user] = community

        // Handle registration verification
        verify(community, times(1)).registerAtTTP(registrationNameCaptor.capture(), publicKeyCaptor.capture(), any())
        val capturedUsername = registrationNameCaptor.lastValue
        val publicKey = publicKeyCaptor.lastValue
        ca.registerUser(capturedUsername, ca.groupDescription.gElementFromBytes(publicKey))

        // Verification
        Assert.assertEquals("The username should be correct", userName, capturedUsername)

        return user
    }

    private fun createBank() {
        val group = BilinearGroup(PairingTypes.FromFile)

        val addressBookManager = createAddressManager(group)
        val depositedEuroManager = DepositedEuroManager(null, group, createDriver())

        val community = prepareCommunityMock()
        val communicationProtocol = IPV8CommunicationProtocol(addressBookManager, community)

        `when`(community.messageList).thenReturn(communicationProtocol.messageList)
        bank = Bank("Bank", communicationProtocol, null, depositedEuroManager)
        bankCommunity = community

        // Assert that the bank is registered
        verify(bankCommunity, times(1)).registerAtTTP(registrationNameCaptor.capture(), publicKeyCaptor.capture(), any())
        val bankName = registrationNameCaptor.lastValue
        val bankPublicKey = publicKeyCaptor.lastValue
        ca.registerUser(bankName, ca.groupDescription.gElementFromBytes(bankPublicKey))

    }

    private fun createAddressManager(group: BilinearGroup): AddressBookManager {
        val addressBookManager = AddressBookManager(null, group, createDriver())
        addressBookManager.insertAddress(ttpAddress)
        return  addressBookManager
    }

    private fun addMessageToList(user: User, message: ICommunityMessage) {
        val community = userList[user]
        community!!.messageList.add(message)
    }

    private fun prepareCommunityMock(): OfflineEuroCommunity {

        val community = Mockito.mock(OfflineEuroCommunity::class.java)

        `when`(community.getGroupDescriptionAndCRS()).then {
            val message = BilinearGroupCRSReplyMessage(ca.groupDescription.toGroupElementBytes(), ca.crs.toCRSBytes())
            community.messageList.add(message)
        }

        return community
    }

    private fun createDriver(): JdbcSqliteDriver {
        return JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).apply {
            Database.Schema.create(this)
        }
    }
}

class MockPair(
    val messageList: MessageList<ICommunityMessage>,
    val community: OfflineEuroCommunity,

)
