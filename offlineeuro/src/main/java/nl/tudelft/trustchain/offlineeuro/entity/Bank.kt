package nl.tudelft.trustchain.offlineeuro.entity

import android.content.Context
import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.communication.ICommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.Schnorr
import nl.tudelft.trustchain.offlineeuro.db.DepositedEuroManager
import java.math.BigInteger
import kotlin.math.min

class Bank(
    name: String,
    group: BilinearGroup,
    communicationProtocol: ICommunicationProtocol,
    private val context: Context?,
    private val depositedEuroManager: DepositedEuroManager = DepositedEuroManager(context, group),
    runSetup: Boolean = true
) : Participant(communicationProtocol, name) {
    private val depositedEuros: ArrayList<DigitalEuro> = arrayListOf()
    val withdrawUserRandomness: HashMap<Element, Element> = hashMapOf()

    init {
        communicationProtocol.participant = this
        this.group = group
        if (runSetup) {
            setUp()
        }
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

    fun createBlindSignature(
        challenge: BigInteger,
        userPublicKey: Element
    ): BigInteger {
        val k =
            lookUp(userPublicKey)
                ?: return BigInteger.ZERO
        remove(userPublicKey)
        // <Subtract balance here>
        return Schnorr.signBlindedChallenge(k, challenge, privateKey)
    }

    private fun lookUp(userPublicKey: Element): Element? {
        for (element in withdrawUserRandomness.entries) {
            val key = element.key

            if (key == userPublicKey) {
                return element.value
            }
        }

        return null
    }

    private fun remove(userPublicKey: Element): Element? {
        for (element in withdrawUserRandomness.entries) {
            val key = element.key

            if (key == userPublicKey) {
                return withdrawUserRandomness.remove(key)
            }
        }

        return null
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
                if (euroProofs[i] == duplicateEuroProofs[i]) {
                    continue
                } else if (i > maxFirstDifferenceIndex) {
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
                return "Double spending detected. Double spender is ${doubleSpender.name} with PK: ${doubleSpender.publicKey}"
            }
        }

        // <Increase user balance here>
        depositedEuroManager.insertDigitalEuro(euro)
        return "Detected double spending but could not blame anyone"
    }

    fun getDepositedTokens(): List<DigitalEuro> {
        return depositedEuros
    }

    override fun onReceivedTransaction(
        transactionDetails: TransactionDetails,
        publicKeyBank: Element,
        publicKeySender: Element
    ): String {
        val isValid = Transaction.validate(transactionDetails, publicKeyBank, group, crs)
        if (isValid) {
            return depositEuro(transactionDetails.digitalEuro)
        }

        return "Invalid Transaction"
    }
}
