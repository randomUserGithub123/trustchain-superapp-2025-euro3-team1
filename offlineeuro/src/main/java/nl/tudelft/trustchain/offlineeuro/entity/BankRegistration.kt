package nl.tudelft.trustchain.offlineeuro.entity

import java.math.BigInteger

class BankRegistration (
    val id: Long,
    val bankDetails: BankDetails,
    val m: BigInteger?,
    val rm: BigInteger?,
    val userName: String?,
    val v: BigInteger?,
    val r: BigInteger?,
)
