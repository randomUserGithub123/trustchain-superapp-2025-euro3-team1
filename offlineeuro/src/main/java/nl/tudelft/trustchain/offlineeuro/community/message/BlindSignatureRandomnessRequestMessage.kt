package nl.tudelft.trustchain.offlineeuro.community.message

import nl.tudelft.ipv8.Peer

class BlindSignatureRandomnessRequestMessage(
    val publicKeyBytes: ByteArray,
    val peer: Peer
) : ICommunityMessage {
    override val messageType = CommunityMessageType.BlindSignatureRandomnessRequestMessage
}
