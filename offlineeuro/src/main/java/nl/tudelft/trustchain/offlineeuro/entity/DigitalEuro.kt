package nl.tudelft.trustchain.offlineeuro.entity

import it.unisa.dia.gas.jpbc.Element
import java.math.BigInteger

class DigitalEuro (
    val serialNumber: BigInteger,
    val firstTheta1: Element,
    val signature: BigInteger,
    val proofs: ArrayList<GrothSahaiProof> = arrayListOf(),
)
