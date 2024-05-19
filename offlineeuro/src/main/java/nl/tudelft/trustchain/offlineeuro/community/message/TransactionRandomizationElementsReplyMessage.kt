package nl.tudelft.trustchain.offlineeuro.community.message

import nl.tudelft.trustchain.offlineeuro.cryptography.RandomizationElementsBytes

class TransactionRandomizationElementsReplyMessage (
    val randomizationElementsBytes: RandomizationElementsBytes,
): ICommunityMessage {
    override val messageType = CommunityMessageType.TransactionRandomnessReplyMessage
}
