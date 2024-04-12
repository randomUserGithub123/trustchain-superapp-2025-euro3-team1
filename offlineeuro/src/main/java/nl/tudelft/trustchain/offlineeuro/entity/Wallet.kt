package nl.tudelft.trustchain.offlineeuro.entity

import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahaiProof
import nl.tudelft.trustchain.offlineeuro.cryptography.RandomizationElements
import nl.tudelft.trustchain.offlineeuro.cryptography.SchnorrSignature

data class WalletEntry(val digitalEuro: DigitalEuro, val t: Element, val transactionSignature: SchnorrSignature?)

class Wallet(
    private val privateKey: Element,
    val publicKey: Element,
    val euros: ArrayList<WalletEntry> = arrayListOf(),
    val spentEuros: ArrayList<WalletEntry> = arrayListOf()
) {

    fun addToWallet(transactionDetails: TransactionDetails, t: Element){
        val digitalEuro = transactionDetails.digitalEuro
        digitalEuro.proofs.add(transactionDetails.currentTransactionProof.grothSahaiProof)

        val transactionSignature = transactionDetails.theta1Signature
        euros.add(WalletEntry(digitalEuro, t, transactionSignature))
    }

    fun addToWallet(digitalEuro: DigitalEuro, t: Element) {
        euros.add(WalletEntry(digitalEuro, t, null))
    }

    fun spendEuro(randomizationElements: RandomizationElements): TransactionDetails? {
        if (euros.isEmpty()) {
            return null
        }
        val euroToSpend = euros.removeAt(0)
        val copiedProofs = arrayListOf<GrothSahaiProof>()
        copiedProofs.addAll(euroToSpend.digitalEuro.proofs)
        val copiedEuro = DigitalEuro(euroToSpend.digitalEuro.serialNumber, euroToSpend.digitalEuro.firstTheta1.duplicate().immutable, euroToSpend.digitalEuro.signature , copiedProofs)

        spentEuros.add(WalletEntry(copiedEuro, euroToSpend.t, euroToSpend.transactionSignature))
        return Transaction.createTransaction(privateKey, publicKey, euroToSpend, randomizationElements, euroToSpend.transactionSignature)
    }

    fun doubleSpendEuro(randomizationElements: RandomizationElements): TransactionDetails? {
        if (spentEuros.isEmpty()) {
            return null
        }
        val euroToSpend = spentEuros.removeAt(0)

        return Transaction.createTransaction(privateKey, publicKey, euroToSpend, randomizationElements, euroToSpend.transactionSignature)
    }

    fun depositEuro(bank: Bank): String {
        if (euros.isEmpty()) {
            return "No Euro to deposit"
        }
        val euroToDeposit = euros.removeAt(0)

        return bank.depositEuro(euroToDeposit.digitalEuro)
    }
}
