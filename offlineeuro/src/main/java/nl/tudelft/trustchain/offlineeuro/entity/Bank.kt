package nl.tudelft.trustchain.offlineeuro.entity

import android.content.Context
import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.cryptography.Schnorr
import java.math.BigInteger
import kotlin.math.min

class Bank (
    val name: String = "BestBank",
    private val context: Context?,
){
    private val privateKey: Element
    val publicKey: Element
    val group = CentralAuthority.groupDescription
    private val depositedEuros: ArrayList<DigitalEuro> = arrayListOf()
    private val withdrawUserRandomness: HashMap<Element, Element> = hashMapOf()

    init {
        privateKey = group.getRandomZr()
        publicKey = group.g.powZn(privateKey)
    }

    fun getBlindSignatureRandomness(userPublicKey: Element): Element {
        if (withdrawUserRandomness.containsKey(userPublicKey)) {
            val randomness = withdrawUserRandomness[userPublicKey]!!
            return group.g.powZn(randomness)
        }
        val randomness = group.getRandomZr()
        withdrawUserRandomness[userPublicKey] = randomness
        return group.g.powZn(randomness)
    }

    fun createBlindSignature(challenge: BigInteger, userPublicKey: Element): BigInteger {
        val k = withdrawUserRandomness[userPublicKey] ?: return BigInteger.ZERO
        withdrawUserRandomness.remove(userPublicKey)
        // <Subtract balance here>
        return Schnorr.signBlindedChallenge(k, challenge, privateKey)
    }

    // TODO Make this a spend transaction
    fun depositEuro(euro: DigitalEuro): String {

        for (depositedEuro in depositedEuros) {
            if (depositedEuro.signature == euro.signature){
                // Loop over the proofs to find the double spending
                val depositedEuroProofs = depositedEuro.proofs
                val euroProofs = euro.proofs

                for (i in 0 until min(depositedEuroProofs.size, euroProofs.size)) {
                    val currentDepositProof = depositedEuroProofs[i]
                    val currentProof = euroProofs[i]

                    if (currentDepositProof == currentProof)
                        continue

                    val doubleSpendingPK = CentralAuthority.getUserFromProofs(Pair(currentProof, currentDepositProof))

                    return if (doubleSpendingPK != null) {
                        "Double spending detected. PK of the perpetrator: $doubleSpendingPK"
                    } else {
                        "Detected double spending but could not blame anyone"
                    }
                }

                return "Detected double spending by finding the exact two same tokens, the depositor tries to double spend"

            }

        }

        depositedEuros.add(euro)
        return "Deposit was successful!"

    }

    fun getDepositedTokens(): List<DigitalEuro> {
        return depositedEuros
    }
}
