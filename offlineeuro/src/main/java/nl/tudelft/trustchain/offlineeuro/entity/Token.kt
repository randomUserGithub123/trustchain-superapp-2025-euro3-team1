package nl.tudelft.trustchain.offlineeuro.entity

import nl.tudelft.ipv8.messaging.deserializeVarLen
import nl.tudelft.ipv8.messaging.serializeVarLen
import java.math.BigInteger

data class Token (
    val u: BigInteger,
    val g: BigInteger,
    val a: BigInteger,
    val r: BigInteger,
    val aPrime: BigInteger,
    val t: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Token) return false

        return u == other.u &&
            g == other.g &&
            a == other.a &&
            r == other.r &&
            aPrime == other.aPrime &&
            t == other.t
    }

    fun serialize(): ByteArray {

        var bytes = ByteArray(0)
        bytes += serializeVarLen(u.toByteArray())
        bytes += serializeVarLen(g.toByteArray())
        bytes += serializeVarLen(a.toByteArray())
        bytes += serializeVarLen(r.toByteArray())
        bytes += serializeVarLen(aPrime.toByteArray())
        bytes += serializeVarLen(t.toByteArray())
        return bytes
    }

    companion object {
        fun deserialize(buffer: ByteArray, offset: Int): Pair<Token, Int> {
            var localOffset = offset

            val (uBytes, uSize) = deserializeVarLen(buffer, localOffset)
            localOffset += uSize

            val (gBytes, gSize) = deserializeVarLen(buffer, localOffset)
            localOffset += gSize

            val (aBytes, aSize) = deserializeVarLen(buffer, localOffset)
            localOffset += aSize

            val (rBytes, rSize) = deserializeVarLen(buffer, localOffset)
            localOffset += rSize

            val (aPrimeBytes, aPrimeSize) = deserializeVarLen(buffer, localOffset)
            localOffset += aPrimeSize

            val (tBytes, tSize) = deserializeVarLen(buffer, localOffset)
            localOffset += tSize

            val token = Token(
                BigInteger(uBytes),
                BigInteger(gBytes),
                BigInteger(aBytes),
                BigInteger(rBytes),
                BigInteger(aPrimeBytes),
                tBytes.toString(Charsets.UTF_8),
            )

            return Pair(token, localOffset - offset)
        }
    }
}
