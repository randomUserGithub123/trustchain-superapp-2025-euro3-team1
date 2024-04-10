package nl.tudelft.trustchain.offlineeuro.entity

import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.cryptography.Schnorr
import nl.tudelft.trustchain.offlineeuro.cryptography.SchnorrSignature

class DigitalEuro (
    val serialNumber: String,
    val firstTheta1: Element,
    val signature: SchnorrSignature,
    val proofs: ArrayList<GrothSahaiProof> = arrayListOf(),
) {

    fun verifySignature(publicKeySigner: Element, group: BilinearGroup): Boolean {
        return Schnorr.verifySchnorrSignature(signature, publicKeySigner, group)
    }
}
