package nl.tudelft.trustchain.offlineeuro.community.payload

import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.messaging.Serializable
import nl.tudelft.ipv8.messaging.deserializeVarLen
import nl.tudelft.ipv8.messaging.serializeVarLen

class RequestPayload(
    val publicKeyBytes: ByteArray
) : Serializable {
    override fun serialize(): ByteArray {
        var payload = ByteArray(0)
        payload += serializeVarLen(publicKeyBytes)
        return payload
    }

    companion object Deserializer : Deserializable<RequestPayload> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<RequestPayload, Int> {
            var localOffset = offset

            val (publicKeyBytes, publicKeySize) = deserializeVarLen(buffer, localOffset)
            localOffset += publicKeySize


            return Pair(
                RequestPayload(publicKeyBytes),
                localOffset - offset
            )
        }
    }
}
