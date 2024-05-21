package nl.tudelft.trustchain.offlineeuro.community.payload

import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.messaging.Serializable
import nl.tudelft.ipv8.messaging.deserializeVarLen
import nl.tudelft.ipv8.messaging.serializeVarLen
import nl.tudelft.trustchain.offlineeuro.cryptography.RandomizationElementsBytes

class TransactionRandomizationElementsPayload(
    val transactionRandomizationElementsBytes: RandomizationElementsBytes
) : Serializable {
    override fun serialize(): ByteArray {
        var payload = ByteArray(0)
        payload += serializeVarLen(transactionRandomizationElementsBytes.group2T)
        payload += serializeVarLen(transactionRandomizationElementsBytes.vT)
        payload += serializeVarLen(transactionRandomizationElementsBytes.group1TInv)
        payload += serializeVarLen(transactionRandomizationElementsBytes.uTInv)
        return payload
    }

    companion object Deserializer : Deserializable<TransactionRandomizationElementsPayload> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<TransactionRandomizationElementsPayload, Int> {
            var localOffset = offset

            val (group2TBytes, group2TSize) = deserializeVarLen(buffer, localOffset)
            localOffset += group2TSize

            val (vTBytes, vTSize) = deserializeVarLen(buffer, localOffset)
            localOffset += vTSize

            val (group1TInvBytes, group1TInvSize) = deserializeVarLen(buffer, localOffset)
            localOffset += group1TInvSize

            val (uTInvBytes, uTInvSize) = deserializeVarLen(buffer, localOffset)
            localOffset += uTInvSize

            val randomizationElements =
                RandomizationElementsBytes(
                    group2TBytes,
                    vTBytes,
                    group1TInvBytes,
                    uTInvBytes
                )
            return Pair(
                TransactionRandomizationElementsPayload(randomizationElements),
                localOffset - offset
            )
        }
    }
}
