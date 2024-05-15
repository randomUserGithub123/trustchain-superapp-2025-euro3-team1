package nl.tudelft.trustchain.offlineeuro.community.message

import java.math.BigInteger

class BlindSignatureReplyMessage(
    val signature: BigInteger,
) : ICommunityMessage {
    override val messageType = CommunityMessageType.BlindSignatureReplyMessage
}
