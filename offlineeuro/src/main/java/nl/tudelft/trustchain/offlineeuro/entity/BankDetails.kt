package nl.tudelft.trustchain.offlineeuro.entity

import java.math.BigInteger

data class BankDetails (
    val name: String,
    val z: BigInteger,
    val eb: BigInteger,
    val nb: BigInteger
){

}
