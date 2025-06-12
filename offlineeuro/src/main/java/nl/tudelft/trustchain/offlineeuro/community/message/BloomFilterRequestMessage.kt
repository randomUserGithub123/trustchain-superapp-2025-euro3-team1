package nl.tudelft.trustchain.offlineeuro.community.message

import nl.tudelft.ipv8.Peer

class BloomFilterRequestMessage(val requestingPeer: Peer) : ICommunityMessage {
    override val messageType: CommunityMessageType = CommunityMessageType.BLOOM_FILTER_REQUEST
}
