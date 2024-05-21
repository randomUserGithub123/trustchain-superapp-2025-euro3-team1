package nl.tudelft.trustchain.offlineeuro.community

import nl.tudelft.ipv8.Overlay
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCrawler
import nl.tudelft.ipv8.attestation.trustchain.TrustChainSettings
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainStore
import nl.tudelft.ipv8.messaging.Packet
import nl.tudelft.trustchain.offlineeuro.community.message.BilinearGroupCRSReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BilinearGroupCRSRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureRandomnessReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureRandomnessRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.ICommunityMessage
import nl.tudelft.trustchain.offlineeuro.community.message.MessageList
import nl.tudelft.trustchain.offlineeuro.community.message.TTPRegistrationMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionRandomizationElementsReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionRandomizationElementsRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.payload.BilinearGroupCRSPayload
import nl.tudelft.trustchain.offlineeuro.community.payload.BlindSignatureRequestPayload
import nl.tudelft.trustchain.offlineeuro.community.payload.ByteArrayPayload
import nl.tudelft.trustchain.offlineeuro.community.payload.TTPRegistrationPayload
import nl.tudelft.trustchain.offlineeuro.community.payload.TransactionRandomizationElementsPayload
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroupElementsBytes
import nl.tudelft.trustchain.offlineeuro.cryptography.CRS
import nl.tudelft.trustchain.offlineeuro.cryptography.CRSBytes
import nl.tudelft.trustchain.offlineeuro.cryptography.RandomizationElementsBytes
import nl.tudelft.trustchain.offlineeuro.entity.TransactionDetailsBytes
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

class OfflineEuroCommunity(
    settings: TrustChainSettings,
    database: TrustChainStore,
    crawler: TrustChainCrawler = TrustChainCrawler()
) : TrustChainCommunity(settings, database, crawler) {
    override val serviceId = "ffffd716494b474ea9f614a16a4da0aed6899aec"

    lateinit var messageList: MessageList<ICommunityMessage>

    var role: Role = Role.User
    val name: String = "BestBank"
    lateinit var crs: CRS
    var bilinearGroup = BilinearGroup()

    init {

        messageHandlers[MessageID.GET_GROUP_DESCRIPTION_CRS] = ::onGetGroupDescriptionAndCRSPacket
        messageHandlers[MessageID.GET_GROUP_DESCRIPTION_CRS_REPLY] = ::onGetGroupDescriptionAndCRSReplyPacket

        messageHandlers[MessageID.REGISTER_AT_TTP] = ::onGetRegisterAtTTPPacket

        messageHandlers[MessageID.GET_BLIND_SIGNATURE_RANDOMNESS] = ::onGetBlindSignatureRandomnessPacket
        messageHandlers[MessageID.GET_BLIND_SIGNATURE_RANDOMNESS_REPLY] = ::onGetBlindSignatureRandomnessReplyPacket

        messageHandlers[MessageID.GET_BLIND_SIGNATURE] = ::onGetBlindSignaturePacket
        messageHandlers[MessageID.GET_BLIND_SIGNATURE_REPLY] = ::onGetBlindSignatureReplyPacket

        messageHandlers[MessageID.GET_TRANSACTION_RANDOMIZATION_ELEMENTS] = ::onGetTransactionRandomizationElementsRequestPacket
        messageHandlers[MessageID.GET_TRANSACTION_RANDOMIZATION_ELEMENTS_REPLY] = ::onGetTransactionRandomizationElementsReplyPacket

        messageHandlers[MessageID.TRANSACTION] = ::onTransactionPacket
    }

    fun getGroupDescriptionAndCRS() {
        val packet =
            serializePacket(
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
        val message = BilinearGroupCRSRequestMessage(requestingPeer)
        messageList.add(message)
    }

    fun sendGroupDescriptionAndCRS(
        groupBytes: BilinearGroupElementsBytes,
        crsBytes: CRSBytes,
        requestingPeer: Peer
    ) {
        val groupAndCrsPacket =
            serializePacket(
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
        val message = BilinearGroupCRSReplyMessage(groupElements, crs)
        messageList.add(message)
    }

    fun registerAtTTP(
        name: String,
        myPublicKeyBytes: ByteArray,
        publicKeyTTP: ByteArray
    ) {
        val ttpPeer = getPeerByPublicKeyBytes(publicKeyTTP) ?: throw Exception("TTP not found")

        val registerPacket =
            serializePacket(
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

    fun onGetRegisterAtTTP(
        peer: Peer,
        payload: TTPRegistrationPayload
    ) {
        val senderPKBytes = peer.publicKey.keyToBin()
        val userName = payload.userName
        val userPKBytes = payload.publicKey

        val message =
            TTPRegistrationMessage(
                userName,
                userPKBytes,
                senderPKBytes
            )

        messageList.add(message)
    }

    fun getBlindSignatureRandomness(
        userPublicKeyBytes: ByteArray,
        publicKeyBank: ByteArray
    ) {
        val bankPeer = getPeerByPublicKeyBytes(publicKeyBank)

        bankPeer ?: throw Exception("Bank not found")

        val packet =
            serializePacket(
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

    fun onGetBlindSignatureRandomness(
        requestingPeer: Peer,
        payload: ByteArrayPayload
    ) {
        val publicKey = payload.bytes
        val message =
            BlindSignatureRandomnessRequestMessage(
                publicKey,
                requestingPeer
            )
        messageList.add(message)
    }

    fun sendBlindSignatureRandomnessReply(
        randomnessBytes: ByteArray,
        peer: Peer
    ) {
        val packet = serializePacket(MessageID.GET_BLIND_SIGNATURE_RANDOMNESS_REPLY, ByteArrayPayload(randomnessBytes))
        send(peer, packet)
    }

    fun onGetBlindSignatureRandomnessReplyPacket(packet: Packet) {
        val (peer, payload) = packet.getAuthPayload(ByteArrayPayload)
        onGetBlindSignatureRandomnessReply(payload)
    }

    fun onGetBlindSignatureRandomnessReply(payload: ByteArrayPayload) {
        val message = BlindSignatureRandomnessReplyMessage(payload.bytes)
        messageList.add(message)
    }

    fun getBlindSignature(
        challenge: BigInteger,
        publicKeyBytes: ByteArray,
        bankPublicKeyBytes: ByteArray
    ) {
        val bankPeer = getRandomPeer(bankPublicKeyBytes)

        val packet =
            serializePacket(
                MessageID.GET_BLIND_SIGNATURE,
                BlindSignatureRequestPayload(
                    challenge,
                    publicKeyBytes
                )
            )

        send(bankPeer!!, packet)
    }

    fun onGetBlindSignaturePacket(packet: Packet) {
        val (requestingPeer, payload) = packet.getAuthPayload(BlindSignatureRequestPayload)
        onGetBlindSignature(requestingPeer, payload)
    }

    fun onGetBlindSignature(
        requestingPeer: Peer,
        payload: BlindSignatureRequestPayload
    ) {
        val message =
            BlindSignatureRequestMessage(
                payload.challenge,
                payload.publicKeyBytes,
                requestingPeer
            )
        messageList.add(message)
    }

    fun sendBlindSignature(
        signature: BigInteger,
        peer: Peer
    ) {
        val packet = serializePacket(MessageID.GET_BLIND_SIGNATURE_REPLY, ByteArrayPayload(signature.toByteArray()))
        send(peer, packet)
    }

    fun onGetBlindSignatureReplyPacket(packet: Packet) {
        val (requestingPeer, payload) = packet.getAuthPayload(ByteArrayPayload)
        onGetBlindSignatureReply(requestingPeer, payload)
    }

    fun onGetBlindSignatureReply(
        requestingPeer: Peer,
        payload: ByteArrayPayload
    ) {
        val message =
            BlindSignatureReplyMessage(
                BigInteger(payload.bytes)
            )
        messageList.add(message)
    }

    fun getTransactionRandomizationElements(publicKeyReceiver: ByteArray) {
        val peer = getPeerByPublicKeyBytes(publicKeyReceiver)

        peer ?: throw Exception("User not found")

        val packet =
            serializePacket(
                MessageID.GET_BLIND_SIGNATURE_RANDOMNESS,
                ByteArrayPayload(
                    ByteArray(0)
                )
            )

        send(peer, packet)
    }

    fun onGetTransactionRandomizationElementsRequestPacket(packet: Packet) {
        val (requestingPeer, payload) = packet.getAuthPayload(ByteArrayPayload)
        onGetTransactionRandomizationElementsRequest(requestingPeer, payload)
    }

    fun onGetTransactionRandomizationElementsRequest(
        requestingPeer: Peer,
        payload: ByteArrayPayload
    ) {
        val message = TransactionRandomizationElementsRequestMessage(payload.bytes, requestingPeer)
        messageList.add(message)
    }

    fun sendTransactionRandomizationElements(
        randomizationElementsBytes: RandomizationElementsBytes,
        requestingPeer: Peer
    ) {
        val packet =
            serializePacket(
                MessageID.GET_TRANSACTION_RANDOMIZATION_ELEMENTS_REPLY,
                TransactionRandomizationElementsPayload(randomizationElementsBytes)
            )
        send(requestingPeer, packet)
    }

    fun onGetTransactionRandomizationElementsReplyPacket(packet: Packet) {
        val (_, payload) = packet.getAuthPayload(TransactionRandomizationElementsPayload)
    }

    fun onGetTransactionRandomizationElements(payload: TransactionRandomizationElementsPayload) {
        val message = TransactionRandomizationElementsReplyMessage(payload.transactionRandomizationElementsBytes)
        messageList.add(message)
    }

    fun sendTransactionDetails(
        publicKeyReceiver: ByteArray,
        transactionDetails: TransactionDetailsBytes
    ) {
        // TODO
    }

    fun onTransactionPacket(packet: Packet) {
        // TODO
    }

    fun sendTransactionResult(
        transactionResult: String,
        requestingPeer: Peer
    ) {
        // TODO
    }

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
