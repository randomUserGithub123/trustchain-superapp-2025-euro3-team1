package nl.tudelft.trustchain.offlineeuro.communication

import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup

interface ICommunicationProtocol {

    fun getGroupDescriptionAndCRS(): BilinearGroup
    fun register(bankName: String)
    fun getBlindSignatureRandomness(bankName: String) : Element
    fun requestBlindSignature(bankName: String)
}
