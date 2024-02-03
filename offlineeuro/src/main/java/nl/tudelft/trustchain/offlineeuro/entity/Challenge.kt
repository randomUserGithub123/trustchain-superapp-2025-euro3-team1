package nl.tudelft.trustchain.offlineeuro.entity

import java.math.BigInteger

data class Challenge(
    val token: Token,
    val challenge: BigInteger,
)

data class ChallengeResponse(
    val token: Token,
    val challenge: BigInteger,
    val gamma: BigInteger
)
