package nl.tudelft.trustchain.offlineeuro.entity

import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.cryptography.RandomizationElements
import nl.tudelft.trustchain.offlineeuro.cryptography.SchnorrSignature
import nl.tudelft.trustchain.offlineeuro.db.WalletManager

data class WalletEntry(
    val digitalEuro: DigitalEuro,
    val t: Element,
    val transactionSignature: SchnorrSignature?,
    val timesSpent: Long = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as WalletEntry
        return this.digitalEuro == other.digitalEuro &&
            this.t == other.t &&
            this.transactionSignature == other.transactionSignature &&
            this.timesSpent == other.timesSpent
    }
}

class Wallet(
    private val privateKey: Element,
    val publicKey: Element,
    private val walletManager: WalletManager
) {
    fun addToWallet(
        transactionDetails: TransactionDetails,
        t: Element
    ) {
        val digitalEuro = transactionDetails.digitalEuro
        digitalEuro.proofs.add(transactionDetails.currentTransactionProof.grothSahaiProof)

        val transactionSignature = transactionDetails.theta1Signature
        val walletEntry = WalletEntry(digitalEuro, t, transactionSignature)
        walletManager.insertWalletEntry(walletEntry)
    }

    fun addToWallet(
        digitalEuro: DigitalEuro,
        t: Element
    ) {
        walletManager.insertWalletEntry(WalletEntry(digitalEuro, t, null))
    }

    fun getWalletEntryToSpend(): WalletEntry? {
        return walletManager.getNumberOfWalletEntriesToSpend(1).firstOrNull()
    }

    fun getAllWalletEntriesToSpend(): List<WalletEntry> {
        return walletManager.getWalletEntriesToSpend()
    }

    fun spendEuro(randomizationElements: RandomizationElements): TransactionDetails? {
        val walletEntry = walletManager.getNumberOfWalletEntriesToSpend(1).firstOrNull() ?: return null
        val euro = walletEntry.digitalEuro
        walletManager.incrementTimesSpent(euro)
        return Transaction.createTransaction(privateKey, publicKey, walletEntry, randomizationElements)
    }

    fun doubleSpendEuro(randomizationElements: RandomizationElements): TransactionDetails? {
        val walletEntry = walletManager.getNumberOfWalletEntriesToDoubleSpend(1).firstOrNull() ?: return null
        val euro = walletEntry.digitalEuro
        walletManager.incrementTimesSpent(euro)

        return Transaction.createTransaction(privateKey, publicKey, walletEntry, randomizationElements)
    }
}
