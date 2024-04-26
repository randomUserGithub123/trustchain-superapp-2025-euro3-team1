package nl.tudelft.trustchain.offlineeuro.entity

import android.content.Context
import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahai
import nl.tudelft.trustchain.offlineeuro.cryptography.Schnorr
import nl.tudelft.trustchain.offlineeuro.db.DepositedEuroManager
import java.math.BigInteger
import kotlin.math.min
import kotlin.system.measureTimeMillis

class Bank (
    val name: String = "BestBank",
    private val context: Context?,
    private val depositedEuroManager: DepositedEuroManager = DepositedEuroManager(context, CentralAuthority.groupDescription)
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

    fun requestDeposit(user: User): String {

        var depositResult = ""
        val depositTime = measureTimeMillis {

        val t = group.getRandomZr()
        val randomizationElements = GrothSahai.tToRandomizationElements(t)
        val transactionDetails = user.onTransactionRequest(randomizationElements)
        Transaction.validate(transactionDetails!!, publicKey)
        transactionDetails.digitalEuro.proofs.add(transactionDetails.currentTransactionProof.grothSahaiProof)
         depositResult = depositEuro(transactionDetails.digitalEuro)
        }

        print("Time to verify deposit: $depositTime")

        return depositResult
    }

    private fun depositEuro(euro: DigitalEuro): String {

        val duplicateEuros = depositedEuroManager.getDigitalEurosByDescriptor(euro)

        if (duplicateEuros.isEmpty()) {
            depositedEuroManager.insertDigitalEuro(euro)
            return "Deposit was successful!"
        }

        var maxFirstDifferenceIndex = -1
        var doubleSpendEuro: DigitalEuro? = null
        for (duplicateEuro in duplicateEuros) {
            // Loop over the proofs to find the double spending
            val euroProofs = euro.proofs
            val duplicateEuroProofs = duplicateEuro.proofs


            for (i in 0 until min(euroProofs.size, duplicateEuroProofs.size)) {
                if (euroProofs[i] == duplicateEuroProofs[i])
                    continue
                else if (i > maxFirstDifferenceIndex) {
                    maxFirstDifferenceIndex = i
                    doubleSpendEuro = duplicateEuro
                    break
                }
            }
        }

        if (doubleSpendEuro != null) {
            val euroProof = euro.proofs[maxFirstDifferenceIndex]
            val depositProof = doubleSpendEuro.proofs[maxFirstDifferenceIndex]
            val doubleSpender = CentralAuthority.getUserFromProofs(Pair(euroProof, depositProof))

            if (doubleSpender != null) {
                // <Increase user balance here>
                depositedEuroManager.insertDigitalEuro(euro)
               return  "Double spending detected. Double spender is ${doubleSpender.name} with PK: ${doubleSpender.publicKey}"
            }
        }

        // <Increase user balance here>
        depositedEuroManager.insertDigitalEuro(euro)
        return "Detected double spending but could not blame anyone"

    }

    fun getDepositedTokens(): List<DigitalEuro> {
        return depositedEuros
    }
}
