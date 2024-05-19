package nl.tudelft.trustchain.offlineeuro.community.message

enum class CommunityMessageType {
    GroupDescriptionCRSRequestMessage,
    GroupDescriptionCRSReplyMessage,

    TTPRegistrationMessage,

    BlindSignatureRandomnessRequestMessage,
    BlindSignatureRandomnessReplyMessage,

    BlindSignatureRequestMessage,
    BlindSignatureReplyMessage,

    TransactionRandomnessRequestMessage,
    TransactionRandomnessReplyMessage,

    TransactionMessage,
    TransactionResultMessage
}
