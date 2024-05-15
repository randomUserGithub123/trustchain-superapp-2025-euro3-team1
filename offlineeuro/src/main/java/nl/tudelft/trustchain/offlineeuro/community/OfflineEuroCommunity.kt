package nl.tudelft.trustchain.offlineeuro.community

import nl.tudelft.ipv8.Overlay
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCrawler
import nl.tudelft.ipv8.attestation.trustchain.TrustChainSettings
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainStore
import nl.tudelft.ipv8.messaging.Packet
import nl.tudelft.trustchain.offlineeuro.community.message.BilinearGroupCRSMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureRandomnessReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureRandomnessRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.ICommunityMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TTPRegistrationMessage
import nl.tudelft.trustchain.offlineeuro.community.payload.BilinearGroupCRSPayload
import nl.tudelft.trustchain.offlineeuro.community.payload.BlindSignatureRequestPayload
import nl.tudelft.trustchain.offlineeuro.community.payload.ByteArrayPayload
import nl.tudelft.trustchain.offlineeuro.community.payload.TTPRegistrationPayload
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.CRS
import nl.tudelft.trustchain.offlineeuro.entity.TransactionDetails
import nl.tudelft.trustchain.offlineeuro.enums.Role
import java.math.BigInteger

object MessageID {
    const val GET_GROUP_DESCRIPTION_CRS = 9
    const val GET_GROUP_DESCRIPTION_CRS_REPLY = 10
    const val REGISTER_AT_TTP = 11

    const val GET_BLIND_SIGNATURE_RANDOMNESS = 12
    const val GET_BLIND_SIGNATURE_RANDOMNESS_REPLY = 13
    const val GET_BLIND_SIGNATURE = 14
    const val GET_BLIND_SIGNATURE_REPLY = 15

    const val GET_TRANSACTION_RANDOMIZATION_ELEMENTS = 16
    const val GET_TRANSACTION_RANDOMIZATION_ELEMENTS_REPLY = 17

    const val TRANSACTION = 18
}

class OfflineEuroCommunity (
    settings: TrustChainSettings,
    database: TrustChainStore,
    crawler: TrustChainCrawler = TrustChainCrawler()
) : TrustChainCommunity(settings, database, crawler)  {
    override val serviceId = "ffffd716494b474ea9f614a16a4da0aed6899aec"

    val messageList = arrayListOf<ICommunityMessage>()

    var role: Role = Role.User
    val name: String = "BestBank"
    lateinit var crs: CRS
    var bilinearGroup = BilinearGroup()

    init {

        messageHandlers[MessageID.GET_GROUP_DESCRIPTION_CRS] = ::onGetGroupDescriptionAndCRSPacket
        messageHandlers[MessageID.GET_GROUP_DESCRIPTION_CRS_REPLY] = ::onGetGroupDescriptionAndCRSReplyPacket

        messageHandlers[MessageID.REGISTER_AT_TTP] = ::onGetRegisterAtTTPPacket

        messageHandlers[MessageID.GET_BLIND_SIGNATURE_RANDOMNESS] =:: onGetBlindSignatureRandomnessPacket
        messageHandlers[MessageID.GET_BLIND_SIGNATURE_RANDOMNESS_REPLY] =:: onGetBlindSignatureRandomnessReplyPacket

        messageHandlers[MessageID.GET_BLIND_SIGNATURE] =:: onGetBlindSignaturePacket
        messageHandlers[MessageID.GET_BLIND_SIGNATURE_REPLY] =:: onGetBlindSignatureReplyPacket

        messageHandlers[MessageID.GET_TRANSACTION_RANDOMIZATION_ELEMENTS] =:: onGetTransactionRandomizationElementsPacket
        messageHandlers[MessageID.GET_TRANSACTION_RANDOMIZATION_ELEMENTS_REPLY] =:: onGetTransactionRandomizationElementsReplyPacket

        messageHandlers[MessageID.TRANSACTION] =:: onTransactionPacket

    }

    fun getGroupDescriptionAndCRS() {

        val packet = serializePacket(
            MessageID.GET_GROUP_DESCRIPTION_CRS,
            ByteArrayPayload(myPeer.publicKey.keyToBin())
        )

        for (peer: Peer in getPeers()) {
            send(peer, packet)
        }
    }


    private fun onGetGroupDescriptionAndCRSPacket(packet: Packet) {
        val (requestingPeer, payload) = packet.getAuthPayload(ByteArrayPayload)
        onGetGroupDescriptionAndCRS(requestingPeer)
    }


    fun onGetGroupDescriptionAndCRS(requestingPeer: Peer) {

        // TODO SPLIT TO TTP AND BANK
        if (role != Role.Bank) {
            return
        }
        val groupBytes = bilinearGroup.toGroupElementBytes()
        val crsBytes = crs.toCRSBytes()

        val groupAndCrsPacket = serializePacket(
            MessageID.GET_GROUP_DESCRIPTION_CRS_REPLY,
            BilinearGroupCRSPayload(groupBytes, crsBytes)
        )

        send(requestingPeer, groupAndCrsPacket)
    }

    private fun onGetGroupDescriptionAndCRSReplyPacket(packet: Packet) {
        val (_, payload) = packet.getAuthPayload(BilinearGroupCRSPayload)
        onGetGroupDescriptionAndCRSReply(payload)
    }

    fun onGetGroupDescriptionAndCRSReply(payload: BilinearGroupCRSPayload) {
        val groupElements = payload.bilinearGroupElements
        val crs = payload.crs
        val message = BilinearGroupCRSMessage(groupElements, crs)
        messageList.add(message)
    }

    fun registerAtTTP(name:String, myPublicKeyBytes: ByteArray,publicKeyTTP: ByteArray) {
        val ttpPeer = getPeerByPublicKeyBytes(publicKeyTTP) ?: throw Exception("TTP not found")

        val registerPacket = serializePacket(
            MessageID.REGISTER_AT_TTP,
            TTPRegistrationPayload(
                name,
                myPublicKeyBytes
            )
        )

        send(ttpPeer, registerPacket)
    }

    fun onGetRegisterAtTTPPacket(packet: Packet) {
        val (peer, payload) = packet.getAuthPayload(TTPRegistrationPayload)
        onGetRegisterAtTTP(peer, payload)
    }

    fun onGetRegisterAtTTP(peer: Peer, payload: TTPRegistrationPayload) {
        val senderPKBytes = peer.publicKey.keyToBin()
        val userName = payload.userName
        val userPKBytes = payload.publicKey

        val message = TTPRegistrationMessage(
            userName,
            userPKBytes,
            senderPKBytes
        )

        messageList.add(message)
    }

    fun getBlindSignatureRandomness(userPublicKeyBytes: ByteArray ,publicKeyBank: ByteArray) {
        val bankPeer = getPeerByPublicKeyBytes(publicKeyBank)

        bankPeer ?: throw Exception("Bank not found")

        val packet = serializePacket(
            MessageID.GET_BLIND_SIGNATURE_RANDOMNESS,
            ByteArrayPayload(
                userPublicKeyBytes
            )
        )

        send(bankPeer, packet)
    }

    fun onGetBlindSignatureRandomnessPacket(packet: Packet) {
        val (requestingPeer, payload) = packet.getAuthPayload(ByteArrayPayload)
        onGetBlindSignatureRandomness(requestingPeer, payload)
    }

    fun onGetBlindSignatureRandomness(requestingPeer: Peer, payload: ByteArrayPayload) {
        val publicKey = payload.bytes
        val message = BlindSignatureRandomnessRequestMessage(
            publicKey,
            requestingPeer
        )
        messageList.add(message)
    }

    fun sendBlindSignatureRandomnessReply(randomnessBytes: ByteArray, peer: Peer) {
        val packet = serializePacket(MessageID.GET_BLIND_SIGNATURE_RANDOMNESS_REPLY, ByteArrayPayload(randomnessBytes))
        send(peer, packet)
    }

    fun onGetBlindSignatureRandomnessReplyPacket(packet: Packet) {
        val (peer, payload) = packet.getAuthPayload(ByteArrayPayload)
        //TODO
    }

    fun onGetBlindSignatureRandomnessReply(payload: ByteArrayPayload) {
        val message = BlindSignatureRandomnessReplyMessage(payload.bytes)
        messageList.add(message)
    }

    fun getBlindSignature(challenge: BigInteger, publicKeyBytes: ByteArray, bankPublicKeyBytes: ByteArray) {

        val bankPeer = getRandomPeer(bankPublicKeyBytes)

        val packet = serializePacket(
            MessageID.GET_BLIND_SIGNATURE,
            BlindSignatureRequestPayload(
                challenge, publicKeyBytes
            )
        )

        send(bankPeer!!, packet)
    }


    fun onGetBlindSignaturePacket(packet: Packet) {
        val (requestingPeer, payload) = packet.getAuthPayload(BlindSignatureRequestPayload)
        onGetBlindSignature(requestingPeer, payload)
    }

    fun onGetBlindSignature(requestingPeer: Peer, payload: BlindSignatureRequestPayload) {
        val message = BlindSignatureRequestMessage(
            payload.challenge,
            payload.publicKeyBytes,
            requestingPeer
        )
        messageList.add(message)
    }

    fun sendBlindSignature(signature: BigInteger, peer: Peer) {
        val packet = serializePacket(MessageID.GET_BLIND_SIGNATURE_REPLY, ByteArrayPayload(signature.toByteArray()))
        send(peer, packet)
    }

    fun onGetBlindSignatureReplyPacket(packet: Packet) {
        val (requestingPeer, payload) = packet.getAuthPayload(ByteArrayPayload)
        onGetBlindSignatureReply(requestingPeer, payload)
    }

    fun onGetBlindSignatureReply(requestingPeer: Peer, payload: ByteArrayPayload) {
        val message = BlindSignatureReplyMessage(
            BigInteger(payload.bytes)
        )
        messageList.add(message)
    }

    fun getTransactionRandomizationElements(publicKeyReceiver: ByteArray) {
        val peer = getPeerByPublicKeyBytes(publicKeyReceiver)

        peer ?: throw Exception("User not found")

        val packet = serializePacket(
            MessageID.GET_BLIND_SIGNATURE_RANDOMNESS,
            ByteArrayPayload(
                ByteArray(0)
            )
        )

        send(peer, packet)
    }

    fun onGetTransactionRandomizationElementsPacket(packet: Packet) {
        //TODO
    }

    fun onGetTransactionRandomizationElementsReplyPacket(packet: Packet) {
        //TODO
    }

    fun sendTransactionDetails(publicKeyReceiver: ByteArray, transactionDetails: TransactionDetails) {
        //TODO
    }
    fun onTransactionPacket(packet: Packet) {
        //TODO
    }

//    private fun onFindBank(requestingPeer: Peer) {
//        // Do nothing if you are not a bank
//        if (role == Role.User)
//            return
//
//        val (eb, nb) = bank.getPublicRSAValues()
//        val bankDetails = BankDetails(
//            name,
//            bank.z,
//            eb,
//            nb,
//            myPeer.publicKey.keyToBin()
//        )
//        // Create the response
//        val responsePacket = serializePacket(
//            MessageID.BANK_DETAILS_REPLY,
//            BankDetailsPayload(bankDetails)
//        )
//
//        // Send the reply
//        send(requestingPeer, responsePacket)
//    }
//
//    private fun onBankDetailsReplyPacket(packet: Packet) {
//        val (_, payload) = packet.getAuthPayload(BankDetailsPayload)
//        onBankDetailsReply(payload)
//
//    }
//
//    private fun onBankDetailsReply(payload: BankDetailsPayload) {
//        val bankDetails = payload.bankDetails
//        user.handleBankDetailsReply(bankDetails)
//    }
//
//    fun sendUserRegistrationMessage(userRegistrationMessage: UserRegistrationMessage,
//                                    publicKeyBank: ByteArray): Boolean {
//        val bankPeer = getPeerByPublicKeyBytes(publicKeyBank) ?: return false
//
//        val packet = serializePacket(
//            MessageID.USER_REGISTRATION,
//            UserRegistrationPayload(userRegistrationMessage)
//        )
//
//        send(bankPeer, packet)
//        return true
//    }
//    private fun onUserRegistrationPacket(packet: Packet) {
//        val (userPeer, payload) = packet.getAuthPayload(UserRegistrationPayload)
//        onUserRegistration(userPeer, payload)
//    }
//
//    private fun onUserRegistration(peer: Peer, payload: UserRegistrationPayload) {
//        val response = bank.handleUserRegistration(payload.userRegistrationMessage)
//        val responseMessagePacket = serializePacket(
//            MessageID.USER_REGISTRATION_REPLY,
//            UserRegistrationResponsePayload(response)
//        )
//
//        send(peer, responseMessagePacket)
//    }
//
//    fun sendUnsignedTokens(tokensToSign: List<UnsignedTokenSignRequestEntry>,
//                           publicKeyBank: ByteArray) {
//        val bankPeer = getPeerByPublicKeyBytes(publicKeyBank) ?: return
//
//        val signRequestPacket = serializePacket(
//            MessageID.TOKEN_SIGN_REQUEST,
//            UnsignedTokenPayload(user.name, tokensToSign)
//        )
//
//        send(bankPeer, signRequestPacket)
//    }
//
//    fun sendTokensToRandomPeer(tokens: List<Token>, bank: BankRegistration): Boolean {
//        val randomPeer: Peer = getRandomPeer(bank.bankDetails.publicKeyBytes) ?: return false
//
//        val tokenPacket = serializePacket(
//            MessageID.SEND_TOKENS,
//            SendTokensPayload(bank.bankDetails.name, tokens)
//        )
//
//        send(randomPeer, tokenPacket)
//        return true
//    }
//
//    fun sendReceiptsToBank(receipts: List<Receipt>, userName: String, bankPublicKeyBytes: ByteArray): Boolean {
//        val bankPeer = getPeerByPublicKeyBytes(bankPublicKeyBytes) ?: return false
//
//        val packet = serializePacket(MessageID.DEPOSIT, DepositPayload(userName, receipts))
//
//        send(bankPeer, packet)
//        return true
//    }
//
//    private fun onUserRegistrationReplyPacket(packet: Packet) {
//        val (_, payload) = packet.getAuthPayload(UserRegistrationResponsePayload)
//        onUserRegistrationReply(payload)
//    }
//
//    private fun onUserRegistrationReply(payload: UserRegistrationResponsePayload) {
//        user.handleRegistrationResponse(payload.userRegistrationResponseMessage)
//    }
//
//    @RequiresApi(Build.VERSION_CODES.O)
//    private fun onTokenSignRequestPacket(packet: Packet) {
//        val (peer, payload) = packet.getAuthPayload(UnsignedTokenPayload)
//        onTokenSignRequest(peer, payload)
//    }
//
//    @RequiresApi(Build.VERSION_CODES.O)
//    private fun onTokenSignRequest(peer: Peer, payload: UnsignedTokenPayload) {
//        val userName = payload.userName
//        val tokensToSign = payload.tokensToSign
//        val responseList = bank.handleSignUnsignedTokenRequest(userName, tokensToSign)
//
//        val responsePacket = serializePacket(
//            MessageID.TOKEN_SIGN_REPLY,
//            UnsignedTokenResponsePayload(bank.name, responseList)
//        )
//
//        send(peer, responsePacket)
//    }
//
//    private fun onTokenSignReplyPacket(packet: Packet) {
//        val (_, payload) = packet.getAuthPayload(UnsignedTokenResponsePayload)
//        onTokenSignReply(payload)
//    }
//
//    private fun onTokenSignReply(payload: UnsignedTokenResponsePayload) {
//        user.handleUnsignedTokenSignResponse(payload.bankName, payload.signedTokens)
//    }
//
//    private fun onTokensReceivedPacket(packet: Packet) {
//        val (peer, payload) = packet.getAuthPayload(SendTokensPayload)
//        onTokensReceived(peer, payload)
//    }
//
//    private fun onTokensReceived(peer: Peer, payload: SendTokensPayload) {
//        val challenges = user.onTokensReceived(payload.tokens, payload.bankName)
//        sendChallenges(challenges, payload.bankName, peer)
//    }
//
//    private fun sendChallenges(challenges: List<Challenge>, bankName: String, peer: Peer) {
//        val packet = serializePacket(MessageID.CHALLENGE, ChallengePayload(bankName, challenges))
//        send(peer, packet)
//    }
//
//    private fun onChallengePacket(packet: Packet) {
//        val (peer, payload) = packet.getAuthPayload(ChallengePayload)
//        val response = user.onChallenges(payload.challenges, payload.bankName)
//        sendChallengeResponses(peer, payload.bankName, response)
//    }
//
//    private fun sendChallengeResponses(peer: Peer, bankName: String, challengeResponses: List<ChallengeResponse>) {
//        val packet = serializePacket(MessageID.CHALLENGE_REPLY, ChallengeResponsePayload(bankName, challengeResponses))
//        send(peer, packet)
//    }
//
//    private fun onChallengeResponsePacket(packet: Packet) {
//        val (_, payload) = packet.getAuthPayload(ChallengeResponsePayload)
//        onChallengeResponse(payload)
//    }
//
//    private fun onChallengeResponse(payload: ChallengeResponsePayload) {
//        user.onChallengesResponseReceived(payload.challenges, payload.bankName)
//    }
//
//    private fun onDepositPacket(packet: Packet) {
//        val (_, payload) = packet.getAuthPayload(DepositPayload)
//        onDeposit(payload)
//    }
//
//    private fun onDeposit(payload: DepositPayload) {
//        bank.handleOnDeposit(payload.receipts, payload.userName)
//    }

    private fun getPeerByPublicKeyBytes(publicKey: ByteArray): Peer? {
        return getPeers().find {
            it.publicKey.keyToBin().contentEquals(publicKey)
        }
    }

    private fun getRandomPeer(publicKeyBank: ByteArray): Peer? {
        return getPeers().shuffled().find {
            !it.publicKey.keyToBin().contentEquals(publicKeyBank)
        }
    }

    class Factory(
        private val settings: TrustChainSettings,
        private val database: TrustChainStore,
        private val crawler: TrustChainCrawler = TrustChainCrawler()
    ) : Overlay.Factory<OfflineEuroCommunity>(OfflineEuroCommunity::class.java) {
        override fun create(): OfflineEuroCommunity {
            return OfflineEuroCommunity(settings, database, crawler)
        }
    }
}
