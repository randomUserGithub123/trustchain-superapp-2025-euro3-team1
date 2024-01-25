package nl.tudelft.trustchain.offlineeuro.entity

import java.math.BigInteger

class Receipt (
    val token: Token,
    val gamma: BigInteger,
    val challenge: BigInteger
)
