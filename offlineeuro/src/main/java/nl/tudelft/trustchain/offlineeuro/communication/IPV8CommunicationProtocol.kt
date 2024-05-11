package nl.tudelft.trustchain.offlineeuro.communication

import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
import nl.tudelft.trustchain.offlineeuro.community.message.BilinearGroupCRSMessage
import nl.tudelft.trustchain.offlineeuro.community.message.CommunityMessageType
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.PairingTypes
import nl.tudelft.trustchain.offlineeuro.db.AddressBookManager

class IPV8CommunicationProtocol (
    val addressBookManager: AddressBookManager,
    val community: OfflineEuroCommunity?
) : ICommunicationProtocol {
    override fun getGroupDescriptionAndCRS(): BilinearGroup {
        community!!.getGroupDescriptionAndCRS()

        while (!community.messageList.any { it.messageType == CommunityMessageType.GroupDescriptionCRS }) {

        }

        val message = community.messageList.first { it.messageType == CommunityMessageType.GroupDescriptionCRS }
        community.messageList.remove(message)

        if (message is BilinearGroupCRSMessage) {
            val group = BilinearGroup(pairingType = PairingTypes.FromFile)
            group.updateGroupElements(message.groupDescription)
            return group
        }

        TODO("Not yet implemented")


    }

    override fun register(bankName: String) {
        TODO("Not yet implemented")
    }

    override fun getBlindSignatureRandomness(bankName: String): Element {
        val bankAddress = addressBookManager.getAddressByName(bankName)
        //community.getBlindSignatureRandomness()
        TODO("Not yet implemented")
    }

    override fun requestBlindSignature(bankName: String) {
        TODO("Not yet implemented")
    }
}
