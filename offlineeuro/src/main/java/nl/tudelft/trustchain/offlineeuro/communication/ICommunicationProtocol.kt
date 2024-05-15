package nl.tudelft.trustchain.offlineeuro.communication

import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.CRS
import nl.tudelft.trustchain.offlineeuro.cryptography.RandomizationElements
import nl.tudelft.trustchain.offlineeuro.entity.TransactionDetails
import java.math.BigInteger

interface ICommunicationProtocol {

    fun getGroupDescriptionAndCRS(): Pair<BilinearGroup, CRS>
    fun register(userName: String, publicKey: Element, nameTTP: String)
    fun getBlindSignatureRandomness(publicKey:Element, bankName: String, group: BilinearGroup) : Element
    fun requestBlindSignature(publicKey: Element, bankName: String, challenge: BigInteger) : BigInteger
    fun requestTransactionRandomness(userNameReceiver: String, group: BilinearGroup): RandomizationElements
    fun sendTransactionDetails(userNameReceiver: String, transactionDetails: TransactionDetails): String
    fun getPublicKeyOf(name: String, group: BilinearGroup): Element
}
