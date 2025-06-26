
package nl.tudelft.trustchain.offlineeuro.community.message

import nl.tudelft.trustchain.offlineeuro.cryptography.BloomFilter

class ExchangeBloomFilterReplyMessage(
    val bloomFilter: BloomFilter
) : ICommunityMessage {
    override val messageType: CommunityMessageType = CommunityMessageType.EXCHANGE_BLOOM_FILTER_REPLY
}
