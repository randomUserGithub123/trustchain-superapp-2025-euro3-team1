package nl.tudelft.trustchain.offlineeuro

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.attestation.trustchain.TrustChainSettings // Added for mockSettings
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainStore // Added for mockStore
import nl.tudelft.offlineeuro.sqldelight.Database
import nl.tudelft.trustchain.offlineeuro.communication.IPV8CommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
import nl.tudelft.trustchain.offlineeuro.community.message.AddressMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureRandomnessReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureRandomnessRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.FraudControlReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.FraudControlRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.ICommunityMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionRandomizationElementsReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionRandomizationElementsRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionResultMessage
import nl.tudelft.trustchain.offlineeuro.cryptography.* // Import all crypto data classes
import nl.tudelft.trustchain.offlineeuro.db.AddressBookManager
import nl.tudelft.trustchain.offlineeuro.db.DepositedEuroManager
import nl.tudelft.trustchain.offlineeuro.db.RegisteredUserManager
import nl.tudelft.trustchain.offlineeuro.db.WalletManager
import nl.tudelft.trustchain.offlineeuro.entity.* // Import all entity data classes
import nl.tudelft.trustchain.offlineeuro.enums.Role
import nl.tudelft.trustchain.offlineeuro.ByteCounter // Assuming ByteCounter is here
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import java.math.BigInteger
import java.nio.charset.StandardCharsets

class SystemTest {
    private val group: BilinearGroup = BilinearGroup(PairingTypes.A)
    private lateinit var ttp: TTP
    private lateinit var ttpCommunity: OfflineEuroCommunity
    private lateinit var crs: CRS
    private val userList = hashMapOf<User, OfflineEuroCommunity>()
    private lateinit var bank: Bank
    private lateinit var bankCommunity: OfflineEuroCommunity
    private var i = 0 // For print statement in withdraw, consider localizing if tests run parallel

    // Mocks for OfflineEuroCommunity constructor if it were real, not strictly needed for Mockito.mock()
    // but good to have if prepareCommunityMock were to change to instantiate real objects.
    // For now, prepareCommunityMock returns a pure mock, so these aren't used by it.
    // private lateinit var mockSettings: TrustChainSettings
    // private lateinit var mockStore: TrustChainStore


    @Before
    fun setup() {
        ByteCounter.reset() // Reset counter before each test

        // mockSettings = Mockito.mock(TrustChainSettings::class.java)
        // mockStore = Mockito.mock(TrustChainStore::class.java)

        createTTP()
        createBank()
        val firstProofCaptor = argumentCaptor<ByteArray>()
        val secondProofCaptor = argumentCaptor<ByteArray>()

        // Simulating bank sending a fraud control request
        `when`(bankCommunity.sendFraudControlRequest(firstProofCaptor.capture(), secondProofCaptor.capture(), any())).then {
            val firstProofBytes = firstProofCaptor.lastValue
            val secondProofBytes = secondProofCaptor.lastValue

            // Record bytes for the simulated fraud control request payload
            ByteCounter.recordSentParts(
                "Simulated_FraudControlRequest",
                mapOf(
                    "proof1" to firstProofBytes,
                    "proof2" to secondProofBytes
                )
            )

            val peerMock = Mockito.mock(Peer::class.java) // This mock is just for constructing the message
            val fraudControlRequestMessage = FraudControlRequestMessage(firstProofBytes, secondProofBytes, peerMock)

            val fraudControlResultCaptor = argumentCaptor<String>()
            // Simulating TTP sending a fraud control reply
            `when`(ttpCommunity.sendFraudControlReply(fraudControlResultCaptor.capture(), any())).then {
                val resultPayload = fraudControlResultCaptor.lastValue.toByteArray(StandardCharsets.UTF_8)
                // Record bytes for the simulated fraud control reply payload
                ByteCounter.recordSentSinglePayload("Simulated_FraudControlReply_Result", resultPayload)

                val replyMessage = FraudControlReplyMessage(fraudControlResultCaptor.lastValue)
                bankCommunity.messageList.add(replyMessage)
            }
            ttpCommunity.messageList.add(fraudControlRequestMessage)
        }
    }

    @After
    fun tearDown() {
        ByteCounter.printStats() // Print stats after each test
    }

    @Test
    fun withdrawSpendDepositDoubleSpendDepositTest() {
        val user = createTestUser()

        Assert.assertEquals("The group descriptions should be equal", bank.group, user.group)
        Assert.assertEquals("The group descriptions should be equal", bank.crs, user.crs)
        Assert.assertEquals("The group descriptions should be equal", ttp.crs, user.crs)


        // println("User1 gets Bank's Address")
        val bankAddressMessage = AddressMessage(bank.name, Role.Bank, bank.publicKey.toBytes(), bank.name.toByteArray())
        ByteCounter.recordSentParts(
            "Simulated_BankAddressMessage_Broadcast_User",
            mapOf(
                "name" to bank.name.toByteArray(StandardCharsets.UTF_8),
                "role" to byteArrayOf(Role.Bank.ordinal.toByte()),
                "publicKey" to bank.publicKey.toBytes(),
                "address" to bank.name.toByteArray(StandardCharsets.UTF_8)
            )
        )
        addMessageToList(user, bankAddressMessage)


        // println("Bank making its address known to its own community/message list")
        ByteCounter.recordSentParts(
            "Simulated_BankAddressMessage_Broadcast_BankList", // Different context for logging
            mapOf(
                "name" to bank.name.toByteArray(StandardCharsets.UTF_8),
                "role" to byteArrayOf(Role.Bank.ordinal.toByte()),
                "publicKey" to bank.publicKey.toBytes(),
                "address" to bank.name.toByteArray(StandardCharsets.UTF_8)
            )
        )
        bankCommunity.messageList.add(bankAddressMessage)

        // println("User1 withdraws DigitalEuro from Bank")
        val digitalEuro = withdrawDigitalEuro(user, bank.name)

        val allWalletEntries = user.wallet.getAllWalletEntriesToSpend()
        Assert.assertEquals("There should only be one Euro", 1, allWalletEntries.size)
        val walletEntry = allWalletEntries[0]
        Assert.assertEquals("That should be the withdrawn Euro", digitalEuro, walletEntry.digitalEuro)
        val computedTheta1 = user.group.g.powZn(walletEntry.t.mul(-1))
        Assert.assertEquals("The first theta should be correct", digitalEuro.firstTheta1, computedTheta1)
        Assert.assertNull("The walletEntry should not have a previous transaction", walletEntry.transactionSignature)

        // println("User2 is created")
        val user2 = createTestUser()

        // println("User2 gets Bank's Address")
        addMessageToList(user2, bankAddressMessage) // user2 receives bank's address

        // println("User1 gets User2's Address")
        val user2AddressMessage = AddressMessage(user2.name, Role.User, user2.publicKey.toBytes(), user2.name.toByteArray())
        ByteCounter.recordSentParts(
            "Simulated_User2AddressMessage_To_User1",
            mapOf(
                "name" to user2.name.toByteArray(StandardCharsets.UTF_8),
                "role" to byteArrayOf(Role.User.ordinal.toByte()),
                "publicKey" to user2.publicKey.toBytes(),
                "address" to user2.name.toByteArray(StandardCharsets.UTF_8)
            )
        )
        addMessageToList(user, user2AddressMessage) // user1 receives user2's address

        // println("First Spend: User1 spends to User2")
        spendEuro(user, user2)

        // println("Deposit: User2 spends/deposits to Bank")
        spendEuro(user2, bank, "Deposit was successful!")

        // println("User3 is created")
        val user3 = createTestUser()

        // println("User3 gets Bank's Address")
        addMessageToList(user3, bankAddressMessage) // user3 receives bank's address

        // println("User1 gets User3's Address")
        val user3AddressMessage = AddressMessage(user3.name, Role.User, user3.publicKey.toBytes(), user3.name.toByteArray())
        ByteCounter.recordSentParts(
            "Simulated_User3AddressMessage_To_User1",
            mapOf(
                "name" to user3.name.toByteArray(StandardCharsets.UTF_8),
                "role" to byteArrayOf(Role.User.ordinal.toByte()),
                "publicKey" to user3.publicKey.toBytes(),
                "address" to user3.name.toByteArray(StandardCharsets.UTF_8)
            )
        )
        addMessageToList(user, user3AddressMessage) // user1 receives user3's address

        // println("Double Spend: User1 spends same conceptual euro to User3")
        spendEuro(user, user3, doubleSpend = true)

        // println("Deposit double spend Euro: User3 spends/deposits to Bank")
        spendEuro(user3, bank, "Double spending detected. Double spender is ${user.name} with PK: ${user.publicKey}")
    }

//    @Test
//    fun getManyBlindSignatures() {
//        val user = createTestUser()
//        val bankAddressMessage = AddressMessage(bank.name, Role.Bank, bank.publicKey.toBytes(), bank.name.toByteArray())
//        ByteCounter.recordSentParts(
//            "Simulated_BankAddressMessage_Broadcast_User_ManySigs",
//            mapOf(
//                "name" to bank.name.toByteArray(StandardCharsets.UTF_8),
//                "role" to byteArrayOf(Role.Bank.ordinal.toByte()),
//                "publicKey" to bank.publicKey.toBytes(),
//                "address" to bank.name.toByteArray(StandardCharsets.UTF_8)
//            )
//        )
//        addMessageToList(user, bankAddressMessage)
//        bankCommunity.messageList.add(bankAddressMessage)
//        for (k in 0 until 50) // Use different loop var
//            withdrawDigitalEuro(user, bank.name)
//    }

    private fun withdrawDigitalEuro(
        user: User,
        bankName: String
    ): DigitalEuro {
        val bankReplyRandomnessCaptor = argumentCaptor<ByteArray>()
        val bankReplySignatureCaptor = argumentCaptor<BigInteger>()
        val userChallengeCaptor = argumentCaptor<BigInteger>()
        val userPeer = Mockito.mock(Peer::class.java)

        val userCommunity = userList[user]!!
        val publicKeyBytes = user.publicKey.toBytes()

        `when`(userCommunity.getBlindSignatureRandomness(eq(publicKeyBytes), eq(bank.name.toByteArray()))).then {
            // Simulate User sending BlindSignatureRandomnessRequest
            ByteCounter.recordSentSinglePayload(
                "Simulated_BlindSigRandomnessRequest_UserPK",
                publicKeyBytes
            )
            val randomnessRequestMessage = BlindSignatureRandomnessRequestMessage(publicKeyBytes, userPeer)
            bankCommunity.messageList.add(randomnessRequestMessage) // Simulate bank receiving it

            // Simulate Bank sending BlindSignatureRandomnessReply
            // This verify captures what bank *would* send; we use the captured value to log its size
            verify(bankCommunity, atLeastOnce()).sendBlindSignatureRandomnessReply(bankReplyRandomnessCaptor.capture(), eq(userPeer))
            val givenRandomness = bankReplyRandomnessCaptor.lastValue
            ByteCounter.recordSentSinglePayload(
                "Simulated_BlindSigRandomnessReply_Randomness",
                givenRandomness
            )
            val randomnessReplyMessage = BlindSignatureRandomnessReplyMessage(givenRandomness)
            addMessageToList(user, randomnessReplyMessage) // Simulate user receiving it
        }

        `when`(userCommunity.getBlindSignature(userChallengeCaptor.capture(), eq(publicKeyBytes), eq(bank.name.toByteArray()))).then {
            val challenge = userChallengeCaptor.lastValue
            // Simulate User sending BlindSignatureRequest
            ByteCounter.recordSentParts(
                "Simulated_BlindSigRequest",
                mapOf(
                    "challenge" to challenge.toByteArray(),
                    "userPK" to publicKeyBytes
                )
            )
            val signatureRequestMessage = BlindSignatureRequestMessage(challenge, publicKeyBytes, userPeer)
            bankCommunity.messageList.add(signatureRequestMessage) // Simulate bank receiving it

            // Simulate Bank sending BlindSignatureReply
            verify(bankCommunity, atLeastOnce()).sendBlindSignature(bankReplySignatureCaptor.capture(), eq(userPeer))
            val signature = bankReplySignatureCaptor.lastValue
            ByteCounter.recordSentSinglePayload(
                "Simulated_BlindSigReply_Signature",
                signature.toByteArray()
            )
            val signatureMessage = BlindSignatureReplyMessage(signature)
            addMessageToList(user, signatureMessage) // Simulate user receiving it
        }

        val withdrawnEuro = user.withdrawDigitalEuro(bankName)

        verify(userCommunity, atLeastOnce()).getBlindSignatureRandomness(publicKeyBytes, bank.name.toByteArray())
        verify(userCommunity, atLeastOnce()).getBlindSignature(any(), eq(publicKeyBytes), eq(bank.name.toByteArray()))
        verify(bankCommunity, atLeastOnce()).sendBlindSignatureRandomnessReply(any(), eq(userPeer))
        verify(bankCommunity, atLeastOnce()).sendBlindSignature(any(), eq(userPeer))

        Assert.assertTrue(
            "The signature should be valid for the user",
            Schnorr.verifySchnorrSignature(withdrawnEuro.signature, bank.publicKey, user.group)
        )
        // print("Valid ${i++}") // Avoid class member 'i' if possible, or make it local to the test method if needed for loop counting
        Assert.assertEquals("There should be no proofs", arrayListOf<GrothSahaiProof>(), withdrawnEuro.proofs)
        return withdrawnEuro
    }

    private fun spendEuro(
        sender: User,
        receiver: Participant,
        expectedResult: String = TransactionResult.VALID_TRANSACTION.description,
        doubleSpend: Boolean = false
    ) {
        val senderCommunity = userList[sender]!!
        val receiverCommunity = if (receiver is Bank) bankCommunity else userList[receiver as User]!!

        val spenderPeer = Mockito.mock(Peer::class.java)
        val randElemReplyCaptor = argumentCaptor<RandomizationElementsBytes>()
        val txDetailsForReceiverCaptor = argumentCaptor<TransactionDetailsBytes>()
        val txResultReplyCaptor = argumentCaptor<String>()

        `when`(senderCommunity.getTransactionRandomizationElements(eq(sender.publicKey.toBytes()), eq(receiver.name.toByteArray()))).then {
            // Simulate Sender sending TransactionRandomizationElementsRequest
            ByteCounter.recordSentSinglePayload(
                "Simulated_TxRandomElementsRequest_SenderPK",
                sender.publicKey.toBytes()
            )
            val requestMessage = TransactionRandomizationElementsRequestMessage(sender.publicKey.toBytes(), spenderPeer)
            receiverCommunity.messageList.add(requestMessage) // Simulate receiver getting it

            // Simulate Receiver sending TransactionRandomizationElementsReply
            verify(receiverCommunity).sendTransactionRandomizationElements(randElemReplyCaptor.capture(), eq(spenderPeer))
            val randomizationElementsBytes = randElemReplyCaptor.lastValue
            // Log components of RandomizationElementsBytes
            ByteCounter.recordSentParts(
                "Simulated_TxRandomElementsReply",
                mapOf(
                    "group2T" to randomizationElementsBytes.group2T,
                    "vT" to randomizationElementsBytes.vT,
                    "group1TInv" to randomizationElementsBytes.group1TInv,
                    "uTInv" to randomizationElementsBytes.uTInv
                )
            )
            val randomizationElementsMessage = TransactionRandomizationElementsReplyMessage(randomizationElementsBytes)
            senderCommunity.messageList.add(randomizationElementsMessage) // Simulate sender getting reply
        }

        `when`(senderCommunity.sendTransactionDetails(eq(sender.publicKey.toBytes()), eq(receiver.name.toByteArray()), txDetailsForReceiverCaptor.capture())).then {
            val transactionDetailsBytes = txDetailsForReceiverCaptor.lastValue
            // Simulate Sender sending TransactionMessage
            ByteCounter.recordSentParts(
                "Simulated_TransactionMessage_SenderPK",
                mapOf("pk" to sender.publicKey.toBytes()) // Sender PK part
            )
            ByteCounter.recordSentParts(
                "Simulated_TransactionMessage_TxDetails",
                mapOf(
                    "digEuro_serial" to transactionDetailsBytes.digitalEuroBytes.serialNumberBytes,
                    "digEuro_theta1" to transactionDetailsBytes.digitalEuroBytes.firstTheta1Bytes,
                    "digEuro_sig" to transactionDetailsBytes.digitalEuroBytes.signatureBytes,
                    "digEuro_proofs" to transactionDetailsBytes.digitalEuroBytes.proofsBytes,
                    "currProof_gs" to transactionDetailsBytes.currentTransactionProofBytes.grothSahaiProofBytes,
                    "currProof_y" to transactionDetailsBytes.currentTransactionProofBytes.usedYBytes,
                    "currProof_vs" to transactionDetailsBytes.currentTransactionProofBytes.usedVSBytes,
                    "prevThetaSig" to transactionDetailsBytes.previousThetaSignatureBytes,
                    "theta1Sig" to transactionDetailsBytes.theta1SignatureBytes,
                    "spenderPK" to transactionDetailsBytes.spenderPublicKeyBytes
                )
            )
            val transactionMessage = TransactionMessage(sender.publicKey.toBytes(), transactionDetailsBytes, spenderPeer)
            receiverCommunity.messageList.add(transactionMessage) // Simulate receiver getting it

            // Simulate Receiver sending TransactionResultMessage
            verify(receiverCommunity, atLeastOnce()).sendTransactionResult(txResultReplyCaptor.capture(), eq(spenderPeer))
            val result = txResultReplyCaptor.lastValue
            ByteCounter.recordSentSinglePayload(
                "Simulated_TransactionResult",
                result.toByteArray(StandardCharsets.UTF_8)
            )
            val transactionResultMessage = TransactionResultMessage(result)
            senderCommunity.messageList.add(transactionResultMessage) // Simulate sender getting result
        }

        val transactionResult =
            if (doubleSpend) {
                sender.doubleSpendDigitalEuroTo(receiver.name)
            } else {
                sender.sendDigitalEuroTo(receiver.name)
            }
        Assert.assertEquals(expectedResult, transactionResult)
    }

    fun createTestUser(): User {
        val addressBookManager = createAddressManager(group)
        val walletManager = WalletManager(null, group, createDriver())
        val userName = "User${userList.size}"
        val community = prepareCommunityMock() // This still returns Mockito.mock(OfflineEuroCommunity::class.java)
        val communicationProtocol = IPV8CommunicationProtocol(addressBookManager, community)

        // The following line is crucial if messageList is accessed before IPV8CommunicationProtocol's init sets it.
        // However, IPV8CommunicationProtocol itself assigns community.messageList in its init block.
        // So, if 'community' is a pure mock, its messageList won't be the one IPV8CommunicationProtocol uses
        // unless this when() makes it so.
        Mockito.`when`(community.messageList).thenReturn(communicationProtocol.messageList)

        val user = User(userName, group, null, walletManager, communicationProtocol, runSetup = false)
        user.crs = crs
        user.group = group
        userList[user] = community
        ttp.registerUser(user.name, user.publicKey)
        return user
    }

    private fun createTTP() {
        val addressBookManager = createAddressManager(group)
        val registeredUserManager = RegisteredUserManager(null, group, createDriver())
        ttpCommunity = prepareCommunityMock()
        val communicationProtocol = IPV8CommunicationProtocol(addressBookManager, ttpCommunity)
        Mockito.`when`(ttpCommunity.messageList).thenReturn(communicationProtocol.messageList)
        ttp = TTP("TTP", group, communicationProtocol, null, registeredUserManager)
        crs = ttp.crs
        communicationProtocol.participant = ttp
    }

    private fun createBank() {
        val addressBookManager = createAddressManager(group)
        val depositedEuroManager = DepositedEuroManager(null, group, createDriver())
        bankCommunity = prepareCommunityMock()
        val communicationProtocol = IPV8CommunicationProtocol(addressBookManager, bankCommunity)
        Mockito.`when`(bankCommunity.messageList).thenReturn(communicationProtocol.messageList)
        bank = Bank("Bank", group, communicationProtocol, null, depositedEuroManager, runSetup = false)
        bank.crs = crs
        addressBookManager.insertAddress(Address(ttp.name, Role.TTP, ttp.publicKey, "SomeTTPPubKey".toByteArray()))
        ttp.registerUser(bank.name, bank.publicKey)
    }

    private fun createAddressManager(group: BilinearGroup): AddressBookManager {
        return AddressBookManager(null, group, createDriver())
    }

    private fun addMessageToList(
        user: User,
        message: ICommunityMessage
    ) {
        val community = userList[user]!! // This is still a Mockito mock
        // Ensure the messageList on the mock is the one managed by IPV8CommunicationProtocol
        community.messageList.add(message)
    }

    private fun prepareCommunityMock(): OfflineEuroCommunity {
        // This returns a standard Mockito mock. Calls to its methods will be stubbed by `when().then{}`.
        // ByteCounter calls would be inside the `then{}` blocks.
        val community = Mockito.mock(OfflineEuroCommunity::class.java)
        return community
    }

    private fun createDriver(): JdbcSqliteDriver {
        return JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).apply {
            Database.Schema.create(this)
        }
    }
}
