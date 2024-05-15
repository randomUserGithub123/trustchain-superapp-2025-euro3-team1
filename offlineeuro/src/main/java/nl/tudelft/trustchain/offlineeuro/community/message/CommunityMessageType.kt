package nl.tudelft.trustchain.offlineeuro.community.message

enum class CommunityMessageType {

    GroupDescriptionCRS,
    TTPRegistrationMessage,
    BlindSignatureRandomnessRequestMessage,
    BlindSignatureRandomnessReplyMessage,
    BlindSignatureRequestMessage,
    BlindSignatureReplyMessage,
    TransactionRequestMessage,
    TransactionRandomnessMessage,
    TransactionMessage,
    TransactionResultMessage
}
