package nl.tudelft.trustchain.offlineeuro.community.message

class RegistrationMessage(
    val name: String,
    val publicKeyBytes: ByteArray,
) : ICommunityMessage {
    override val messageType = CommunityMessageType.RegistrationMessage
}
