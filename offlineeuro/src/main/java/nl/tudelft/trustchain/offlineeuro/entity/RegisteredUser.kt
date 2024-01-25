package nl.tudelft.trustchain.offlineeuro.entity

import java.math.BigInteger

data class RegisteredUser(
    val id: Int,
    val name: String,
    val s: BigInteger,
    val k: BigInteger,
    val v: BigInteger,
    val r: BigInteger
) {}
