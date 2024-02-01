package nl.tudelft.trustchain.offlineeuro.community

import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.messaging.SERIALIZED_LONG_SIZE
import nl.tudelft.ipv8.messaging.Serializable
import nl.tudelft.ipv8.messaging.deserializeLong
import nl.tudelft.ipv8.messaging.deserializeVarLen
import nl.tudelft.ipv8.messaging.serializeLong
import nl.tudelft.ipv8.messaging.serializeVarLen
import nl.tudelft.trustchain.offlineeuro.entity.UnsignedTokenSignRequestEntry
import java.math.BigInteger

class UnsignedTokenPayload(
    val tokensToSign: List<UnsignedTokenSignRequestEntry>
): Serializable {
    override fun serialize(): ByteArray {

        var payload = ByteArray(0)

        for (tokenToSign in tokensToSign) {
            payload += serializeLong(tokenToSign.id)
            payload += serializeVarLen(tokenToSign.a.toByteArray())
            payload += serializeVarLen(tokenToSign.c.toByteArray())
        }

        return payload
    }

    companion object Deserializer : Deserializable<UnsignedTokenPayload> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<UnsignedTokenPayload, Int> {

            var localOffset = offset
            val requestEntries = arrayListOf<UnsignedTokenSignRequestEntry>()

            while (localOffset < buffer.size) {

                val id = deserializeLong(buffer, localOffset)
                localOffset += SERIALIZED_LONG_SIZE

                val (aBytes, aSize) = deserializeVarLen(buffer, localOffset)
                localOffset += aSize

                val (cBytes, cSize) = deserializeVarLen(buffer, localOffset)
                localOffset += cSize

                requestEntries.add(UnsignedTokenSignRequestEntry(id, BigInteger(aBytes), BigInteger(cBytes)))
            }

            return Pair(
                UnsignedTokenPayload(requestEntries),
                localOffset - offset
            )
        }
    }
}

