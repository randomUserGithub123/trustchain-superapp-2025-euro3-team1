package nl.tudelft.trustchain.offlineeuro.entity

import java.math.BigInteger

class BankDetails (
    val name: String,
    val z: BigInteger,
    val eb: BigInteger,
    val nb: BigInteger,
    val publicKeyBytes: ByteArray
)
