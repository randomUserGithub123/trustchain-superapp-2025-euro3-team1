package nl.tudelft.trustchain.offlineeuro.entity

import android.content.Context
import android.util.Log
import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.communication.ICommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.BloomFilter
import nl.tudelft.trustchain.offlineeuro.cryptography.Schnorr
import nl.tudelft.trustchain.offlineeuro.db.WalletManager
import java.util.UUID

class User(
    name: String,
    group: BilinearGroup,
    context: Context?,
    private var walletManager: WalletManager? = null,
    communicationProtocol: ICommunicationProtocol,
    runSetup: Boolean = true,
    onDataChangeCallback: ((String?) -> Unit)? = null
) : Participant(communicationProtocol, name, onDataChangeCallback) {
    val wallet: Wallet
    private val bloomFilter: BloomFilter = BloomFilter(1000)

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
        Log.d("User_sendDigitalEuroTo", "Initiating payment from '$name' to '$nameReceiver'.")

        val randomizationElements = communicationProtocol.requestTransactionRandomness(nameReceiver, group)

        Log.d("User_sendDigitalEuroTo", "Creating transaction details to send.")
        val transactionDetails =
            wallet.spendEuro(randomizationElements, group, crs)
                ?: throw Exception("No euro to spend")

        this.bloomFilter.add(transactionDetails.digitalEuro)

        Log.d("User_sendDigitalEuroTo", "Local Bloom Filter updated with spent token. Cardinality: ${bloomFilter.getBitSet().cardinality()}")

        val result = communicationProtocol.sendTransactionDetails(nameReceiver, transactionDetails)
        Log.i("User_sendDigitalEuroTo", "Transaction details sent. Receiver responded with: '$result'")


        if (result != "Valid transaction") {
            onDataChangeCallback?.invoke("Transaction failed: $result")
            Log.e("User_sendDigitalEuroTo", "Transaction was rejected by receiver. Aborting filter sync.")
            return result
        }

        // exchange bloom filter to make sure both users bloom filter is up-to-date
        try {
            onDataChangeCallback?.invoke("Transaction successful. Starting filter sync...")
            Log.i("User_sendDigitalEuroTo", "Starting two-way Bloom Filter synchronization with '$nameReceiver'.")

            // Here we send our filter and returns the receiver's merged bloom filter.
            Log.d("User_sendDigitalEuroTo", "Calling exchangeBloomFilters. Sending my filter (cardinality: ${getBloomFilter().getBitSet().cardinality()})...")
            val receiversFilter = communicationProtocol.exchangeBloomFilters(nameReceiver, getBloomFilter())
            Log.d("User_sendDigitalEuroTo", "Received filter from '$nameReceiver'. Cardinality: ${receiversFilter.getBitSet().cardinality()}")


            // B) We then perform the final merge.
            Log.d("User_sendDigitalEuroTo", "Merging receiver's filter into local filter.")
            updateBloomFilter(receiversFilter)
            Log.i("User_sendDigitalEuroTo", "Sync complete. Final local filter cardinality: ${getBloomFilter().getBitSet().cardinality()}")

            onDataChangeCallback?.invoke("Sync complete. Both parties are up to date.")

        } catch (e: Exception) {
            Log.e("User_sendDigitalEuroTo", "Filter synchronization failed after transaction.", e)
            onDataChangeCallback?.invoke("Transaction sent, but filter sync failed: ${e.message}")
        }

        return result
    }

    fun depositDigitalEuro(bankName: String): String {
        Log.d("User_depositDigitalEuro", "Initiating deposit from '$name' to bank '$bankName'.")

        val randomizationElements = communicationProtocol.requestTransactionRandomness(bankName, group)

        Log.d("User_depositDigitalEuro", "Creating transaction details to deposit.")
        val transactionDetails =
            wallet.spendEuro(randomizationElements, group, crs)
                ?: throw Exception("No euro to deposit")

        // Send the transaction to the bank and get the result
        val result = communicationProtocol.sendTransactionDetails(bankName, transactionDetails)
        Log.i("User_depositDigitalEuro", "Transaction details sent to bank. Bank responded with: '$result'")


        if (result == "Deposit was successful!") {
            // If the transaction was successful, invoke the callback with a success message.
            onDataChangeCallback?.invoke("Deposited coin ${transactionDetails.digitalEuro.serialNumber} successfully!")
        } else {
            // If the transaction failed, the bank rejected it. Report the failure.
            onDataChangeCallback?.invoke("Deposit failed: $result")
            Log.e("User_depositDigitalEuro", "Deposit was rejected by the bank: $result")
        }
        return result
    }

    fun doubleSpendDigitalEuroTo(nameReceiver: String): String {
        val randomizationElements = communicationProtocol.requestTransactionRandomness(nameReceiver, group)
        val transactionDetails = wallet.doubleSpendEuro(randomizationElements, group, crs)
        val result = communicationProtocol.sendTransactionDetails(nameReceiver, transactionDetails!!)
        communicationProtocol.sendBloomFilter(nameReceiver, getBloomFilter())
        onDataChangeCallback?.invoke(result)
        return result
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
        onDataChangeCallback?.invoke("Withdrawn ${digitalEuro.serialNumber} successfully!")

        // disabling bloom filter transaction
//        try {
//            val bankBloomFilter = communicationProtocol.requestBloomFilter(bank)
//            updateBloomFilter(bankBloomFilter) // Update your own bloom filter with the bank's
//            communicationProtocol.sendBloomFilter(bank, getBloomFilter())
//            onDataChangeCallback?.invoke("Received bank's bloom filter and updated local filter!")
//        } catch (e: Exception) {
//            onDataChangeCallback?.invoke("Withdrawn, but failed to request bank's bloom filter: ${e.message}")
//        }

        return digitalEuro
    }

    fun getBalance(): Int {
        return walletManager!!.getWalletEntriesToSpend().count()
    }

    override fun onReceivedTransaction(
        transactionDetails: TransactionDetails,
        publicKeyBank: Element,
        publicKeySender: Element
    ): String {
        if (this.bloomFilter.mightContain(transactionDetails.digitalEuro)) {
            val warningMessage = "Note: Token seen before. Potential for double spending..."
            Log.w("User_onReceived", "Bloom filter match for token: ${transactionDetails.digitalEuro.serialNumber}. This is not an error, but requires validation.")
            onDataChangeCallback?.invoke(warningMessage)
        }

        val usedRandomness = lookUpRandomness(publicKeySender) ?: return "Randomness Not found!"
        removeRandomness(publicKeySender)
        val transactionResult = Transaction.validate(transactionDetails, publicKeyBank, group, crs)

        if (transactionResult.valid) {
            wallet.addToWallet(transactionDetails, usedRandomness)

            this.bloomFilter.add(transactionDetails.digitalEuro)

            onDataChangeCallback?.invoke("Received an euro from $publicKeySender")
            return transactionResult.description
        }
        onDataChangeCallback?.invoke(transactionResult.description)
        return transactionResult.description
    }

    override fun reset() {
        randomizationElementMap.clear()
        walletManager!!.clearWalletEntries()
        setUp()
    }

    override fun getBloomFilter(): BloomFilter {
        return bloomFilter
    }

    override fun updateBloomFilter(receivedBF: BloomFilter) {
        // Prepare M (Set of All Received Monies) specific to User
        val myReceivedMonies =
            wallet.getAllWalletEntriesToSpend().map {
                it.digitalEuro
            }

        // Call the centralized Algorithm 2 logic in the BloomFilter class
        val updateMessage = this.bloomFilter.applyAlgorithm2Update(receivedBF, myReceivedMonies)
        onDataChangeCallback?.invoke(updateMessage) // Use the message for UI/logging
    }
}
