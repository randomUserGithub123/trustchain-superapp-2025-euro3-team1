package nl.tudelft.trustchain.offlineeuro.communication

import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import it.unisa.dia.gas.jpbc.Element
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.math.BigInteger
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeoutException
import kotlin.concurrent.thread
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
import nl.tudelft.trustchain.offlineeuro.community.message.MessageList
import nl.tudelft.trustchain.offlineeuro.community.message.AddressMessage
import nl.tudelft.trustchain.offlineeuro.community.message.AddressRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BilinearGroupCRSReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BilinearGroupCRSRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureRandomnessReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureRandomnessRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.CommunityMessageType
import nl.tudelft.trustchain.offlineeuro.community.message.FraudControlReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.FraudControlRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.ICommunityMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TTPRegistrationMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionRandomizationElementsReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionRandomizationElementsRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionResultMessage
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroupElementsBytes
import nl.tudelft.trustchain.offlineeuro.cryptography.CRSBytes
import nl.tudelft.trustchain.offlineeuro.cryptography.RandomizationElements
import nl.tudelft.trustchain.offlineeuro.cryptography.RandomizationElementsBytes
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahaiProof
import nl.tudelft.trustchain.offlineeuro.libraries.GrothSahaiSerializer

class BluetoothCommunicationProtocol(
    val addressBookManager: AddressBookManager,
    val community: OfflineEuroCommunity,
    private val context: Context
) : ICommunicationProtocol {

    val messageList = MessageList(this::handleRequestMessage)

    private val sleepDuration: Long = 100
    private val timeOutInMS = 10000

    override lateinit var participant: Participant

    private val bluetoothAdapter: BluetoothAdapter =
        BluetoothAdapter.getDefaultAdapter() ?: throw IllegalStateException("Bluetooth not supported")

    private val SERVICE_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val SERVICE_NAME = "OfflineEuroTransfer"

    // Bluetooth Server
    private var serverThread: Thread? = null
    private var serverSocket: BluetoothServerSocket? = null
    @Volatile private var running: Boolean = true

    // Bluetooth Client
    private var activeDevice: BluetoothDevice? = null

    private lateinit var bankPublicKey: Element

    init {
        community.messageList = messageList
        startServer()
    }

    private fun startServer() {
        serverThread = thread(start = true) {
            try {
                
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)

                while (running) {
                    try {
                        
                        val socket = serverSocket?.accept()
                        if (socket != null) {
                            thread { handleIncomingConnection(socket) }
                        }

                    } catch (e: Exception) {
                        if (running) {
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(context.applicationContext, "Server error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context.applicationContext, "Server init failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                Log.d("BluetoothProtocol", "Bluetooth server thread exiting.")
            }
        }
    }

    fun stopServer() {
        running = false
        try {
            serverSocket?.close()
            serverThread?.interrupt()
            Log.d("BluetoothProtocol", "Bluetooth server stopped.")
        } catch (e: Exception) {
            Log.e("BluetoothProtocol", "Error stopping server", e)
        }
    }

    private fun handleIncomingConnection(socket: BluetoothSocket) {
        thread {
            try {

                val output = ObjectOutputStream(socket.outputStream)
                val input = ObjectInputStream(socket.inputStream)

                when (val requestType = input.readObject() as String) {

                    "GET_GROUP_DESCRIPTION_AND_CRS" -> {

                        if (participant !is TTP) {
                            throw Exception("Only TTP can provide group description and CRS")
                        }

                        val ttp = participant as TTP

                        val groupBytes = ttp.group.toGroupElementBytes()
                        val crsBytes = ttp.crs.toCRSBytes()

                        output.writeObject(groupBytes)
                        output.writeObject(crsBytes)
                        output.writeObject(ttp.publicKey.toBytes())
                        output.flush()

                    }

                    "REGISTER_AT_TTP" -> {

                        val userName = input.readObject() as String
                        val publicKeyBytes = input.readObject() as ByteArray
                        val ttpPublicKeyBytes = input.readObject() as ByteArray

                        if (participant !is TTP) {
                            throw Exception("Only TTP can handle registrations")
                        }

                        val ttp = participant as TTP
                        val publicKey = ttp.group.gElementFromBytes(publicKeyBytes)
                        
                        ttp.registerUser(userName, publicKey)

                        output.writeObject("SUCCESS")
                        output.flush()

                    }

                    "BLIND_SIGNATURE_RANDOMNESS_REQUEST" -> {
                        
                        val publicKeyBytes = input.readObject() as ByteArray

                        if (participant !is Bank) {
                            throw Exception("Not a bank")
                        }

                        val bank = participant as Bank
                        val publicKey = bank.group.gElementFromBytes(publicKeyBytes)
                        val randomness = bank.getBlindSignatureRandomness(publicKey)

                        output.writeObject(randomness.toBytes())
                        output.flush()
                    
                    }

                    "BLIND_SIGNATURE_REQUEST" -> {

                        val publicKeyBytes = input.readObject() as ByteArray
                        val challenge = input.readObject() as BigInteger

                        if (participant !is Bank) {
                            throw Exception("Participant is not a bank")
                        }

                        val bank = participant as Bank
                        val publicKey = bank.group.gElementFromBytes(publicKeyBytes)

                        val signature = bank.createBlindSignature(challenge, publicKey)

                        output.writeObject(signature)
                        output.flush()

                    }

                    "GET_PUBLIC_KEY" -> {

                        val publicKeyBytes = participant.publicKey.toBytes()
                        output.writeObject(publicKeyBytes)
                        output.flush()

                    }

                    "TRANSACTION_RANDOMNESS_REQUEST" -> {

                        val publicKeyBytes = input.readObject() as ByteArray
                        val bankPublicKeyBytes = input.readObject() as ByteArray

                        if (participant !is User) {
                            throw Exception("Participant is not a user. Participant is: ${participant::class.qualifiedName}")
                        }

                        val user = participant as User
                        val publicKey = user.group.gElementFromBytes(publicKeyBytes)

                        this.bankPublicKey = user.group.gElementFromBytes(bankPublicKeyBytes)

                        val randomizationElements = participant.generateRandomizationElements(publicKey)
                        val randomizationElementBytes = randomizationElements.toRandomizationElementsBytes()

                        output.writeObject(randomizationElementBytes)
                        output.flush()

                    }

                    "TRANSACTION_DETAILS" -> {

                        val publicKeyBytes = input.readObject() as ByteArray
                        val transactionDetailsBytes = input.readObject() as TransactionDetailsBytes

                        val bankPublicKey =
                            if (participant is Bank) {
                                participant.publicKey
                            } else {
                                this.bankPublicKey
                            }

                        val group = participant.group
                        val publicKey = group.gElementFromBytes(publicKeyBytes)
                        val transactionDetails = transactionDetailsBytes.toTransactionDetails(group)

                        val result = participant.onReceivedTransaction(transactionDetails, bankPublicKey, publicKey)

                        output.writeObject(result)
                        output.flush()

                    }

                }

                socket.close()

            } catch (e: Exception) {
                if (e.message?.contains("closed") == true) {
                    Log.i("BluetoothProtocol", "Socket closed normally.")
                } else {
                    Log.e("BluetoothProtocol", "Exception during connection", e)
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context.applicationContext, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun discoverNearbyDeviceBlocking(): BluetoothDevice? {

        val foundDevices = mutableListOf<BluetoothDevice>()
        val latch = CountDownLatch(1)

        val appContext = context.applicationContext

        if (!bluetoothAdapter.isEnabled) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(appContext, "Enable Bluetooth first", Toast.LENGTH_LONG).show()
            }
            return null
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
        val locationEnabled = locationManager?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true
        if (!locationEnabled) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(appContext, "Enable location services", Toast.LENGTH_LONG).show()
            }
            return null
        }

        val bondedDevices = bluetoothAdapter.bondedDevices
        for (device in bondedDevices) {
            val deviceClass = device.bluetoothClass?.deviceClass
            if (deviceClass == BluetoothClass.Device.PHONE_SMART) {
                unpairDevice(device)
                Log.i("BluetoothProtocol", "Unpaired device: ${device.name} [${device.address}]")
            }
        }

        Handler(Looper.getMainLooper()).post {
            Toast.makeText(appContext, "Starting Bluetooth discovery...", Toast.LENGTH_SHORT).show()
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action

                // Handler(Looper.getMainLooper()).post {
                //     Toast.makeText(appContext, "Broadcast: $action", Toast.LENGTH_SHORT).show()
                // }

                when (action) {
                    BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(appContext, "Discovery started", Toast.LENGTH_SHORT).show()
                        }
                    }

                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(appContext, "Discovery finished", Toast.LENGTH_SHORT).show()
                        }
                        latch.countDown()
                    }

                    BluetoothDevice.ACTION_FOUND -> {
                        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                        val name = device?.name ?: "Unnamed"
                        val address = device?.address ?: "Unknown"
                        val deviceClass = device?.bluetoothClass?.deviceClass

                        // val info = "Found: $name [$address] | RSSI: $rssi"
                        // Handler(Looper.getMainLooper()).post {
                        //     Toast.makeText(appContext, info, Toast.LENGTH_SHORT).show()
                        // }

                        if(
                            device != null &&
                            rssi >= -30 &&
                            deviceClass == BluetoothClass.Device.PHONE_SMART &&
                            !foundDevices.contains(device)
                        ){

                            // Handler(Looper.getMainLooper()).post {
                            //     Toast.makeText(appContext, "Found device!", Toast.LENGTH_SHORT).show()
                            // }

                            foundDevices.add(device)
                            bluetoothAdapter.cancelDiscovery()
                            latch.countDown()
                        }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_FOUND)
        }
        context.registerReceiver(receiver, filter)

        if (bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery()
        }

        val started = bluetoothAdapter.startDiscovery()
        if (!started) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(appContext, "startDiscovery() failed", Toast.LENGTH_LONG).show()
            }
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
            return null
        }

        Handler(Looper.getMainLooper()).postDelayed({
            if (latch.count > 0) {
                bluetoothAdapter.cancelDiscovery()
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(appContext, "TIMEOUT", Toast.LENGTH_SHORT).show()
                }
                latch.countDown()
            }
        }, 40_000)

        latch.await()

        try {
            context.unregisterReceiver(receiver)
        } catch (_: Exception) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(appContext, "Receiver already unregistered", Toast.LENGTH_SHORT).show()
            }
        }

        return foundDevices.firstOrNull()
    }

    @Suppress("DiscouragedPrivateApi")
    private fun unpairDevice(device: BluetoothDevice): Boolean {
        return try {
            val method = device.javaClass.getMethod("removeBond")
            method.invoke(device) as Boolean
        } catch (e: Exception) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context.applicationContext, "ERROR UNPAIR", Toast.LENGTH_SHORT).show()
            }
            false
        }
    }


    fun startSession(): Boolean {

        try {

            if (activeDevice != null) return true

            val device = discoverNearbyDeviceBlocking() ?: return false
            activeDevice = device

            return true
        } catch (e: Exception) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context.applicationContext, "startSession() failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
            return false
        }
    }

    fun endSession() {
        activeDevice = null
    }

    /////////////////////////////
    //
    //      USER -> TTP
    //
    /////////////////////////////

    // override fun getGroupDescriptionAndCRS() {
    //     community.getGroupDescriptionAndCRS()
    //     val message =
    //         waitForMessage(CommunityMessageType.GroupDescriptionCRSReplyMessage) as BilinearGroupCRSReplyMessage

    //     participant.group.updateGroupElements(message.groupDescription)
    //     val crs = message.crs.toCRS(participant.group)
    //     participant.crs = crs
    //     messageList.add(message.addressMessage)
    // }

    override fun getGroupDescriptionAndCRS() {
        if (!startSession()) throw Exception("No peer connected")

        try{
            val socket = activeDevice!!.createRfcommSocketToServiceRecord(SERVICE_UUID)
            socket.connect()

            val output = ObjectOutputStream(socket.outputStream)
            val input = ObjectInputStream(socket.inputStream)

            output.writeObject("GET_GROUP_DESCRIPTION_AND_CRS")
            output.flush()

            val groupElementBytes = input.readObject() as BilinearGroupElementsBytes
            val crsBytes = input.readObject() as CRSBytes
            val ttpPublicKeyBytes = input.readObject() as ByteArray

            participant.group.updateGroupElements(groupElementBytes)
            val crs = crsBytes.toCRS(participant.group)
            participant.crs = crs

            messageList.add(
                AddressMessage("TTP", Role.TTP, ttpPublicKeyBytes, community.myPeer.publicKey.keyToBin())
            )

            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context.applicationContext, "getGroupDescriptionAndCRS() SUCCESS", Toast.LENGTH_LONG).show()
            }

            socket.close()

        } catch(e: Exception){
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context.applicationContext, "getGroupDescriptionAndCRS() FAIL: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

    }

    // override fun register(
    //     userName: String,
    //     publicKey: Element,
    //     nameTTP: String
    // ) {
    //     val ttpAddress = addressBookManager.getAddressByName(nameTTP)
    //     community.registerAtTTP(userName, publicKey.toBytes(), ttpAddress.peerPublicKey!!)
    // }

    override fun register(
        userName: String,
        publicKey: Element,
        nameTTP: String
    ) {
        if (!startSession()) throw Exception("No peer connected")

        try{
            val socket = activeDevice!!.createRfcommSocketToServiceRecord(SERVICE_UUID)
            socket.connect()

            val output = ObjectOutputStream(socket.outputStream)
            val input = ObjectInputStream(socket.inputStream)
            
            val ttpAddress = addressBookManager.getAddressByName(nameTTP)
            val ttpPublicKeyBytes = ttpAddress.publicKey.toBytes()

            output.writeObject("REGISTER_AT_TTP")
            output.writeObject(userName)
            output.writeObject(publicKey.toBytes())
            output.writeObject(ttpPublicKeyBytes)
            output.flush()

            val result = input.readObject() as String
            if (result != "SUCCESS") {
                throw Exception("Registration failed: $result")
            }

            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context.applicationContext, "register() SUCCESS", Toast.LENGTH_LONG).show()
            }

            socket.close()

        } catch(e: Exception){
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context.applicationContext, "register() FAIL: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

    }

    /////////////////////////////
    //
    //      USER -> BANK
    //
    /////////////////////////////

    override fun getBlindSignatureRandomness(
        publicKey: Element,
        bankName: String,
        group: BilinearGroup
    ): Element {
        if (!startSession()) throw Exception("No peer connected")

        val socket = activeDevice!!.createRfcommSocketToServiceRecord(SERVICE_UUID)
        socket.connect()

        val output = ObjectOutputStream(socket.outputStream)
        val input = ObjectInputStream(socket.inputStream)

        output.writeObject("BLIND_SIGNATURE_RANDOMNESS_REQUEST")
        output.writeObject(publicKey.toBytes())
        output.flush()

        val randomnessBytes = input.readObject() as ByteArray

        socket.close()

        return group.gElementFromBytes(randomnessBytes)
    }

    override fun requestBlindSignature(
        publicKey: Element,
        bankName: String,
        challenge: BigInteger
    ): BigInteger {
        if (!startSession()) throw Exception("No peer connected")

        val socket = activeDevice!!.createRfcommSocketToServiceRecord(SERVICE_UUID)
        socket.connect()

        val output = ObjectOutputStream(socket.outputStream)
        val input = ObjectInputStream(socket.inputStream)

        output.writeObject("BLIND_SIGNATURE_REQUEST")
        output.writeObject(publicKey.toBytes())
        output.writeObject(challenge)
        output.flush()

        val signature = input.readObject() as BigInteger

        socket.close()

        return signature
    }


    /////////////////////////////
    //
    //      USER -> USER
    //
    /////////////////////////////

    override fun requestTransactionRandomness(
        userNameReceiver: String,
        group: BilinearGroup
    ): RandomizationElements {
    
        if (!startSession()) throw Exception("No peer connected")

        val socket = activeDevice!!.createRfcommSocketToServiceRecord(SERVICE_UUID)
        socket.connect()

        val output = ObjectOutputStream(socket.outputStream)
        val input  = ObjectInputStream(socket.inputStream)

        output.writeObject("TRANSACTION_RANDOMNESS_REQUEST")
        output.writeObject(participant.publicKey.toBytes())
        output.writeObject(this.bankPublicKey.toBytes())
        output.flush()

        val randBytes = input.readObject() as RandomizationElementsBytes
        socket.close()

        return randBytes.toRandomizationElements(group)
    }

    override fun sendTransactionDetails(
        userNameReceiver: String,
        transactionDetails: TransactionDetails
    ): String {

        if (!startSession()) throw Exception("No peer connected")

        val socket = activeDevice!!.createRfcommSocketToServiceRecord(SERVICE_UUID)
        socket.connect()

        val output = ObjectOutputStream(socket.outputStream)
        val input  = ObjectInputStream(socket.inputStream)

        output.writeObject("TRANSACTION_DETAILS")
        output.writeObject(participant.publicKey.toBytes())
        output.writeObject(transactionDetails.toTransactionDetailsBytes())
        output.flush()

        val result = input.readObject() as String
        socket.close()

        return result
    }

    /////////////////////////////
    //
    //      BANK -> TTP
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
        community.scopePeers(participant.name, getParticipantRole(), participant.publicKey.toBytes())
    }

    override fun getPublicKeyOf(
        name: String,
        group: BilinearGroup
    ): Element {
        if (!startSession()) throw Exception("No peer connected")

        val socket = activeDevice!!.createRfcommSocketToServiceRecord(SERVICE_UUID)
        socket.connect()

        val output = ObjectOutputStream(socket.outputStream)
        val input = ObjectInputStream(socket.inputStream)

        output.writeObject("GET_PUBLIC_KEY")
        output.flush()

        val publicKeyBytes = input.readObject() as ByteArray

        socket.close()

        this.bankPublicKey = group.gElementFromBytes(publicKeyBytes)
        return this.bankPublicKey
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
