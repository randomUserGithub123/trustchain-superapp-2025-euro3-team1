package nl.tudelft.trustchain.offlineeuro.communication

import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
import nl.tudelft.trustchain.offlineeuro.community.message.BilinearGroupCRSMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureRandomnessReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.CommunityMessageType
import nl.tudelft.trustchain.offlineeuro.community.message.ICommunityMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionRandomizationElementsMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionResultMessage
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.CRS
import nl.tudelft.trustchain.offlineeuro.cryptography.PairingTypes
import nl.tudelft.trustchain.offlineeuro.cryptography.RandomizationElements
import nl.tudelft.trustchain.offlineeuro.db.AddressBookManager
import nl.tudelft.trustchain.offlineeuro.entity.TransactionDetails
import java.math.BigInteger

class IPV8CommunicationProtocol(
    val addressBookManager: AddressBookManager,
    val community: OfflineEuroCommunity
) : ICommunicationProtocol {

    private val SLEEP_DURATION: Long = 100
    private val MAX_WAIT_DURATION_MS = 5000
    override fun getGroupDescriptionAndCRS(): Pair<BilinearGroup, CRS> {
        community.getGroupDescriptionAndCRS()
        val message =
            waitForMessage(CommunityMessageType.GroupDescriptionCRS) as BilinearGroupCRSMessage

        val group = BilinearGroup(pairingType = PairingTypes.FromFile)
        group.updateGroupElements(message.groupDescription)
        val crs = message.crs.toCRS(group)
        return Pair(group, crs)
    }

    override fun register(userName: String, publicKey: Element, nameTTP: String) {
        val ttpAddress = addressBookManager.getAddressByName(nameTTP)
        community.registerAtTTP(userName, publicKey.toBytes(), ttpAddress.peerPublicKey!!)
    }

    override fun getBlindSignatureRandomness(
        publicKey: Element,
        bankName: String,
        group: BilinearGroup
    ): Element {
        val bankAddress = addressBookManager.getAddressByName(bankName)
        community.getBlindSignatureRandomness(publicKey.toBytes(), bankAddress.peerPublicKey!!)

        val replyMessage =
            waitForMessage(CommunityMessageType.BlindSignatureRandomnessReplyMessage) as BlindSignatureRandomnessReplyMessage
        return group.gElementFromBytes(replyMessage.randomnessBytes)
    }

    override fun requestBlindSignature(
        publicKey: Element,
        bankName: String,
        challenge: BigInteger
    ): BigInteger {
        val bankAddress = addressBookManager.getAddressByName(bankName)
        community.getBlindSignature(challenge, publicKey.toBytes(), bankAddress.peerPublicKey!!)

        val replyMessage = waitForMessage(CommunityMessageType.BlindSignatureReplyMessage) as BlindSignatureReplyMessage
        return replyMessage.signature
    }

    override fun requestTransactionRandomness(userNameReceiver: String, group: BilinearGroup): RandomizationElements {
        val peerAddress = addressBookManager.getAddressByName(userNameReceiver)
        community.getTransactionRandomizationElements(peerAddress.peerPublicKey!!)
        val message = waitForMessage(CommunityMessageType.TransactionRandomnessMessage) as TransactionRandomizationElementsMessage
        return message.randomizationElementsBytes.toRandomizationElements(group)
    }

    override fun sendTransactionDetails(
        userNameReceiver: String,
        transactionDetails: TransactionDetails
    ) : String {
        val peerAddress = addressBookManager.getAddressByName(userNameReceiver)
        community.sendTransactionDetails(peerAddress.peerPublicKey!!, transactionDetails)
        val message = waitForMessage(CommunityMessageType.TransactionResultMessage) as TransactionResultMessage
        return message.result
    }

    override fun getPublicKeyOf(name: String, group: BilinearGroup): Element {
        return addressBookManager.getAddressByName(name).publicKey
    }

    private fun waitForMessage(messageType: CommunityMessageType): ICommunityMessage {

        var loops = 0

        while (!community.messageList.any { it.messageType == messageType }) {
            if (loops * SLEEP_DURATION >= MAX_WAIT_DURATION_MS) {
                throw Exception("TimeOut")
            }
            Thread.sleep(SLEEP_DURATION)
            loops++
        }

        val message =
            community.messageList.first { it.messageType == messageType }
        community.messageList.remove(message)

        return message
    }
}
