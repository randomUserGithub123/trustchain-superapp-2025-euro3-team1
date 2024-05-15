package nl.tudelft.trustchain.offlineeuro.community.message

class TransactionResultMessage (
    val result: String,
): ICommunityMessage {
    override val messageType = CommunityMessageType.TransactionResultMessage
}
