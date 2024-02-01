package nl.tudelft.trustchain.offlineeuro.community

import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.messaging.SERIALIZED_LONG_SIZE
import nl.tudelft.ipv8.messaging.Serializable
import nl.tudelft.ipv8.messaging.deserializeLong
import nl.tudelft.ipv8.messaging.deserializeVarLen
import nl.tudelft.ipv8.messaging.serializeLong
import nl.tudelft.ipv8.messaging.serializeVarLen
import nl.tudelft.trustchain.offlineeuro.entity.UnsignedTokenSignResponseEntry
import nl.tudelft.trustchain.offlineeuro.entity.UnsignedTokenStatus
import java.math.BigInteger

class UnsignedTokenResponsePayload (
    val bankName: String,
    val signedTokens: List<UnsignedTokenSignResponseEntry>
): Serializable {
    override fun serialize(): ByteArray {
        var payload = ByteArray(0)

        payload += serializeVarLen(bankName.toByteArray())

        for (tokenToSign in signedTokens) {
            payload += serializeLong(tokenToSign.id)
            payload += serializeVarLen(tokenToSign.aPrime.toByteArray())
            payload += serializeVarLen(tokenToSign.cPrime.toByteArray())
            payload += serializeVarLen(tokenToSign.t.toByteArray())
            payload += serializeLong(tokenToSign.status.ordinal.toLong())
        }

        return payload
    }

    companion object Deserializer : Deserializable<UnsignedTokenResponsePayload> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<UnsignedTokenResponsePayload, Int> {

            var localOffset = offset
            val responseEntries = arrayListOf<UnsignedTokenSignResponseEntry>()

            val (bankName, bankNameSize) = deserializeVarLen(buffer, localOffset)
            localOffset += bankNameSize

            while (localOffset < buffer.size) {

                val id = deserializeLong(buffer, localOffset)
                localOffset += SERIALIZED_LONG_SIZE

                val (aBytes, aSize) = deserializeVarLen(buffer, localOffset)
                localOffset += aSize

                val (cBytes, cSize) = deserializeVarLen(buffer, localOffset)
                localOffset += cSize

                val (tBytes, tSize) = deserializeVarLen(buffer, offset)
                localOffset += tSize

                val statusLong = deserializeLong(buffer, localOffset)
                localOffset += SERIALIZED_LONG_SIZE

                val status = UnsignedTokenStatus.fromInt(statusLong.toInt())
                val responseEntry = UnsignedTokenSignResponseEntry(id, BigInteger(aBytes), BigInteger(cBytes), tBytes.toString(), status)
                responseEntries.add(responseEntry)
            }

            return Pair(
                UnsignedTokenResponsePayload(bankName.toString(), responseEntries),
                localOffset - offset
            )
        }
    }
}
