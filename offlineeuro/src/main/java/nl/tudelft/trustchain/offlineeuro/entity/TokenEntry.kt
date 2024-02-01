package nl.tudelft.trustchain.offlineeuro.entity

import java.math.BigInteger

class TokenEntry (
    private val id: Long,
    val token: Token,
    val w: BigInteger,
    val y: BigInteger,
    private val bankId: Int
){
}
