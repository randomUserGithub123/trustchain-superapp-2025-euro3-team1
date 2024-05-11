package nl.tudelft.trustchain.offlineeuro.community.message

import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroupElementsBytes
import nl.tudelft.trustchain.offlineeuro.cryptography.CRSBytes

class BilinearGroupCRSMessage (
    val groupDescription: BilinearGroupElementsBytes,
    val crs: CRSBytes
): ICommunityMessage {
    override val messageType = CommunityMessageType.GroupDescriptionCRS
}
