package nl.tudelft.trustchain.offlineeuro.community.message

import nl.tudelft.trustchain.offlineeuro.cryptography.BloomFilter

class BloomFilterReplyMessage(
    val bloomFilter: BloomFilter
) : ICommunityMessage {
    override val messageType: CommunityMessageType = CommunityMessageType.BLOOM_FILTER_REPLY
} 
