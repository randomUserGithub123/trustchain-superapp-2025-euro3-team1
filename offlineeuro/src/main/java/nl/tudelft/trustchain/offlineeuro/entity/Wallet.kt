package nl.tudelft.trustchain.offlineeuro.entity

import it.unisa.dia.gas.jpbc.Element

data class WalletEntry(val digitalEuro: DigitalEuro, val t: Element?)

class Wallet(
    val privateKey: Element,
    val publicKey: Element,
    val euros: ArrayList<WalletEntry> = arrayListOf(),
    val spentEuros: ArrayList<WalletEntry> = arrayListOf()
) {

    fun addToWallet(transactionDetails: TransactionDetails, t: Element){
        val digitalEuro = transactionDetails.digitalEuro
        digitalEuro.proofs.add(transactionDetails.currentProofs.first.grothSahaiProof)
        euros.add(WalletEntry(digitalEuro, t))
    }

    fun addToWallet(digitalEuro: DigitalEuro, t: Element?) {
        euros.add(WalletEntry(digitalEuro, t))
    }

    fun spendEuro(randomizationElements: RandomizationElements): TransactionDetails? {
        if (euros.isEmpty()) {
            return null
        }
        val euroToSpend = euros.removeAt(0)
        val copiedProofs = arrayListOf<GrothSahaiProof>()
        copiedProofs.addAll(euroToSpend.digitalEuro.proofs)
        val copiedEuro = DigitalEuro(copiedProofs, euroToSpend.digitalEuro.signature)

        spentEuros.add(WalletEntry(copiedEuro, euroToSpend.t))
        return Transaction.createTransaction(privateKey, publicKey, euroToSpend, randomizationElements)
    }

    fun doubleSpendEuro(randomizationElements: RandomizationElements): TransactionDetails? {
        if (spentEuros.isEmpty()) {
            return null
        }
        val euroToSpend = spentEuros.removeAt(0)

        return Transaction.createTransaction(privateKey, publicKey, euroToSpend, randomizationElements)
    }

    fun depositEuro(bank: Bank): String {
        if (euros.isEmpty()) {
            return "No Euro to deposit"
        }
        val euroToDeposit = euros.removeAt(0)

        return bank.depositEuro(euroToDeposit.digitalEuro)
    }
}
