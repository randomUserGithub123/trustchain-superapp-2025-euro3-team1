package nl.tudelft.trustchain.offlineeuro.community.message

import nl.tudelft.ipv8.Peer

data class BloomFilterRequestMessage(
    val requestingPeer: Peer
) : ICommunityMessage {
    override val messageType = CommunityMessageType.BloomFilterRequestMessage
}

data class BloomFilterReplyMessage(
    val bloomFilterBytes: ByteArray,
    val expectedElements: Int,
    val falsePositiveRate: Double,
    val requestingPeer: Peer
) : ICommunityMessage {
    override val messageType = CommunityMessageType.BloomFilterReplyMessage

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BloomFilterReplyMessage

        if (!bloomFilterBytes.contentEquals(other.bloomFilterBytes)) return false
        if (expectedElements != other.expectedElements) return false
        if (falsePositiveRate != other.falsePositiveRate) return false
        if (requestingPeer != other.requestingPeer) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bloomFilterBytes.contentHashCode()
        result = 31 * result + expectedElements
        result = 31 * result + falsePositiveRate.hashCode()
        result = 31 * result + requestingPeer.hashCode()
        return result
    }
} 