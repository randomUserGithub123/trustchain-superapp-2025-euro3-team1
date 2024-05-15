package nl.tudelft.trustchain.offlineeuro.community.message

import nl.tudelft.trustchain.offlineeuro.cryptography.RandomizationElementsBytes

class TransactionRandomizationElementsMessage (
    val randomizationElementsBytes: RandomizationElementsBytes,
): ICommunityMessage {
    override val messageType = CommunityMessageType.TransactionRandomnessMessage
}
