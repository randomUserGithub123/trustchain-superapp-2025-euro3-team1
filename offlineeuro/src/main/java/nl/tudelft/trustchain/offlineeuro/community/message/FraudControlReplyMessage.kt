package nl.tudelft.trustchain.offlineeuro.community.message

class FraudControlReplyMessage(
    val result: String,
) : ICommunityMessage {
    override val messageType = CommunityMessageType.FraudControlReplyMessage
}
