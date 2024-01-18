package nl.tudelft.trustchain.offlineeuro.entity

import java.math.BigInteger
import kotlin.reflect.typeOf

class Token (
    val u: BigInteger,
    val g: BigInteger,
    val A: BigInteger,
    val r: BigInteger,
    val ADoublePrime: BigInteger,
    val t: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Token) return false

        return u == other.u &&
            g == other.g &&
            A == other.A &&
            r == other.r &&
            ADoublePrime == other.ADoublePrime &&
            t == other.t
    }
}
