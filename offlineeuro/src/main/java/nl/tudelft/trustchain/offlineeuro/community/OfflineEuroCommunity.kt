package nl.tudelft.trustchain.offlineeuro.community

import android.content.Context
import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.Overlay
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCrawler
import nl.tudelft.ipv8.attestation.trustchain.TrustChainSettings
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainStore
import nl.tudelft.ipv8.messaging.Packet
import nl.tudelft.trustchain.common.eurotoken.TransactionRepository
import nl.tudelft.trustchain.offlineeuro.entity.Bank
import nl.tudelft.trustchain.offlineeuro.entity.BankDetails
import nl.tudelft.trustchain.offlineeuro.entity.User
import nl.tudelft.trustchain.offlineeuro.entity.UserRegistrationMessage

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
    const val USER_REGISTRATION = 11
    const val USER_REGISTRATION_REPLY = 12
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
    val name: String = "BestBank"
    val bank: Bank
    val user: User
    val context: Context
    init {
        this.context = context
        bank = Bank(context)
        user = User(context)
        messageHandlers[MessageID.FIND_BANK] = ::onFindBankPacket
        messageHandlers[MessageID.BANK_DETAILS_REPLY] = ::onBankDetailsReplyPacket
        messageHandlers[MessageID.USER_REGISTRATION] = ::onUserRegistrationPacket
        messageHandlers[MessageID.USER_REGISTRATION_REPLY] = ::onUserRegistrationReplyPacket

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

        val (eb, nb) = bank.getPublicRSAValues()
        val bankDetails = BankDetails(
            name,
            bank.z,
            eb,
            nb,
            myPeer.publicKey.keyToBin()
        )
        // Create the response
        val responsePacket = serializePacket(
            MessageID.BANK_DETAILS_REPLY,
            BankDetailsPayload(bankDetails)
        )

        // Send the reply
        send(requestingPeer, responsePacket)
    }

    private fun onBankDetailsReplyPacket(packet: Packet) {
        val (_, payload) = packet.getAuthPayload(BankDetailsPayload)
        onBankDetailsReply(payload)

    }

    private fun onBankDetailsReply(payload: BankDetailsPayload) {
        val bankDetails = payload.bankDetails
        user.bankRegistrationManager.addNewBank(bankDetails)
    }

    fun sendUserRegistrationMessage(userRegistrationMessage: UserRegistrationMessage,
                                    publicKeyBank: ByteArray): Boolean {
        val bankPeer = getPeerByPublicKeyBytes(publicKeyBank) ?: return false

        val packet = serializePacket(
            MessageID.USER_REGISTRATION,
            UserRegistrationPayload(userRegistrationMessage)
        )

        send(bankPeer, packet)
        return true
    }
    private fun onUserRegistrationPacket(packet: Packet) {
        val (userPeer, payload) = packet.getAuthPayload(UserRegistrationPayload)
        onUserRegistration(userPeer, payload)
    }

    private fun onUserRegistration(peer: Peer, payload: UserRegistrationPayload) {
        val response = bank.registerUser(payload.userRegistrationMessage)
        val responseMessagePacket = serializePacket(
            MessageID.USER_REGISTRATION_REPLY,
            UserRegistrationResponsePayload(response)
        )

        send(peer, responseMessagePacket)
    }


    private fun onUserRegistrationReplyPacket(packet: Packet) {

    }

    private fun getPeerByPublicKeyBytes(publicKey: ByteArray): Peer? {
        return getPeers().find {
            it.publicKey.keyToBin().contentEquals(publicKey)
        }
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
