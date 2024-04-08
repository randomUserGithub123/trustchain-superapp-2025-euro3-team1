package nl.tudelft.trustchain.offlineeuro.entity

import android.content.Context
import nl.tudelft.trustchain.offlineeuro.libraries.Cryptography
import java.math.BigInteger
import kotlin.math.min

class Bank (
    val name: String = "BestBank",
    private val context: Context?,
){
    val depositedEuros: ArrayList<DigitalEuro> = arrayListOf()
    private var rsaParameters: RSAParameters = Cryptography.generateRSAParameters(512)

    fun getPublicRSAValues(): Pair<BigInteger, BigInteger> {
        return Pair(rsaParameters.e, rsaParameters.n)
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
