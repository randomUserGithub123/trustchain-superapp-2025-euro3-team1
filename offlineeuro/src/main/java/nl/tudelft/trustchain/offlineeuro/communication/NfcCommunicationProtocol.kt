package nl.tudelft.trustchain.offlineeuro.communication

import android.content.Context
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.entity.Address
import nl.tudelft.trustchain.offlineeuro.entity.Bank
import nl.tudelft.trustchain.offlineeuro.entity.Participant
import nl.tudelft.trustchain.offlineeuro.entity.TTP
import nl.tudelft.trustchain.offlineeuro.entity.TransactionDetails
import nl.tudelft.trustchain.offlineeuro.entity.TransactionDetailsBytes
import nl.tudelft.trustchain.offlineeuro.entity.User
import nl.tudelft.trustchain.offlineeuro.enums.Role
import nl.tudelft.trustchain.offlineeuro.db.AddressBookManager
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
import nl.tudelft.trustchain.offlineeuro.community.message.*
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroupElementsBytes
import nl.tudelft.trustchain.offlineeuro.cryptography.CRSBytes
import nl.tudelft.trustchain.offlineeuro.cryptography.RandomizationElements
import nl.tudelft.trustchain.offlineeuro.cryptography.RandomizationElementsBytes
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahaiProof
import nl.tudelft.trustchain.offlineeuro.libraries.GrothSahaiSerializer

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.math.BigInteger
import java.util.concurrent.TimeoutException
import kotlin.concurrent.thread

/**
 * NfcCommunicationProtocol replaces the Bluetooth-based protocol with NFC (IsoDep) communication.
 * All methods that previously used Bluetooth sockets now use APDU commands over IsoDep.
 *
 * Note: Before calling any session-based method (e.g., getBlindSignatureRandomness, sendTransactionDetails),
 * you must call attachTag(tag) with the discovered NFC Tag. Then call startSession() to ensure IsoDep is connected.
 */
class NfcCommunicationProtocol(
    private val addressBookManager: AddressBookManager,
    private val community: OfflineEuroCommunity,
    private val context: Context
) : ICommunicationProtocol {

    // MessageList to handle community‐based messages (TTP & Bank interactions)
    val messageList = MessageList(this::handleRequestMessage)

    private val sleepDuration: Long = 100
    private val timeOutInMS = 10_000L

    override lateinit var participant: Participant

    // IsoDep instance (connected to a remote “card” / peer)
    private var isoDep: IsoDep? = null

    // Temporarily store bankPublicKey when User receives a TRANSACTION_RANDOMNESS_REQUEST
    private var bankPublicKey: Element? = null

    init {
        // Wire up the community’s messageList so that TTP/Bank can receive community messages
        community.messageList = messageList
    }

    /**
     * Call this when an NFC Tag is discovered (e.g., in onTagDiscovered in your Activity/Fragment).
     * This will obtain an IsoDep instance and connect to it.
     */
    // fun attachTag(tag: Tag) {
    //     isoDep = IsoDep.get(tag)?.apply {
    //         try {
    //             if (!isConnected) {
    //                 connect()
    //                 // Optional: set a reasonable timeout (e.g., 5 seconds)
    //                 timeout = 5000
    //             }
    //         } catch (e: Exception) {
    //             Log.e("NfcCommProtocol", "Failed to connect IsoDep: ${e.message}", e)
    //             isoDep = null
    //         }
    //     }
    // }

        /**
    * Improved version for NfcCommunicationProtocol
    */
    fun attachTag(tag: Tag) {
        try {
            // Close any existing connection first
            endSession()
            
            // Get IsoDep instance
            val isoDepTech = IsoDep.get(tag)
            if (isoDepTech == null) {
                Log.e("NfcCommProtocol", "Tag does not support IsoDep")
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "NFC tag doesn't support required protocol", Toast.LENGTH_LONG).show()
                }
                return
            }
            
            // Connect to the tag
            if (!isoDepTech.isConnected) {
                isoDepTech.connect()
                Log.d("NfcCommProtocol", "Successfully connected to IsoDep tag")
            }
            
            // Set timeout and store reference
            isoDepTech.timeout = 5000
            isoDep = isoDepTech
            
            // Notify UI of successful connection
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "NFC tag connected successfully", Toast.LENGTH_SHORT).show()
            }
            
        } catch (e: Exception) {
            Log.e("NfcCommProtocol", "Failed to attach NFC tag: ${e.message}", e)
            isoDep = null
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Failed to connect to NFC tag: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Close the current IsoDep connection (end the NFC session).
     */
    fun endSession() {
        try {
            isoDep?.close()
        } catch (e: Exception) {
            Log.w("NfcCommProtocol", "Error closing IsoDep: ${e.message}")
        } finally {
            isoDep = null
        }
    }

    /**
     * Ensure that IsoDep is attached and connected before proceeding.
     * Returns true if ready, false otherwise.
     */
    fun startSession(): Boolean {
        val tagConnection = isoDep
        if (tagConnection == null || !tagConnection.isConnected) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context.applicationContext, "No NFC tag connected", Toast.LENGTH_LONG).show()
            }
            return false
        }
        return true
    }

    /**
     * Sends an APDU command with the given INS and payload, then waits for a response.
     * It expects SW_SUCCESS (0x9000) at the end of the response bytes. Throws on failure.
     */
    @Throws(Exception::class)
    private fun sendCommand(ins: Byte, payload: ByteArray = ByteArray(0)): ByteArray {
        val adp = isoDep ?: throw IllegalStateException("IsoDep not attached")
        // Build APDU: CLA=0x00, INS, P1=0x00, P2=0x00, Lc=payload length, payload bytes
        val header = byteArrayOf(0x00, ins, 0x00, 0x00, (payload.size and 0xFF).toByte())
        val commandApdu = header + payload
        val response: ByteArray = try {
            adp.transceive(commandApdu)
        } catch (e: Exception) {
            throw TimeoutException("APDU transceive failed: ${e.message}")
        }
        if (response.size < 2) {
            throw Exception("Invalid APDU response (too short)")
        }
        // Last two bytes are SW1 | SW2
        val sw1 = response[response.size - 2]
        val sw2 = response[response.size - 1]
        if (sw1 != 0x90.toByte() || sw2 != 0x00.toByte()) {
            throw Exception("APDU returned error status: 0x${sw1.toInt().and(0xFF).toString(16)}${sw2.toInt().and(0xFF).toString(16)}")
        }
        // Return the payload (everything except the last two status bytes)
        return response.copyOfRange(0, response.size - 2)
    }

    /////////////////////////////
    //
    //      USER -> TTP (community overlay)
    //
    /////////////////////////////

    override fun getGroupDescriptionAndCRS() {
        community.getGroupDescriptionAndCRS()
        val message = waitForMessage(CommunityMessageType.GroupDescriptionCRSReplyMessage) as BilinearGroupCRSReplyMessage
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

    /////////////////////////////
    //
    //      USER -> BANK (over NFC)
    //
    /////////////////////////////

    @Throws(Exception::class)
    override fun getBlindSignatureRandomness(
        publicKey: Element,
        bankName: String,
        group: BilinearGroup
    ): Element {
        if (!startSession()) throw Exception("No NFC session")
        // Payload: bankName is not strictly needed here; card already knows it’s the Bank side
        val publicKeyBytes = publicKey.toBytes()
        val responseBytes = sendCommand(ApduConstants.INS_BLIND_RANDOMNESS_REQUEST, publicKeyBytes)
        return group.gElementFromBytes(responseBytes)
    }

    @Throws(Exception::class)
    override fun requestBlindSignature(
        publicKey: Element,
        bankName: String,
        challenge: BigInteger
    ): BigInteger {
        if (!startSession()) throw Exception("No NFC session")
        val publicKeyBytes = publicKey.toBytes()
        val challengeBytes = challenge.toByteArray()  // 32‐byte challenge
        val payload = publicKeyBytes + challengeBytes
        val responseBytes = sendCommand(ApduConstants.INS_BLIND_SIGNATURE_CHALLENGE, payload)
        return BigInteger(1, responseBytes)
    }

    /////////////////////////////
    //
    //      USER -> USER (over NFC)
    //
    /////////////////////////////

    @Throws(Exception::class)
    override fun requestTransactionRandomness(
        userNameReceiver: String,
        group: BilinearGroup
    ): RandomizationElements {
        if (!startSession()) throw Exception("No NFC session")
        // Sender is this participant, so attach senderPubKey and bankPubKey
        val senderPubBytes = participant.publicKey.toBytes()
        val bankPubBytes = bankPublicKey?.toBytes()
            ?: throw Exception("Bank public key unknown—did you retrieve it first?")
        val payload = senderPubBytes + bankPubBytes
        val responseRaw = sendCommand(ApduConstants.INS_TRANSACTION_RANDOMNESS_REQUEST, payload)
        // Deserialize RandomizationElementsBytes
        val bais = ByteArrayInputStream(responseRaw)
        val ois = ObjectInputStream(bais)
        val randWrapper = ois.readObject() as RandomizationElementsBytes
        return randWrapper.toRandomizationElements(group)
    }

    @Throws(Exception::class)
    override fun sendTransactionDetails(
        userNameReceiver: String,
        transactionDetails: TransactionDetails
    ): String {
        if (!startSession()) throw Exception("No NFC session")
        // Payload: [senderPubKey || serialized TransactionDetailsBytes]
        val senderPubBytes = participant.publicKey.toBytes()
        val wrapper = transactionDetails.toTransactionDetailsBytes()
        val baos = ByteArrayOutputStream()
        val oos = ObjectOutputStream(baos)
        oos.writeObject(wrapper)
        oos.flush()
        val txRaw = baos.toByteArray()
        val payload = senderPubBytes + txRaw
        val responseBytes = sendCommand(ApduConstants.INS_TRANSACTION_DETAILS, payload)
        return String(responseBytes, Charsets.UTF_8)
    }

    /////////////////////////////
    //
    //      BANK -> TTP (community overlay)
    //
    /////////////////////////////

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
        community.scopePeers(
            participant.name,
            getParticipantRole(),
            participant.publicKey.toBytes()
        )
    }

    @Throws(Exception::class)
    override fun getPublicKeyOf(
        name: String,
        group: BilinearGroup
    ): Element {
        if (!startSession()) throw Exception("No NFC session")
        val responseBytes = sendCommand(ApduConstants.INS_GET_PUBLIC_KEY, ByteArray(0))
        bankPublicKey = group.gElementFromBytes(responseBytes)
        return bankPublicKey!!
    }

    /**
     * Blocks until a community message of the given type arrives (or times out).
     */
    @Throws(Exception::class)
    private fun waitForMessage(messageType: CommunityMessageType): ICommunityMessage {
        var loops = 0
        while (!community.messageList.any { it.messageType == messageType }) {
            if (loops * sleepDuration >= timeOutInMS) {
                throw TimeoutException("Timeout waiting for $messageType")
            }
            Thread.sleep(sleepDuration)
            loops++
        }
        val msg = community.messageList.first { it.messageType == messageType }
        community.messageList.remove(msg)
        return msg
    }

    /**
     * Called by community when a peer sends a message; identical logic to Bluetooth version.
     */
    private fun handleAddressMessage(message: AddressMessage) {
        val publicKey = participant.group.gElementFromBytes(message.publicKeyBytes)
        val address = Address(message.name, message.role, publicKey, message.peerPublicKey)
        addressBookManager.insertAddress(address)
        participant.onDataChangeCallback?.invoke(null)
    }

    private fun handleGetBilinearGroupAndCRSRequest(message: BilinearGroupCRSRequestMessage) {
        if (participant !is TTP) return
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

    private fun handleTransactionRandomizationElementsRequest(message: TransactionRandomizationElementsRequestMessage) {
        val group = participant.group
        val publicKey = group.gElementFromBytes(message.publicKey)
        val requestingPeer = message.requestingPeer
        val randElements = participant.generateRandomizationElements(publicKey)
        val randWrapper = randElements.toRandomizationElementsBytes()
        community.sendTransactionRandomizationElements(randWrapper, requestingPeer)
    }

    private fun handleTransactionMessage(message: TransactionMessage) {
        val bankPubKey = if (participant is Bank) {
            participant.publicKey
        } else {
            addressBookManager.getAddressByName("Bank").publicKey
        }
        val group = participant.group
        val senderPub = group.gElementFromBytes(message.publicKeyBytes)
        val txWrapper = message.transactionDetailsBytes
        val txDetails = txWrapper.toTransactionDetails(group)
        val result = participant.onReceivedTransaction(txDetails, bankPubKey, senderPub)
        val requestingPeer = message.requestingPeer
        community.sendTransactionResult(result, requestingPeer)
    }

    private fun handleRegistrationMessage(message: TTPRegistrationMessage) {
        if (participant !is TTP) return
        val ttp = participant as TTP
        val pubKey = ttp.group.gElementFromBytes(message.userPKBytes)
        ttp.registerUser(message.userName, pubKey)
    }

    private fun handleAddressRequestMessage(message: AddressRequestMessage) {
        val role = getParticipantRole()
        community.sendAddressReply(
            participant.name,
            role,
            participant.publicKey.toBytes(),
            message.requestingPeer
        )
    }

    private fun handleFraudControlRequestMessage(message: FraudControlRequestMessage) {
        if (getParticipantRole() != Role.TTP) return
        val ttp = participant as TTP
        val firstProof = GrothSahaiSerializer.deserializeProofBytes(
            message.firstProofBytes,
            participant.group
        )
        val secondProof = GrothSahaiSerializer.deserializeProofBytes(
            message.secondProofBytes,
            participant.group
        )
        val result = ttp.getUserFromProofs(firstProof, secondProof)
        community.sendFraudControlReply(result, message.requestingPeer)
    }

    private fun handleRequestMessage(message: ICommunityMessage) {
        when (message) {
            is AddressMessage -> handleAddressMessage(message)
            is AddressRequestMessage -> handleAddressRequestMessage(message)
            is BilinearGroupCRSRequestMessage -> handleGetBilinearGroupAndCRSRequest(message)
            is TransactionRandomizationElementsRequestMessage -> handleTransactionRandomizationElementsRequest(message)
            is TransactionMessage -> handleTransactionMessage(message)
            is TTPRegistrationMessage -> handleRegistrationMessage(message)
            is FraudControlRequestMessage -> handleFraudControlRequestMessage(message)
            else -> throw Exception("Unsupported message type: ${message.messageType}")
        }
    }

    private fun getParticipantRole(): Role {
        return when (participant) {
            is User -> Role.User
            is TTP -> Role.TTP
            is Bank -> Role.Bank
            else -> throw Exception("Unknown role for participant")
        }
    }
}
