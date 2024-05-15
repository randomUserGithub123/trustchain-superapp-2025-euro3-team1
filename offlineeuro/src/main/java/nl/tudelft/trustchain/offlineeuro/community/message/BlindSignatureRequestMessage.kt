package nl.tudelft.trustchain.offlineeuro.community.message

import nl.tudelft.ipv8.Peer
import java.math.BigInteger

class BlindSignatureRequestMessage(
    val challenge: BigInteger,
    val publicKeyBytes: ByteArray,
    val peer: Peer
) : ICommunityMessage {
    override val messageType = CommunityMessageType.BlindSignatureRequestMessage
}
