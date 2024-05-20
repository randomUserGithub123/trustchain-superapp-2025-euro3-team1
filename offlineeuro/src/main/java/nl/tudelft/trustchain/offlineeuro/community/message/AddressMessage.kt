package nl.tudelft.trustchain.offlineeuro.community.message

import nl.tudelft.trustchain.offlineeuro.enums.Role

class AddressMessage (
    val name: String,
    val role: Role,
    val publicKeyBytes: ByteArray,
    val peerPublicKey: ByteArray,
): ICommunityMessage {
    override val messageType = CommunityMessageType.AddressMessage
}
