package nl.tudelft.trustchain.offlineeuro.community.payload

import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.messaging.Serializable
import nl.tudelft.ipv8.messaging.deserializeVarLen
import nl.tudelft.ipv8.messaging.serializeVarLen

class ByteArrayPayload(
    val bytes: ByteArray
) : Serializable {
    override fun serialize(): ByteArray {
        var payload = ByteArray(0)
        payload += serializeVarLen(bytes)
        return payload
    }

    companion object Deserializer : Deserializable<ByteArrayPayload> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<ByteArrayPayload, Int> {
            var localOffset = offset

            val (publicKeyBytes, publicKeySize) = deserializeVarLen(buffer, localOffset)
            localOffset += publicKeySize


            return Pair(
                ByteArrayPayload(publicKeyBytes),
                localOffset - offset
            )
        }
    }
}
