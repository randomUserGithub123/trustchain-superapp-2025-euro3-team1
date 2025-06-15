package nl.tudelft.trustchain.offlineeuro.community.payload

import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.messaging.SERIALIZED_INT_SIZE
import nl.tudelft.ipv8.messaging.SERIALIZED_LONG_SIZE
import nl.tudelft.ipv8.messaging.Serializable
import nl.tudelft.ipv8.messaging.deserializeInt
import nl.tudelft.ipv8.messaging.deserializeLong
import nl.tudelft.ipv8.messaging.deserializeVarLen
import nl.tudelft.ipv8.messaging.serializeInt
import nl.tudelft.ipv8.messaging.serializeLong
import nl.tudelft.ipv8.messaging.serializeVarLen

class BloomFilterReplyPayload(
    val bloomFilterBytes: ByteArray,
    val expectedElements: Int,
    val falsePositiveRate: Double
) : Serializable {
    private const val MAX_CHUNK_SIZE = 900
    override fun serialize(): ByteArray {
        var payload = ByteArray(0)
        
        // Split into chunks
        val chunks = mutableListOf<ByteArray>()
        var remaining = bloomFilterBytes
        while (remaining.isNotEmpty()) {
            val chunkSize = minOf(MAX_CHUNK_SIZE, remaining.size)
            chunks.add(remaining.copyOfRange(0, chunkSize))
            remaining = remaining.copyOfRange(chunkSize, remaining.size)
        }
        
        payload += serializeInt(chunks.size) // Number of chunks
        
        // Serialize each chunk
        for (chunk in chunks) {
            payload += serializeVarLen(chunk)
        }
        
        payload += serializeInt(expectedElements)
        payload += serializeLong(falsePositiveRate.toBits())
        return payload
    }

    companion object Deserializer : Deserializable<BloomFilterReplyPayload> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<BloomFilterReplyPayload, Int> {
            var localOffset = offset

            // Read number of chunks
            val numChunks = deserializeInt(buffer, localOffset)
            localOffset += SERIALIZED_INT_SIZE

            // Read and combine chunks
            val chunks = mutableListOf<ByteArray>()
            for (i in 0 until numChunks) {
                val (chunk, chunkSize) = deserializeVarLen(buffer, localOffset)
                chunks.add(chunk)
                localOffset += chunkSize
            }
            val bloomFilterBytes = chunks.reduce { acc, bytes -> acc + bytes }

            val expectedElements = deserializeInt(buffer, localOffset)
            localOffset += SERIALIZED_INT_SIZE

            val falsePositiveRateBits = deserializeLong(buffer, localOffset)
            localOffset += SERIALIZED_LONG_SIZE
            val falsePositiveRate = Double.fromBits(falsePositiveRateBits)

            return Pair(
                BloomFilterReplyPayload(bloomFilterBytes, expectedElements, falsePositiveRate),
                localOffset - offset
            )
        }
    }
}
