package nl.tudelft.trustchain.offlineeuro.community.payload

import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.messaging.Serializable

class BloomFilterRequestPayload : Serializable {
    override fun serialize(): ByteArray {
        return ByteArray(0) // Empty payload
    }

    companion object Deserializer : Deserializable<BloomFilterRequestPayload> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<BloomFilterRequestPayload, Int> {
            // For an empty payload, there's no data to read, so the size consumed is 0.
            return Pair(BloomFilterRequestPayload(), 0)
        }
    }
}
