package nl.tudelft.trustchain.offlineeuro.entity

import it.unisa.dia.gas.jpbc.Element

data class WalletEntry(val digitalEuro: DigitalEuro, val t: Element?)

class Wallet(
    val privateKey: Element,
    val publicKey: Element,
    val euros: ArrayList<WalletEntry> = arrayListOf()
) {

    fun addToWallet(transactionDetails: TransactionDetails, t: Element){
        val digitalEuro = transactionDetails.digitalEuro
        digitalEuro.proofs.add(transactionDetails.currentProofs.first)
        euros.add(WalletEntry(digitalEuro, t))
    }

    fun addToWallet(digitalEuro: DigitalEuro, t: Element?){
        euros.add(WalletEntry(digitalEuro, t))
    }


    fun spendEuro(randomizationElements: RandomizationElements): TransactionDetails? {
        if (euros.isEmpty()) {
            return null
        }
        val euroToSpend = euros.removeAt(0)

        return Transaction.createTransaction(privateKey, publicKey, euroToSpend, randomizationElements)
    }
}
