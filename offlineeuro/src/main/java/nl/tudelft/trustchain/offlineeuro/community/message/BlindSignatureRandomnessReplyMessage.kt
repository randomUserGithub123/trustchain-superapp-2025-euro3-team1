package nl.tudelft.trustchain.offlineeuro.community.message

class BlindSignatureRandomnessReplyMessage(
    val randomnessBytes: ByteArray,
) : ICommunityMessage {
    override val messageType = CommunityMessageType.BlindSignatureRandomnessReplyMessage
}
