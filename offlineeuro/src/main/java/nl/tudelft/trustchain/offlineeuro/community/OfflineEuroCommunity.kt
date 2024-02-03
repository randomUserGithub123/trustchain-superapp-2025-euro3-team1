package nl.tudelft.trustchain.offlineeuro.community

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
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
import nl.tudelft.trustchain.offlineeuro.entity.BankRegistration
import nl.tudelft.trustchain.offlineeuro.entity.Challenge
import nl.tudelft.trustchain.offlineeuro.entity.ChallengeResponse
import nl.tudelft.trustchain.offlineeuro.entity.Receipt
import nl.tudelft.trustchain.offlineeuro.entity.Token
import nl.tudelft.trustchain.offlineeuro.entity.UnsignedTokenSignRequestEntry
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
    const val TOKEN_SIGN_REQUEST = 13
    const val TOKEN_SIGN_REPLY = 14
    const val SEND_TOKENS = 15
    const val CHALLENGE = 16
    const val CHALLENGE_REPLY = 17
    const val DEPOSIT = 18
}

@RequiresApi(Build.VERSION_CODES.O)
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

        // TODO FIX NAMES OF BANK AND USER
        this.context = context
        bank = Bank(name, context)
        user = User(name, context)
        messageHandlers[MessageID.FIND_BANK] = ::onFindBankPacket
        messageHandlers[MessageID.BANK_DETAILS_REPLY] = ::onBankDetailsReplyPacket
        messageHandlers[MessageID.USER_REGISTRATION] = ::onUserRegistrationPacket
        messageHandlers[MessageID.USER_REGISTRATION_REPLY] = ::onUserRegistrationReplyPacket
        messageHandlers[MessageID.TOKEN_SIGN_REQUEST] = ::onTokenSignRequestPacket
        messageHandlers[MessageID.TOKEN_SIGN_REPLY] = ::onTokenSignReplyPacket
        messageHandlers[MessageID.SEND_TOKENS] = ::onTokensReceivedPacket
        messageHandlers[MessageID.CHALLENGE] = ::onChallengePacket
        messageHandlers[MessageID.CHALLENGE_REPLY] = ::onChallengeResponsePacket
        messageHandlers[MessageID.DEPOSIT] = ::onDepositPacket
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
        onFindBank(requestingPeer)
    }

    private fun onFindBank(requestingPeer: Peer) {
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
        user.handleBankDetailsReply(bankDetails)
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
        val response = bank.handleUserRegistration(payload.userRegistrationMessage)
        val responseMessagePacket = serializePacket(
            MessageID.USER_REGISTRATION_REPLY,
            UserRegistrationResponsePayload(response)
        )

        send(peer, responseMessagePacket)
    }

    fun sendUnsignedTokens(tokensToSign: List<UnsignedTokenSignRequestEntry>,
                           publicKeyBank: ByteArray) {
        val bankPeer = getPeerByPublicKeyBytes(publicKeyBank) ?: return

        val signRequestPacket = serializePacket(
            MessageID.TOKEN_SIGN_REQUEST,
            UnsignedTokenPayload(user.name, tokensToSign)
        )

        send(bankPeer, signRequestPacket)
    }

    fun sendTokensToRandomPeer(tokens: List<Token>, bank: BankRegistration): Boolean {
        val randomPeer: Peer = getRandomPeer(bank.bankDetails.publicKeyBytes) ?: return false

        val tokenPacket = serializePacket(
            MessageID.SEND_TOKENS,
            SendTokensPayload(bank.bankDetails.name, tokens)
        )

        send(randomPeer, tokenPacket)
        return true
    }

    fun sendReceiptsToBank(receipts: List<Receipt>, userName: String, bankPublicKeyBytes: ByteArray): Boolean {
        val bankPeer = getPeerByPublicKeyBytes(bankPublicKeyBytes) ?: return false

        val packet = serializePacket(MessageID.DEPOSIT, DepositPayload(userName, receipts))

        send(bankPeer, packet)
        return true
    }

    private fun onUserRegistrationReplyPacket(packet: Packet) {
        val (_, payload) = packet.getAuthPayload(UserRegistrationResponsePayload)
        onUserRegistrationReply(payload)
    }

    private fun onUserRegistrationReply(payload: UserRegistrationResponsePayload) {
        user.handleRegistrationResponse(payload.userRegistrationResponseMessage)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun onTokenSignRequestPacket(packet: Packet) {
        val (peer, payload) = packet.getAuthPayload(UnsignedTokenPayload)
        onTokenSignRequest(peer, payload)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun onTokenSignRequest(peer: Peer, payload: UnsignedTokenPayload) {
        val responseList = bank.handleSignUnsignedTokenRequest(payload.userName, payload.tokensToSign)

        val responsePacket = serializePacket(
            MessageID.TOKEN_SIGN_REPLY,
            UnsignedTokenResponsePayload(bank.name, responseList)
        )

        send(peer, responsePacket)
    }

    private fun onTokenSignReplyPacket(packet: Packet) {
        val (_, payload) = packet.getAuthPayload(UnsignedTokenResponsePayload)
        onTokenSignReply(payload)
    }

    private fun onTokenSignReply(payload: UnsignedTokenResponsePayload) {
        user.handleUnsignedTokenSignResponse(payload.bankName, payload.signedTokens)
    }

    private fun onTokensReceivedPacket(packet: Packet) {
        val (peer, payload) = packet.getAuthPayload(SendTokensPayload)
        onTokensReceived(peer, payload)
    }

    private fun onTokensReceived(peer: Peer, payload: SendTokensPayload) {
        val challenges = user.onTokensReceived(payload.tokens, payload.bankName)
        sendChallenges(challenges, payload.bankName, peer)
    }

    private fun sendChallenges(challenges: List<Challenge>, bankName: String, peer: Peer) {
        val packet = serializePacket(MessageID.CHALLENGE, ChallengePayload(bankName, challenges))
        send(peer, packet)
    }

    private fun onChallengePacket(packet: Packet) {
        val (peer, payload) = packet.getAuthPayload(ChallengePayload)
        val response = user.onChallenges(payload.challenges, payload.bankName)
        sendChallengeResponses(peer, payload.bankName, response)
    }

    private fun sendChallengeResponses(peer: Peer, bankName: String, challengeResponses: List<ChallengeResponse>) {
        val packet = serializePacket(MessageID.CHALLENGE_REPLY, ChallengeResponsePayload(bankName, challengeResponses))
        send(peer, packet)
    }

    private fun onChallengeResponsePacket(packet: Packet) {
        val (_, payload) = packet.getAuthPayload(ChallengeResponsePayload)
        onChallengeResponse(payload)
    }

    private fun onChallengeResponse(payload: ChallengeResponsePayload) {
        user.onChallengesResponseReceived(payload.challenges, payload.bankName)
    }

    private fun onDepositPacket(packet: Packet) {
        val (_, payload) = packet.getAuthPayload(DepositPayload)

    }

    private fun onDeposit(payload: DepositPayload) {
        bank.handleOnDeposit(payload.receipts, payload.userName)
    }

    private fun getPeerByPublicKeyBytes(publicKey: ByteArray): Peer? {
        return getPeers().find {
            it.publicKey.keyToBin().contentEquals(publicKey)
        }
    }

    private fun getRandomPeer(publicKeyBank: ByteArray): Peer? {
        return getPeers().shuffled().find {
            !it.publicKey.keyToBin().contentEquals(publicKeyBank)
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
