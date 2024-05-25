package nl.tudelft.trustchain.offlineeuro.community.message

import nl.tudelft.ipv8.Peer

class AddressRequestMessage(
    val requestingPeer: Peer
) : ICommunityMessage {
    override val messageType = CommunityMessageType.AddressRequestMessage
}
