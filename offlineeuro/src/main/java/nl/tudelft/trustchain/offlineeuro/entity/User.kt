package nl.tudelft.trustchain.offlineeuro.entity

import android.content.Context
import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.communication.ICommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.Schnorr
import nl.tudelft.trustchain.offlineeuro.db.WalletManager
import java.util.UUID

class User(
    name: String,
    group: BilinearGroup,
    val context: Context?,
    private var walletManager: WalletManager? = null,
    communicationProtocol: ICommunicationProtocol,
    runSetup: Boolean = true
) : Participant(communicationProtocol, name) {
    val wallet: Wallet

    init {
        communicationProtocol.participant = this
        this.group = group

        if (runSetup) {
            setUp()
        } else {
            generateKeyPair()
        }
        if (walletManager == null) {
            walletManager = WalletManager(context, group)
        }

        wallet = Wallet(privateKey, publicKey, walletManager!!)
    }

    fun sendDigitalEuroTo(nameReceiver: String): String {
        val randomizationElements = communicationProtocol.requestTransactionRandomness(nameReceiver, group)
        val transactionDetails = wallet.spendEuro(randomizationElements, group, crs)
        return communicationProtocol.sendTransactionDetails(nameReceiver, transactionDetails!!)
    }

    fun doubleSpendDigitalEuroTo(nameReceiver: String): String {
        val randomizationElements = communicationProtocol.requestTransactionRandomness(nameReceiver, group)
        val transactionDetails = wallet.doubleSpendEuro(randomizationElements, group, crs)
        return communicationProtocol.sendTransactionDetails(nameReceiver, transactionDetails!!)
    }

    fun withdrawDigitalEuro(bank: String): DigitalEuro {
        val serialNumber = UUID.randomUUID().toString()
        val firstT = group.getRandomZr()
        val tInv = firstT.mul(-1)
        val initialTheta = group.g.powZn(tInv).immutable

        val bytesToSign = serialNumber.toByteArray() + initialTheta.toBytes()

        val bankRandomness = communicationProtocol.getBlindSignatureRandomness(publicKey, bank, group)
        val bankPublicKey = communicationProtocol.getPublicKeyOf(bank, group)

        val blindedChallenge = Schnorr.createBlindedChallenge(bankRandomness, bytesToSign, bankPublicKey, group)
        val blindSignature = communicationProtocol.requestBlindSignature(publicKey, bank, blindedChallenge.blindedChallenge)
        val signature = Schnorr.unblindSignature(blindedChallenge, blindSignature)
        val digitalEuro = DigitalEuro(serialNumber, initialTheta, signature, arrayListOf())
        wallet.addToWallet(digitalEuro, firstT)
        return digitalEuro
    }

    override fun onReceivedTransaction(
        transactionDetails: TransactionDetails,
        publicKeyBank: Element,
        publicKeySender: Element
    ): String {
        val usedRandomness = lookUpRandomness(publicKeySender) ?: return "Randomness Not found!"

        val isValid = Transaction.validate(transactionDetails, publicKeyBank, group, crs)

        if (isValid) {
            wallet.addToWallet(transactionDetails, usedRandomness)
            return "Successful transaction"
        }

        return "Invalid Transaction!"
    }
}
