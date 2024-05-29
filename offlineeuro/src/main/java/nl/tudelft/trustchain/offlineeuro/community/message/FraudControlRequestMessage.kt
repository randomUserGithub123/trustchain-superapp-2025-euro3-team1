package nl.tudelft.trustchain.offlineeuro.community.message

import nl.tudelft.ipv8.Peer

class FraudControlRequestMessage(
    val firstProofBytes: ByteArray,
    val secondProofBytes: ByteArray,
    val requestingPeer: Peer
) : ICommunityMessage {
    override val messageType = CommunityMessageType.FraudControlRequestMessage
}
