package nl.tudelft.trustchain.offlineeuro.entity

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
}
