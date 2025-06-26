package nl.tudelft.trustchain.offlineeuro.community.message

enum class CommunityMessageType {
    AddressMessage,
    AddressRequestMessage,

    RegistrationMessage,

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
    TransactionResultMessage,

    FraudControlRequestMessage,
    FraudControlReplyMessage,

    BloomFilterRequestMessage,
    BloomFilterReplyMessage,

    BLOOM_FILTER_REPLY,

    BLOOM_FILTER_REQUEST,

    EXCHANGE_BLOOM_FILTER_REQUEST,
    EXCHANGE_BLOOM_FILTER_REPLY
}
