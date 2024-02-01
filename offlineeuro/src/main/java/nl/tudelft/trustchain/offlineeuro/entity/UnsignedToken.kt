package nl.tudelft.trustchain.offlineeuro.entity

import java.math.BigInteger

class UnsignedToken (
    val id: Long,
    val a: BigInteger,
    val c: BigInteger,
    val bigA: BigInteger,
    val beta1: BigInteger,
    val beta2: BigInteger,
    val l: BigInteger,
    val u: BigInteger,
    val g: BigInteger,
    val y: BigInteger,
    val w: BigInteger,
    val bankId: Long,
    val status: UnsignedTokenStatus,
)

data class UnsignedTokenSignRequestEntry (
    val id: Long,
    val a: BigInteger,
    val c: BigInteger,
)

data class UnsignedTokenSignResponseEntry (
    val id: Long,
    val aPrime: BigInteger,
    val cPrime: BigInteger,
    val t: String,
    val status: UnsignedTokenStatus,
)

data class UnsignedTokenAdd(
    val a: BigInteger,
    val c: BigInteger,
    val bigA: BigInteger,
    val beta1: BigInteger,
    val beta2: BigInteger,
    val l: BigInteger,
    val u: BigInteger,
    val g: BigInteger,
    val y: BigInteger,
    val w: BigInteger,
    val bankId: Long,
    val status: UnsignedTokenStatus = UnsignedTokenStatus.CREATED,
)

enum class UnsignedTokenStatus {
    CREATED,
    SENT,
    REJECTED,
    SIGNED;

    companion object {
        fun fromInt(value: Int): UnsignedTokenStatus {
            return UnsignedTokenStatus.values().find { it.ordinal == value }!!
        }
    }
}
