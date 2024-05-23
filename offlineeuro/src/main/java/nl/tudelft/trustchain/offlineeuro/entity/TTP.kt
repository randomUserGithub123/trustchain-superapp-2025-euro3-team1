package nl.tudelft.trustchain.offlineeuro.entity

import android.content.Context
import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.communication.ICommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.CRSGenerator
import nl.tudelft.trustchain.offlineeuro.db.RegisteredUserManager

class TTP(
    name: String = "TTP",
    communicationProtocol: ICommunicationProtocol,
    context: Context?,
    group: BilinearGroup
) : Participant(communicationProtocol, name) {
    private val registeredUserManager: RegisteredUserManager
    private val crsMap: Map<Element, Element>

    init {
        communicationProtocol.participant = this
        this.group = group
        val generatedCRS = CRSGenerator.generateCRSMap(group)
        this.crs = generatedCRS.first
        this.crsMap = generatedCRS.second
        registeredUserManager = RegisteredUserManager(context, group)
        generateKeyPair()
    }

    fun registerUser(
        name: String,
        publicKey: Element
    ): Boolean {
        return registeredUserManager.addRegisteredUser(name, publicKey)
    }

    fun getRegisteredUsers(): List<RegisteredUser> {
        return registeredUserManager.getAllRegisteredUsers()
    }

    override fun onReceivedTransaction(
        transactionDetails: TransactionDetails,
        publicKeyBank: Element,
        publicKeySender: Element
    ): String {
        TODO("Not yet implemented")
    }
}
