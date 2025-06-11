package nl.tudelft.trustchain.offlineeuro.community.message

class BloomFilterRequestMessage : ICommunityMessage {
    override val messageType: CommunityMessageType = CommunityMessageType.BLOOM_FILTER_REQUEST
} 
