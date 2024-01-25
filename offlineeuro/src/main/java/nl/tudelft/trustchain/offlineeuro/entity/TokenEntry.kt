package nl.tudelft.trustchain.offlineeuro.entity

import java.math.BigInteger

class TokenEntry (
    private val id: Int,
    private val token: Token,
    private val w: BigInteger,
    private val y: BigInteger
){
}
