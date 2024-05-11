package nl.tudelft.trustchain.offlineeuro.entity

import android.content.Context
import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.communication.ICommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.CRS
import nl.tudelft.trustchain.offlineeuro.cryptography.RandomizationElements
import nl.tudelft.trustchain.offlineeuro.cryptography.Schnorr
import nl.tudelft.trustchain.offlineeuro.db.WalletManager
import java.util.UUID

enum class CommunicationState {
    INPROGRESS,
    COMPLETE,
    FAILED
}

class User (
    var name: String,
    context: Context?,
    walletManager: WalletManager = WalletManager(context, CentralAuthority.groupDescription),
    private val communicationProtocol: ICommunicationProtocol
)
{
    private val privateKey: Element
    val publicKey: Element
    val wallet: Wallet
    val group: BilinearGroup
    var crs: CRS

    init {
        CentralAuthority.initializeRegisteredUserManager(context)
        group = CentralAuthority.groupDescription
        privateKey = group.getRandomZr()
        publicKey = group.g.powZn(privateKey)
        CentralAuthority.registerUser(name, publicKey)
        crs = CentralAuthority.crs
        wallet = Wallet(privateKey, publicKey, walletManager)
    }

    // TODO Cleaner solution for this
    var communicationState: CommunicationState = CommunicationState.COMPLETE

    fun registerWithBank(bankName: String, community: OfflineEuroCommunity, userName: String): Boolean {
        //TODO
        return false
    }

    fun onTransactionRequest(randomizationElements: RandomizationElements): TransactionDetails? {
        return wallet.spendEuro(randomizationElements)
    }

    fun sendTokenToRandomPeer(community: OfflineEuroCommunity, keepToken: Boolean = false): Boolean {
        //TODO
        return false
    }

    fun withdrawDigitalEuro(bank: Bank): DigitalEuro {
        val serialNumber = UUID.randomUUID().toString()
        val firstT = group.getRandomZr()
        val tInv = firstT.mul(-1)
        val initialTheta = group.g.powZn(tInv).immutable

        val bytesToSign = serialNumber.toByteArray() + initialTheta.toBytes()

        val bankRandomness = communicationProtocol.getBlindSignatureRandomness("Test")

        val bankRandomness2 = bank.getBlindSignatureRandomness(publicKey)

        val blindedChallenge = Schnorr.createBlindedChallenge(bankRandomness, bytesToSign, bank.publicKey, group)
        val blindSignature = bank.createBlindSignature(blindedChallenge.blindedChallenge, publicKey)
        val signature = Schnorr.unblindSignature(blindedChallenge, blindSignature)
        val digitalEuro = DigitalEuro(serialNumber, initialTheta, signature, arrayListOf())
        wallet.addToWallet(digitalEuro, firstT)
        return digitalEuro
    }

    fun depositEuro(bank: Bank): String {
        return bank.requestDeposit(this)
    }
}
