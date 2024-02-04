package nl.tudelft.trustchain.offlineeuro.entity

import java.math.BigInteger

class TokenEntry (
    val id: Long,
    val token: Token,
    val w: BigInteger,
    val y: BigInteger,
    val bankId: Long
){
}
