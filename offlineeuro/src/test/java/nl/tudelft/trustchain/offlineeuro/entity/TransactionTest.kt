package nl.tudelft.trustchain.offlineeuro.entity

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import it.unisa.dia.gas.jpbc.Element
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
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.CRS
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahai
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahaiProof
import nl.tudelft.trustchain.offlineeuro.cryptography.PairingTypes
import nl.tudelft.trustchain.offlineeuro.cryptography.Schnorr
import nl.tudelft.trustchain.offlineeuro.db.AddressBookManager
import nl.tudelft.trustchain.offlineeuro.db.DepositedEuroManager
import nl.tudelft.trustchain.offlineeuro.db.WalletManager
import nl.tudelft.trustchain.offlineeuro.enums.Role
import org.junit.Assert
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.math.BigInteger
import kotlin.math.floor

class TransactionTest {
    // Setup the TTP
    private val ca = CentralAuthority
    private val group: BilinearGroup = ca.groupDescription
    private val crs: CRS = ca.crs
    private val ttpPK = ca.groupDescription.generateRandomElementOfG()
    private val registrationNameCaptor = argumentCaptor<String>()
    private val publicKeyCaptor = argumentCaptor<ByteArray>()
    private val userList = hashMapOf<User, OfflineEuroCommunity>()
    private lateinit var bank: Bank
    private lateinit var bankCommunity: OfflineEuroCommunity
    private var i = 0

    @Test
    fun transactionWithoutProofsTest() {
        // Initiate
        ca.initializeRegisteredUserManager(null, createDriver())
        createBank()
        val user = createTestUser()
        withdrawDigitalEuro(user, bank.name)
        val walletEntry = user.wallet.getWalletEntryToSpend()!!

        val privateKey = group.getRandomZr()
        val publicKey = group.g.powZn(privateKey)
        val randomT = group.getRandomZr()
        val randomizationElements = GrothSahai.tToRandomizationElements(randomT, group, crs)
        val transactionDetails =
            Transaction.createTransaction(
                privateKey,
                publicKey,
                walletEntry,
                randomizationElements,
                group,
                crs,
            )

        Assert.assertTrue("The transaction should be valid", Transaction.validate(transactionDetails, bank.publicKey, group, crs).valid)
    }

    @Test
    fun invalidProof() {
        // Initiate
        ca.initializeRegisteredUserManager(null, createDriver())
        createBank()
        val user = createTestUser()
        withdrawDigitalEuro(user, bank.name)
        val walletEntry = addProofsToDigitalEuro(user.wallet.getWalletEntryToSpend()!!, 10)
        val transactionDetails = walletEntryToTransactionDetails(walletEntry)
        val randomProofNr = floor(Math.random() * transactionDetails.digitalEuro.proofs.size).toInt()
        val randomProof = transactionDetails.digitalEuro.proofs[randomProofNr]
        val newProof =
            GrothSahaiProof(
                group.generateRandomElementOfG(),
                randomProof.c2,
                randomProof.d1,
                randomProof.d2,
                randomProof.theta1,
                randomProof.theta2,
                randomProof.pi1,
                randomProof.pi2,
                randomProof.target
            )
        transactionDetails.digitalEuro.proofs[randomProofNr] = newProof
        Assert.assertFalse("The transaction should be invalid", Transaction.validate(transactionDetails, bank.publicKey, group, crs).valid)
    }

    private fun walletEntryToTransactionDetails(walletEntry: WalletEntry): TransactionDetails {
        val privateKey = group.getRandomZr()
        val publicKey = group.g.powZn(privateKey)
        val randomT = group.getRandomZr()
        val randomizationElements = GrothSahai.tToRandomizationElements(randomT, group, crs)
        return Transaction.createTransaction(
            privateKey,
            publicKey,
            walletEntry,
            randomizationElements,
            group,
            crs,
        )
    }

    private fun addProofsToDigitalEuro(
        walletEntry: WalletEntry,
        numberOfProofs: Int
    ): WalletEntry {
        var entry = walletEntry
        for (i: Int in 0 until numberOfProofs) {
            val privateKey = group.getRandomZr()
            val publicKey = group.g.powZn(privateKey)
            val randomT = group.getRandomZr()
            val randomizationElements = GrothSahai.tToRandomizationElements(randomT, group, crs)
            val transactionDetails =
                Transaction.createTransaction(
                    privateKey,
                    publicKey,
                    entry,
                    randomizationElements,
                    group,
                    crs,
                )
            Assert.assertTrue("The transaction should be valid", Transaction.validate(transactionDetails, bank.publicKey, group, crs).valid)
            entry = detailsToWalletEntry(transactionDetails, randomT)
        }

        return entry
    }

    private fun withdrawDigitalEuro(
        user: User,
        bankName: String
    ): DigitalEuro {
        // Assert that the group descriptions and crs are equal
        Assert.assertEquals("The group descriptions should be equal", bank.group, user.group)
        Assert.assertEquals("The group descriptions should be equal", bank.crs, user.crs)

        val bankAddressMessage = AddressMessage(bank.name, Role.Bank, bank.publicKey.toBytes(), bank.name.toByteArray())
        addMessageToList(user, bankAddressMessage)
        // TODO MAKE THIS UNNECESSARY
        bankCommunity.messageList.add(bankAddressMessage)
        // Prepare mock elements
        val byteArrayCaptor = argumentCaptor<ByteArray>()
        val challengeCaptor = argumentCaptor<BigInteger>()
        val userPeer = Mockito.mock(Peer::class.java)

        val userCommunity = userList[user]!!
        val publicKeyBytes = user.publicKey.toBytes()

        // Request the randomness
        Mockito.`when`(userCommunity.getBlindSignatureRandomness(any(), any())).then {
            val randomnessRequestMessage = BlindSignatureRandomnessRequestMessage(publicKeyBytes, userPeer)
            bankCommunity.messageList.add(randomnessRequestMessage)

            verify(bankCommunity, Mockito.atLeastOnce()).sendBlindSignatureRandomnessReply(byteArrayCaptor.capture(), any())
            val givenRandomness = byteArrayCaptor.lastValue

            val randomnessReplyMessage = BlindSignatureRandomnessReplyMessage(givenRandomness)
            addMessageToList(user, randomnessReplyMessage)

            // Request the signature
            Mockito.`when`(userCommunity.getBlindSignature(challengeCaptor.capture(), any(), any()))
                .then {
                    val challenge = challengeCaptor.lastValue
                    val signatureRequestMessage = BlindSignatureRequestMessage(challenge, publicKeyBytes, userPeer)
                    bankCommunity.messageList.add(signatureRequestMessage)

                    verify(bankCommunity, Mockito.atLeastOnce()).sendBlindSignature(challengeCaptor.capture(), any())
                    val signature = challengeCaptor.lastValue

                    val signatureMessage = BlindSignatureReplyMessage(signature)
                    addMessageToList(user, signatureMessage)
                }
        }

        val withdrawnEuro = user.withdrawDigitalEuro(bankName)

        // User must make two requests
        verify(userCommunity, Mockito.atLeastOnce()).getBlindSignatureRandomness(publicKeyBytes, bank.name.toByteArray())
        verify(userCommunity, Mockito.atLeastOnce()).getBlindSignature(any(), eq(publicKeyBytes), eq(bank.name.toByteArray()))

        // Bank must respond twice
        verify(bankCommunity, Mockito.atLeastOnce()).sendBlindSignatureRandomnessReply(any(), eq(userPeer))
        verify(bankCommunity, Mockito.atLeastOnce()).sendBlindSignature(any(), eq(userPeer))

        // The euro must be valid
        Assert.assertTrue(
            "The signature should be valid for the user",
            Schnorr.verifySchnorrSignature(withdrawnEuro.signature, bank.publicKey, user.group)
        )

        Assert.assertEquals("There should be no proofs", arrayListOf<GrothSahaiProof>(), withdrawnEuro.proofs)

        return withdrawnEuro
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

        Mockito.`when`(community.messageList).thenReturn(communicationProtocol.messageList)
        val user = User(userName, group, null, walletManager, communicationProtocol)
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

    fun detailsToWalletEntry(
        transactionDetails: TransactionDetails,
        t: Element
    ): WalletEntry {
        val digitalEuro = transactionDetails.digitalEuro
        digitalEuro.proofs.add(transactionDetails.currentTransactionProof.grothSahaiProof)

        val transactionSignature = transactionDetails.theta1Signature
        return WalletEntry(digitalEuro, t, transactionSignature)
    }

    private fun createBank() {
        val group = BilinearGroup(PairingTypes.FromFile)

        val addressBookManager = createAddressManager(group)
        val depositedEuroManager = DepositedEuroManager(null, group, createDriver())

        val community = prepareCommunityMock()
        val communicationProtocol = IPV8CommunicationProtocol(addressBookManager, community)

        Mockito.`when`(community.messageList).thenReturn(communicationProtocol.messageList)
        bank = Bank("Bank", group, communicationProtocol, null, depositedEuroManager)
        bankCommunity = community

        // Assert that the bank is registered
        verify(bankCommunity, times(1)).registerAtTTP(registrationNameCaptor.capture(), publicKeyCaptor.capture(), any())
        val bankName = registrationNameCaptor.lastValue
        val bankPublicKey = publicKeyCaptor.lastValue
        ca.registerUser(bankName, ca.groupDescription.gElementFromBytes(bankPublicKey))
    }

    private fun createAddressManager(group: BilinearGroup): AddressBookManager {
        val addressBookManager = AddressBookManager(null, group, createDriver())
        return addressBookManager
    }

    private fun addMessageToList(
        user: User,
        message: ICommunityMessage
    ) {
        val community = userList[user]
        community!!.messageList.add(message)
    }

    private fun prepareCommunityMock(): OfflineEuroCommunity {
        val community = Mockito.mock(OfflineEuroCommunity::class.java)
        val ttpAddress = Address("TTP", Role.Bank, ttpPK, "TTPPublicKey".toByteArray())
        val ttpAddressMessage = AddressMessage("TTP", ttpAddress.type, ttpAddress.publicKey.toBytes(), ttpAddress.peerPublicKey!!)

        Mockito.`when`(community.getGroupDescriptionAndCRS()).then {
            val message = BilinearGroupCRSReplyMessage(ca.groupDescription.toGroupElementBytes(), ca.crs.toCRSBytes(), ttpAddressMessage)
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
