package nl.tudelft.trustchain.offlineeuro.entity

import android.content.Context
import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.communication.ICommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.CRS
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahai
import nl.tudelft.trustchain.offlineeuro.cryptography.RandomizationElements
import nl.tudelft.trustchain.offlineeuro.cryptography.Schnorr
import nl.tudelft.trustchain.offlineeuro.db.WalletManager
import java.util.UUID

class User (
    var name: String,
    context: Context?,
    private var walletManager: WalletManager? = null,
    private val communicationProtocol: ICommunicationProtocol
)
{
    private val privateKey: Element
    val publicKey: Element
    val wallet: Wallet
    val group: BilinearGroup
    val randomizationElementMap: HashMap<Element, Element> = hashMapOf()
    private val crs: CRS


    init {
        val groupAndCRS = communicationProtocol.getGroupDescriptionAndCRS()
        group = groupAndCRS.first
        crs = groupAndCRS.second

        privateKey = group.getRandomZr()
        publicKey = group.g.powZn(privateKey)

        if (walletManager == null) {
            walletManager = WalletManager(context, group)
        }

        wallet = Wallet(privateKey, publicKey, walletManager!!)
        // TODO NAME OF TTP
        communicationProtocol.register(name, publicKey, "TTP")
    }

    fun generateRandomizationElements(receiverPublicKey: Element): RandomizationElements {
        val randomT = group.getRandomZr()
        randomizationElementMap[receiverPublicKey] = randomT
        return GrothSahai.tToRandomizationElements(randomT, group, crs)
    }
    fun sendDigitalEuroTo(nameReceiver: String): String {
        val randomizationElements = communicationProtocol.requestTransactionRandomness(nameReceiver, group)
        val transactionDetails = wallet.spendEuro(randomizationElements)
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

    fun onReceivedTransaction(transactionDetails: TransactionDetails,
                              publicKeyBank: Element,
                              publicKeySender: Element): String {
        val usedRandomness = randomizationElementMap[publicKeySender] ?: return "Randomness Not found!"

        val isValid = Transaction.validate(transactionDetails, publicKeyBank)

        if (isValid) {
            wallet.addToWallet(transactionDetails, usedRandomness)
            return "Successful transaction"
        }

        return "Invalid Transaction!"
    }

}
