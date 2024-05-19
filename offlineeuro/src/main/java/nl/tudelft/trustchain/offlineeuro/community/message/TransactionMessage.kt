package nl.tudelft.trustchain.offlineeuro.community.message

import nl.tudelft.ipv8.Peer
import nl.tudelft.trustchain.offlineeuro.entity.TransactionDetailsBytes

class TransactionMessage(
    val publicKeyBytes: ByteArray,
    val transactionDetailsBytes: TransactionDetailsBytes,
    val requestingPeer: Peer
) : ICommunityMessage {
    override val messageType: CommunityMessageType = CommunityMessageType.TransactionMessage
}
