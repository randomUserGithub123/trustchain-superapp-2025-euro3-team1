package nl.tudelft.trustchain.offlineeuro.entity

import android.content.Context
import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.communication.ICommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.Schnorr
import nl.tudelft.trustchain.offlineeuro.cryptography.CRS
import nl.tudelft.trustchain.offlineeuro.db.WalletManager
import java.util.UUID
import kotlin.concurrent.thread

class User(
    name: String,
    group: BilinearGroup,
    context: Context?,
    private var walletManager: WalletManager? = null,
    runSetup: Boolean = true,
    onDataChangeCallback: ((String?) -> Unit)? = null
) : Participant(name, onDataChangeCallback) {

    private var wallet: Wallet? = null

    init {
        this.group = group

//        if (runSetup) {
//            initializeUser()
//        } else {
//            // This is for cases where you load a user that is already set up
//            generateKeyPair()
//        }

        if (walletManager == null) {
            walletManager = WalletManager(context, group)
        }
    }

    fun runUserSetup() {
        // The body is the same as your old initializeUser()
        thread {
            try {
                onDataChangeCallback?.invoke("Setting up User: Getting system parameters...")
                communicationProtocol.getGroupDescriptionAndCRS()

                onDataChangeCallback?.invoke("Setting up User: Creating keys...")
                generateKeyPair()

                onDataChangeCallback?.invoke("Setting up User: Registering with TTP...")
                communicationProtocol.register(name, publicKey, "TTP")

                onDataChangeCallback?.invoke("User setup complete!")
            } catch (e: Exception) {
                onDataChangeCallback?.invoke("User setup failed: ${e.message}")
            }
        }
    }

    fun sendDigitalEuroTo(nameReceiver: String): String {

        // walletManager = walletManager ?: WalletManager(
        //     context ?: throw IllegalStateException("Context is null"),
        //     group
        // )
        // val wallet = Wallet(privateKey, publicKey, walletManager!!)

        val currentWallet = wallet ?: Wallet(privateKey, publicKey, walletManager!!).also { wallet = it }

        val randomizationElements = communicationProtocol.requestTransactionRandomness(nameReceiver, group)
        val transactionDetails =
            currentWallet.spendEuro(randomizationElements, group, crs)
                ?: throw Exception("No euro to spend")

        val result = communicationProtocol.sendTransactionDetails(nameReceiver, transactionDetails)
        onDataChangeCallback?.invoke(result)
        return result
    }

    fun doubleSpendDigitalEuroTo(nameReceiver: String): String {

        // walletManager = walletManager ?: WalletManager(
        //     context ?: throw IllegalStateException("Context is null"),
        //     group
        // )
        // val wallet = Wallet(privateKey, publicKey, walletManager!!)

        val currentWallet = wallet ?: Wallet(privateKey, publicKey, walletManager!!).also { wallet = it }

        val randomizationElements = communicationProtocol.requestTransactionRandomness(nameReceiver, group)
        val transactionDetails = currentWallet.doubleSpendEuro(randomizationElements, group, crs)
        val result = communicationProtocol.sendTransactionDetails(nameReceiver, transactionDetails!!)
        onDataChangeCallback?.invoke(result)
        return result
    }

    fun withdrawDigitalEuro(bank: String): DigitalEuro {

        // walletManager = walletManager ?: WalletManager(
        //     context ?: throw IllegalStateException("Context is null"),
        //     group
        // )
        // val wallet = Wallet(privateKey, publicKey, walletManager!!)

        val currentWallet = wallet ?: Wallet(privateKey, publicKey, walletManager!!).also { wallet = it }

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
        currentWallet.addToWallet(digitalEuro, firstT)
        onDataChangeCallback?.invoke("Withdrawn ${digitalEuro.serialNumber} successfully!")
        return digitalEuro
    }

    fun getBalance(): Int {
        // walletManager = walletManager ?: WalletManager(
        //     context ?: throw IllegalStateException("Context is null"),
        //     group
        // )
        return walletManager!!.getWalletEntriesToSpend().count()
    }

    override fun onReceivedTransaction(
        transactionDetails: TransactionDetails,
        publicKeyBank: Element,
        publicKeySender: Element
    ): String {

        // walletManager = walletManager ?: WalletManager(
        //     context ?: throw IllegalStateException("Context is null"),
        //     group
        // )
        // val wallet = Wallet(privateKey, publicKey, walletManager!!)

        val currentWallet = wallet ?: Wallet(privateKey, publicKey, walletManager!!).also { wallet = it }

        val usedRandomness = lookUpRandomness(publicKeySender) ?: return "Randomness Not found!"
        removeRandomness(publicKeySender)
        val transactionResult = Transaction.validate(transactionDetails, publicKeyBank, group, crs)

        if (transactionResult.valid) {
            currentWallet.addToWallet(transactionDetails, usedRandomness)
            onDataChangeCallback?.invoke("Received an euro from $publicKeySender")
            return transactionResult.description
        }
        onDataChangeCallback?.invoke(transactionResult.description)
        return transactionResult.description
    }

    override fun reset() {
        // walletManager = walletManager ?: WalletManager(
        //     context ?: throw IllegalStateException("Context is null"),
        //     group
        // )
        randomizationElementMap.clear()
        walletManager!!.clearWalletEntries()
        runUserSetup()
    }
}
