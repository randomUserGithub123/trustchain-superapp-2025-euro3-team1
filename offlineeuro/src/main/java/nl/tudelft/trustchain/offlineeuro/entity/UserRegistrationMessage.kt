package nl.tudelft.trustchain.offlineeuro.entity

import java.math.BigInteger

enum class MessageResult {
    SuccessFul,
    Failed;

    companion object {
        fun fromInt(value: Int): MessageResult {
            return MessageResult.values().find { it.ordinal == value }!!
        }
    }
}

data class UserRegistrationMessage(
    val userName: String,
    val i: Pair<BigInteger, BigInteger>,
    val arm: BigInteger
)

data class UserRegistrationResponseMessage(
    val result: MessageResult,
    val bankName: String,
    val v: BigInteger,
    val r: BigInteger,
    val errorMessage: String
)
