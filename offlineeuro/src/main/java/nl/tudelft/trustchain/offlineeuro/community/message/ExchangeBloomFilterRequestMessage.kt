
package nl.tudelft.trustchain.offlineeuro.community.message

import nl.tudelft.ipv8.Peer
import nl.tudelft.trustchain.offlineeuro.cryptography.BloomFilter

class ExchangeBloomFilterRequestMessage(
    val bloomFilter: BloomFilter,
    val requestingPeer:Peer
) : ICommunityMessage {
    override val messageType: CommunityMessageType = CommunityMessageType.EXCHANGE_BLOOM_FILTER_REQUEST
}
