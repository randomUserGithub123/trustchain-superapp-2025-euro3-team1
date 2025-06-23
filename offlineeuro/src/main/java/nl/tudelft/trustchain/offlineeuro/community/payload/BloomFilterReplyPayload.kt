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
    override fun serialize(): ByteArray {
        var payload = ByteArray(0)
        payload += serializeVarLen(bloomFilterBytes)
        payload += serializeInt(expectedElements)
        // Serialize Double by converting to its raw Long bits
        payload += serializeLong(falsePositiveRate.toBits())
        return payload
    }

    companion object Deserializer : Deserializable<BloomFilterReplyPayload> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<BloomFilterReplyPayload, Int> {
            var localOffset = offset

            val (bfBytes, bfSize) = deserializeVarLen(buffer, localOffset)
            localOffset += bfSize

            val expectedElements = deserializeInt(buffer, localOffset)
            localOffset += SERIALIZED_INT_SIZE

            // Deserialize Long and convert back to Double from its raw bits
            val falsePositiveRateBits = deserializeLong(buffer, localOffset)
            localOffset += SERIALIZED_LONG_SIZE
            val falsePositiveRate = Double.fromBits(falsePositiveRateBits)

            return Pair(
                BloomFilterReplyPayload(bfBytes, expectedElements, falsePositiveRate),
                localOffset - offset
            )
        }
    }
}
