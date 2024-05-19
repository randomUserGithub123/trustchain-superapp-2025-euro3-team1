package nl.tudelft.trustchain.offlineeuro.community.message

import nl.tudelft.ipv8.Peer

class BilinearGroupCRSRequestMessage (
    val requestingPeer: Peer
): ICommunityMessage {
    override val messageType = CommunityMessageType.GroupDescriptionCRSRequestMessage
}
