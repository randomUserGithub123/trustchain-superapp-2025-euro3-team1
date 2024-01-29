package nl.tudelft.trustchain.offlineeuro.community

import android.content.Context
import android.widget.Toast
import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.Overlay
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCrawler
import nl.tudelft.ipv8.attestation.trustchain.TrustChainSettings
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainStore
import nl.tudelft.ipv8.messaging.Packet
import nl.tudelft.trustchain.common.eurotoken.TransactionRepository
import nl.tudelft.trustchain.offlineeuro.entity.Bank
import nl.tudelft.trustchain.offlineeuro.entity.User

enum class Role {
    Bank,
    User;

    companion object {
        fun fromInt(value: Int): Role {
            return values().find { it.ordinal == value }!!
        }
    }
}

object MessageID {
    const val FIND_BANK = 9
    const val BANK_DETAILS_REPLY = 10
}

class OfflineEuroCommunity(
    context: Context,
    settings: TrustChainSettings,
    database: TrustChainStore,
    crawler: TrustChainCrawler = TrustChainCrawler()
) : Community() {
    override val serviceId = "ffffd716494b474ea9f614a16a4da0aed6899aec"

    private lateinit var transactionRepository: TransactionRepository

    var role: Role = Role.User
    var name: String = "BestBank"
    var bank: Bank
    var user: User
    val context: Context
    init {
        this.context = context
        bank = Bank(context)
        user = User()
        messageHandlers[MessageID.FIND_BANK] = ::onFindBankPacket
        messageHandlers[MessageID.BANK_DETAILS_REPLY] = ::onBankDetailsReplyPacket

    }

    fun findBank() {

        val findBankPacket = serializePacket(
            MessageID.FIND_BANK,
            FindBankPayload(name, Role.User)
        )

        for (peer: Peer in getPeers()) {
            send(peer, findBankPacket)
        }
    }

    private fun onFindBankPacket(packet: Packet) {
        val (requestingPeer, payload) = packet.getAuthPayload(FindBankPayload)
        onFindBank(requestingPeer, payload)
    }

    private fun onFindBank(requestingPeer: Peer, payload: FindBankPayload) {
        // Do nothing if you are not a bank
        if (role == Role.User)
            return

        // Create the response
        val responsePacket = serializePacket(
            MessageID.BANK_DETAILS_REPLY,
            BankDetailsPayload(name, Role.Bank)
        )

        // Send the reply
        send(requestingPeer, responsePacket)
    }

    private fun onBankDetailsReplyPacket(packet: Packet) {
        val (bankPeer, payload) = packet.getAuthPayload(BankDetailsPayload)
        onBankDetailsReply(bankPeer, payload)

    }

    private fun onBankDetailsReply(bankPeer: Peer, payload: BankDetailsPayload) {
        val bankName = payload.name
        user.banks.add(Pair(bankName, bankPeer))
        Toast.makeText(context, "Found Bank $bankName", Toast.LENGTH_SHORT).show()
    }

    class Factory(
        private val context: Context,
        private val settings: TrustChainSettings,
        private val database: TrustChainStore,
        private val crawler: TrustChainCrawler = TrustChainCrawler()
    ) : Overlay.Factory<OfflineEuroCommunity>(OfflineEuroCommunity::class.java) {
        override fun create(): OfflineEuroCommunity {
            return OfflineEuroCommunity(context, settings, database, crawler)
        }
    }
}
