package nl.tudelft.trustchain.offlineeuro.communication

import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
import nl.tudelft.trustchain.offlineeuro.community.message.AddressMessage
import nl.tudelft.trustchain.offlineeuro.community.message.AddressRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BilinearGroupCRSReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BilinearGroupCRSRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureRandomnessReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureRandomnessRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BloomFilterReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.CommunityMessageType
import nl.tudelft.trustchain.offlineeuro.community.message.FraudControlReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.FraudControlRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.ICommunityMessage
import nl.tudelft.trustchain.offlineeuro.community.message.MessageList
import nl.tudelft.trustchain.offlineeuro.community.message.TTPRegistrationMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionRandomizationElementsReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionRandomizationElementsRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionResultMessage
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.BloomFilter
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahaiProof
import nl.tudelft.trustchain.offlineeuro.cryptography.RandomizationElements
import nl.tudelft.trustchain.offlineeuro.db.AddressBookManager
import nl.tudelft.trustchain.offlineeuro.entity.Address
import nl.tudelft.trustchain.offlineeuro.entity.Bank
import nl.tudelft.trustchain.offlineeuro.entity.Participant
import nl.tudelft.trustchain.offlineeuro.entity.TTP
import nl.tudelft.trustchain.offlineeuro.entity.TransactionDetails
import nl.tudelft.trustchain.offlineeuro.entity.User
import nl.tudelft.trustchain.offlineeuro.enums.Role
import nl.tudelft.trustchain.offlineeuro.libraries.GrothSahaiSerializer
import java.math.BigInteger

class IPV8CommunicationProtocol(
    val addressBookManager: AddressBookManager,
    val community: OfflineEuroCommunity,
) : ICommunicationProtocol {
    val messageList = MessageList(this::handleRequestMessage)

    init {
        community.messageList = messageList
    }

    private val sleepDuration: Long = 100
    private val timeOutInMS = 10000
    override lateinit var participant: Participant

    override fun getGroupDescriptionAndCRS() {
        community.getGroupDescriptionAndCRS()
        val message =
            waitForMessage(CommunityMessageType.GroupDescriptionCRSReplyMessage) as BilinearGroupCRSReplyMessage

        participant.group.updateGroupElements(message.groupDescription)
        val crs = message.crs.toCRS(participant.group)
        participant.crs = crs
        messageList.add(message.addressMessage)
    }

    override fun register(
        userName: String,
        publicKey: Element,
        nameTTP: String
    ) {
        val ttpAddress = addressBookManager.getAddressByName(nameTTP)
        community.registerAtTTP(userName, publicKey.toBytes(), ttpAddress.peerPublicKey!!)
    }

    override fun getBlindSignatureRandomness(
        publicKey: Element,
        bankName: String,
        group: BilinearGroup
    ): Element {
        val bankAddress = addressBookManager.getAddressByName(bankName)
        community.getBlindSignatureRandomness(publicKey.toBytes(), bankAddress.peerPublicKey!!)

        val replyMessage =
            waitForMessage(CommunityMessageType.BlindSignatureRandomnessReplyMessage) as BlindSignatureRandomnessReplyMessage
        return group.gElementFromBytes(replyMessage.randomnessBytes)
    }

    override fun requestBlindSignature(
        publicKey: Element,
        bankName: String,
        challenge: BigInteger
    ): BigInteger {
        val bankAddress = addressBookManager.getAddressByName(bankName)
        community.getBlindSignature(challenge, publicKey.toBytes(), bankAddress.peerPublicKey!!)

        val replyMessage = waitForMessage(CommunityMessageType.BlindSignatureReplyMessage) as BlindSignatureReplyMessage
        return replyMessage.signature
    }

    override fun requestTransactionRandomness(
        userNameReceiver: String,
        group: BilinearGroup
    ): RandomizationElements {
        val peerAddress = addressBookManager.getAddressByName(userNameReceiver)
        community.getTransactionRandomizationElements(participant.publicKey.toBytes(), peerAddress.peerPublicKey!!)
        val message = waitForMessage(CommunityMessageType.TransactionRandomnessReplyMessage) as TransactionRandomizationElementsReplyMessage
        return message.randomizationElementsBytes.toRandomizationElements(group)
    }

    override fun sendTransactionDetails(
        userNameReceiver: String,
        transactionDetails: TransactionDetails
    ): String {
        val peerAddress = addressBookManager.getAddressByName(userNameReceiver)
        community.sendTransactionDetails(
            participant.publicKey.toBytes(),
            peerAddress.peerPublicKey!!,
            transactionDetails.toTransactionDetailsBytes()
        )
        val message = waitForMessage(CommunityMessageType.TransactionResultMessage) as TransactionResultMessage
        return message.result
    }

    override fun requestFraudControl(
        firstProof: GrothSahaiProof,
        secondProof: GrothSahaiProof,
        nameTTP: String
    ): String {
        val ttpAddress = addressBookManager.getAddressByName(nameTTP)
        community.sendFraudControlRequest(
            GrothSahaiSerializer.serializeGrothSahaiProof(firstProof),
            GrothSahaiSerializer.serializeGrothSahaiProof(secondProof),
            ttpAddress.peerPublicKey!!
        )
        val message = waitForMessage(CommunityMessageType.FraudControlReplyMessage) as FraudControlReplyMessage
        return message.result
    }

    fun scopePeers() {
        community.scopePeers(participant.name, getParticipantRole(), participant.publicKey.toBytes())
    }

    override fun getPublicKeyOf(
        name: String,
        group: BilinearGroup
    ): Element {
        return addressBookManager.getAddressByName(name).publicKey
    }

    override fun requestBloomFilter(participantName: String): BloomFilter {
        val peerAddress = addressBookManager.getAddressByName(participantName)
        community.sendBloomFilterRequest(peerAddress.peerPublicKey!!)

        val reply = waitForMessage(CommunityMessageType.BLOOM_FILTER_REPLY) as BloomFilterReplyMessage
        return reply.bloomFilter
    }

    override fun sendBloomFilter(
        participantName: String,
        bloomFilter: BloomFilter
    ) {
        val peerAddress = addressBookManager.getAddressByName(participantName)
        val message = BloomFilterReplyMessage(bloomFilter)
        community.addMessage(message)
    }

    private fun waitForMessage(messageType: CommunityMessageType): ICommunityMessage {
        var loops = 0

        while (!community.messageList.any { it.messageType == messageType }) {
            if (loops * sleepDuration >= timeOutInMS) {
                throw Exception("TimeOut")
            }
            Thread.sleep(sleepDuration)
            loops++
        }

        val message =
            community.messageList.first { it.messageType == messageType }
        community.messageList.remove(message)

        return message
    }

    private fun handleAddressMessage(message: AddressMessage) {
        val publicKey = participant.group.gElementFromBytes(message.publicKeyBytes)
        val address = Address(message.name, message.role, publicKey, message.peerPublicKey)
        addressBookManager.insertAddress(address)
        participant.onDataChangeCallback?.invoke(null)
    }

    private fun handleGetBilinearGroupAndCRSRequest(message: BilinearGroupCRSRequestMessage) {
        if (participant !is TTP) {
            return
        } else {
            val groupBytes = participant.group.toGroupElementBytes()
            val crsBytes = participant.crs.toCRSBytes()
            val peer = message.requestingPeer
            community.sendGroupDescriptionAndCRS(
                groupBytes,
                crsBytes,
                participant.publicKey.toBytes(),
                peer
            )
        }
    }

    private fun handleBlindSignatureRandomnessRequest(message: BlindSignatureRandomnessRequestMessage) {
        if (participant !is Bank) {
            throw Exception("Participant is not a bank")
        }
        val bank = (participant as Bank)
        val publicKey = bank.group.gElementFromBytes(message.publicKeyBytes)
        val randomness = bank.getBlindSignatureRandomness(publicKey)
        val requestingPeer = message.peer
        community.sendBlindSignatureRandomnessReply(randomness.toBytes(), requestingPeer)
    }

    private fun handleBlindSignatureRequestMessage(message: BlindSignatureRequestMessage) {
        if (participant !is Bank) {
            throw Exception("Participant is not a bank")
        }
        val bank = (participant as Bank)

        val publicKey = bank.group.gElementFromBytes(message.publicKeyBytes)
        val challenge = message.challenge
        val signature = bank.createBlindSignature(challenge, publicKey)
        val requestingPeer = message.peer
        community.sendBlindSignature(signature, requestingPeer)
    }

    private fun handleTransactionRandomizationElementsRequest(message: TransactionRandomizationElementsRequestMessage) {
        val group = participant.group
        val publicKey = group.gElementFromBytes(message.publicKey)
        val requestingPeer = message.requestingPeer

        val randomizationElements = participant.generateRandomizationElements(publicKey)
        val randomizationElementBytes = randomizationElements.toRandomizationElementsBytes()
        community.sendTransactionRandomizationElements(randomizationElementBytes, requestingPeer)
    }

    private fun handleTransactionMessage(message: TransactionMessage) {
        val bankPublicKey =
            if (participant is Bank) {
                participant.publicKey
            } else {
                addressBookManager.getAddressByName("Bank").publicKey
            }

        val group = participant.group
        val publicKey = group.gElementFromBytes(message.publicKeyBytes)
        val transactionDetailsBytes = message.transactionDetailsBytes
        val transactionDetails = transactionDetailsBytes.toTransactionDetails(group)
        val transactionResult = participant.onReceivedTransaction(transactionDetails, bankPublicKey, publicKey)
        val requestingPeer = message.requestingPeer
        community.sendTransactionResult(transactionResult, requestingPeer)
    }

    private fun handleRegistrationMessage(message: TTPRegistrationMessage) {
        if (participant !is TTP) {
            return
        }

        val ttp = participant as TTP
        val publicKey = ttp.group.gElementFromBytes(message.userPKBytes)
        ttp.registerUser(message.userName, publicKey)
    }

    private fun handleAddressRequestMessage(message: AddressRequestMessage) {
        val role = getParticipantRole()

        community.sendAddressReply(participant.name, role, participant.publicKey.toBytes(), message.requestingPeer)
    }

    private fun handleFraudControlRequestMessage(message: FraudControlRequestMessage) {
        if (getParticipantRole() != Role.TTP) {
            return
        }
        val ttp = participant as TTP
        val firstProof = GrothSahaiSerializer.deserializeProofBytes(message.firstProofBytes, participant.group)
        val secondProof = GrothSahaiSerializer.deserializeProofBytes(message.secondProofBytes, participant.group)
        val result = ttp.getUserFromProofs(firstProof, secondProof)
        community.sendFraudControlReply(result, message.requestingPeer)
    }

    private fun handleRequestMessage(message: ICommunityMessage) {
        when (message) {
            is AddressMessage -> handleAddressMessage(message)
            is AddressRequestMessage -> handleAddressRequestMessage(message)
            is BilinearGroupCRSRequestMessage -> handleGetBilinearGroupAndCRSRequest(message)
            is BlindSignatureRandomnessRequestMessage -> handleBlindSignatureRandomnessRequest(message)
            is BlindSignatureRequestMessage -> handleBlindSignatureRequestMessage(message)
            is TransactionRandomizationElementsRequestMessage -> handleTransactionRandomizationElementsRequest(message)
            is TransactionMessage -> handleTransactionMessage(message)
            is TTPRegistrationMessage -> handleRegistrationMessage(message)
            is FraudControlRequestMessage -> handleFraudControlRequestMessage(message)
            else -> throw Exception("Unsupported message type")
        }
        return
    }

    private fun getParticipantRole(): Role {
        return when (participant) {
            is User -> Role.User
            is TTP -> Role.TTP
            is Bank -> Role.Bank
            else -> throw Exception("Unknown role")
        }
    }
}
