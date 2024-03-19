package nl.tudelft.trustchain.offlineeuro.entity

import java.math.BigInteger

class DigitalEuro (
    val proofs: ArrayList<GrothSahaiProof> = arrayListOf(),
    val signature: BigInteger

) {

}
