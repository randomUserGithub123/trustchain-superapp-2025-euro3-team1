package nl.tudelft.trustchain.offlineeuro.communication

import android.app.AlertDialog
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
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
import java.util.concurrent.TimeUnit
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
    private val context: Context,
    override var participant: Participant
) : ICommunicationProtocol {

    private val TAG = "BluetoothProtocol"
    private val DISCOVERY_TIMEOUT_SECONDS = 25L
    private val CONNECTION_TIMEOUT_SECONDS = 8L

    private val messageList = MessageList(this::handleRequestMessage)
    private val bluetoothAdapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter() ?: throw IllegalStateException("Bluetooth not supported")
    private val TTP_SERVICE_UUID: UUID = UUID.fromString("a8a2f32c-5321-4a24-8272-52465e17e39a")
    private val BANK_SERVICE_UUID: UUID = UUID.fromString("b3e65e6b-42f4-4a27-89b5-4b9a91d2d3a3")
    private val USER_SERVICE_UUID: UUID = UUID.fromString("c8d348a6-5e58-4e18-842b-56e6d1e4e6f4")
    private val SERVICE_NAME = "OfflineEuroTransfer"
    private var serverThread: Thread? = null
    private var serverSocket: BluetoothServerSocket? = null
    @Volatile private var running: Boolean = true
    private var activeDevice: BluetoothDevice? = null
    private lateinit var bankPublicKey: Element

    init {
        community.messageList = messageList
        startServer()
    }

    private fun startServer() {
        Log.d(TAG, "startServer: Initializing Bluetooth server...")
        val serviceUuid = when (participant) {
            is TTP -> TTP_SERVICE_UUID
            is Bank -> BANK_SERVICE_UUID
            is User -> USER_SERVICE_UUID
            else -> {
                Log.e(TAG, "startServer: Participant has an unknown role.")
                return
            }
        }
        serverThread = thread(start = true) {
            try {
                serverSocket = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(SERVICE_NAME, serviceUuid)
                Log.d(TAG, "startServer: Server socket listening INSECURELY on UUID $serviceUuid. Waiting for connections...")
                while (running) {
                    try {
                        serverSocket?.accept()?.let { socket ->
                            Log.i(TAG, "startServer: Incoming connection from ${socket.remoteDevice.name} [${socket.remoteDevice.address}]")
                            thread { handleIncomingConnection(socket) }
                        }
                    } catch (e: Exception) {
                        if (running) Log.e(TAG, "startServer: Error while accepting connection", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "startServer: Server thread initialization failed", e)
            } finally {
                Log.d(TAG, "startServer: Bluetooth server thread exiting.")
            }
        }
    }

    private fun handleIncomingConnection(socket: BluetoothSocket) {
        try {
            Log.d(TAG, "handleIncomingConnection: Handling connection from ${socket.remoteDevice.name}")
            val output = ObjectOutputStream(socket.outputStream)
            val input = ObjectInputStream(socket.inputStream)
            val requestType = input.readObject() as String
            Log.d(TAG, "handleIncomingConnection: Received request type: '$requestType'")
            when (requestType) {
                "GET_GROUP_DESCRIPTION_AND_CRS" -> {
                    if (participant !is TTP) throw Exception("Only TTP can provide group description and CRS")
                    val ttp = participant as TTP
                    output.writeObject(ttp.group.toGroupElementBytes())
                    output.writeObject(ttp.crs.toCRSBytes())
                    output.writeObject(ttp.publicKey.toBytes())
                    output.flush()
                }
                "REGISTER_AT_TTP" -> {
                    val userName = input.readObject() as String
                    val publicKeyBytes = input.readObject() as ByteArray
                    input.readObject() as ByteArray // ttpPublicKeyBytes, unused
                    if (participant !is TTP) throw Exception("This device is not a TTP")
                    val ttp = participant as TTP
                    val publicKey = ttp.group.gElementFromBytes(publicKeyBytes)
                    ttp.registerUser(userName, publicKey)
                    output.writeObject("SUCCESS")
                    output.flush()
                }
                "BLIND_SIGNATURE_RANDOMNESS_REQUEST" -> {
                    val publicKeyBytes = input.readObject() as ByteArray
                    if (participant !is Bank) throw Exception("Not a bank")
                    val bank = participant as Bank
                    val publicKey = bank.group.gElementFromBytes(publicKeyBytes)
                    val randomness = bank.getBlindSignatureRandomness(publicKey)
                    output.writeObject(randomness.toBytes())
                    output.flush()
                }
                "BLIND_SIGNATURE_REQUEST" -> {
                    val publicKeyBytes = input.readObject() as ByteArray
                    val challenge = input.readObject() as BigInteger
                    if (participant !is Bank) throw Exception("Not a bank")
                    val bank = participant as Bank
                    val publicKey = bank.group.gElementFromBytes(publicKeyBytes)
                    val signature = bank.createBlindSignature(challenge, publicKey)
                    output.writeObject(signature)
                    output.flush()
                }
                "GET_PUBLIC_KEY" -> {
                    output.writeObject(participant.publicKey.toBytes())
                    output.flush()
                }
                "TRANSACTION_RANDOMNESS_REQUEST" -> {
                    val publicKeyBytes = input.readObject() as ByteArray
                    val bankPublicKeyBytes = input.readObject() as ByteArray
                    if (participant !is User) throw Exception("Not a user")
                    val user = participant as User
                    val publicKey = user.group.gElementFromBytes(publicKeyBytes)
                    this.bankPublicKey = user.group.gElementFromBytes(bankPublicKeyBytes)
                    val randomizationElements = participant.generateRandomizationElements(publicKey)
                    output.writeObject(randomizationElements.toRandomizationElementsBytes())
                    output.flush()
                }
                "TRANSACTION_DETAILS" -> {
                    val publicKeyBytes = input.readObject() as ByteArray
                    val transactionDetailsBytes = input.readObject() as TransactionDetailsBytes
                    val bankPublicKey = if (participant is Bank) {
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
        } catch (e: Exception) {
            if (e.message?.contains("closed") != true && e !is java.io.EOFException) {
                Log.e(TAG, "handleIncomingConnection: Exception during connection", e)
            }
        } finally {
            try { socket.close() } catch (e: Exception) {}
        }
    }

    fun stopServer() {
        running = false
        try {
            serverSocket?.close()
            serverThread?.interrupt()
        } catch (e: Exception) {
            Log.e("BluetoothProtocol", "Error stopping server", e)
        }
    }

    private fun connectWithTimeout(socket: BluetoothSocket) {
        val latch = CountDownLatch(1)
        var connectException: Exception? = null
        thread {
            try {
                socket.connect()
            } catch (e: Exception) {
                connectException = e
            } finally {
                latch.countDown()
            }
        }
        Log.d(TAG, "connectWithTimeout: Attempting to connect, timeout in $CONNECTION_TIMEOUT_SECONDS seconds...")
        if (latch.await(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            connectException?.let {
                Log.e(TAG, "connectWithTimeout: Connection failed explicitly.", it)
                throw it
            }
            Log.i(TAG, "connectWithTimeout: Connection successful.")
        } else {
            Log.e(TAG, "connectWithTimeout: Connection attempt timed out.")
            try { socket.close() } catch (_: Exception) {}
            throw TimeoutException("Connection timed out")
        }
    }

    // In BluetoothCommunicationProtocol.kt

    private fun discoverDeviceWithRoleBlocking(targetRole: Role): BluetoothDevice? {
        Log.d(TAG, "discoverDeviceWithRoleBlocking: Looking for a device with role: $targetRole")
        val latch = CountDownLatch(1)
        var foundDevice: BluetoothDevice? = null
        val targetUuid = when (targetRole) {
            Role.TTP -> TTP_SERVICE_UUID
            Role.Bank -> BANK_SERVICE_UUID
            Role.User -> USER_SERVICE_UUID
        }
        Log.d(TAG, "discoverDeviceWithRoleBlocking: Target Service UUID: $targetUuid")
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (foundDevice != null) return
                val action = intent?.action
                val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }

                when (action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                        if (device != null && device.name != null && rssi > -80) {
                            Log.d(TAG, "onReceive(ACTION_FOUND): Found nearby device '${device.name}'. Fetching services (SDP)...")
                            // This initiates the service discovery
                            device.fetchUuidsWithSdp()
                        }
                    }
                    BluetoothDevice.ACTION_UUID -> {
                        val deviceWithUuid = device ?: return
                        val receivedUuids = deviceWithUuid.uuids

                        // --- START OF CRITICAL DEBUGGING LOGS ---
                        if (receivedUuids != null) {
                            Log.i(TAG, "onReceive(ACTION_UUID): Received UUIDs for device '${deviceWithUuid.name}':")
                            for (uuid in receivedUuids) {
                                Log.i(TAG, "  - Found Service: ${uuid.uuid}")
                            }
                        } else {
                            Log.w(TAG, "onReceive(ACTION_UUID): Received NULL UUIDs for device '${deviceWithUuid.name}'. This often indicates a caching issue.")
                        }
                        // --- END OF CRITICAL DEBUGGING LOGS ---

                        if (receivedUuids?.any { it.uuid == targetUuid } == true) {
                            Log.i(TAG, "onReceive(ACTION_UUID): SUCCESS! Found device '${deviceWithUuid.name}' with target role $targetRole.")
                            foundDevice = deviceWithUuid
                            if (bluetoothAdapter.isDiscovering) {
                                bluetoothAdapter.cancelDiscovery()
                            }
                            latch.countDown()
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        if (foundDevice == null) {
                            Log.d(TAG, "onReceive(ACTION_DISCOVERY_FINISHED): Discovery timed out without finding the target service.")
                        }
                        latch.countDown()
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_UUID)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(receiver, filter)
        if (bluetoothAdapter.isDiscovering) bluetoothAdapter.cancelDiscovery()
        bluetoothAdapter.startDiscovery()
        latch.await(DISCOVERY_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        if (bluetoothAdapter.isDiscovering) bluetoothAdapter.cancelDiscovery()
        try { context.unregisterReceiver(receiver) } catch (e: Exception) {}
        if (foundDevice == null) {
            Log.w(TAG, "discoverDeviceWithRoleBlocking: Could not find any device advertising role $targetRole within the time limit.")
        }
        return foundDevice
    }

    override fun getGroupDescriptionAndCRS() {
        Log.d(TAG, "getGroupDescriptionAndCRS: Discovering TTP with specific service UUID...")
        val targetDevice = discoverDeviceWithRoleBlocking(Role.TTP)
            ?: throw TimeoutException("Could not find a TTP device advertising the correct service.")

        Log.d(TAG, "getGroupDescriptionAndCRS: Found TTP '${targetDevice.name}'. Attempting to connect...")
        var socket: BluetoothSocket? = null
        try {
            socket = targetDevice.createInsecureRfcommSocketToServiceRecord(TTP_SERVICE_UUID)
            connectWithTimeout(socket)

            val output = ObjectOutputStream(socket.outputStream)
            val input = ObjectInputStream(socket.inputStream)
            output.writeObject("GET_GROUP_DESCRIPTION_AND_CRS")
            output.flush()
            val groupElementBytes = input.readObject() as BilinearGroupElementsBytes
            val crsBytes = input.readObject() as CRSBytes
            val ttpPublicKeyBytes = input.readObject() as ByteArray
            participant.group.updateGroupElements(groupElementBytes)
            participant.crs = crsBytes.toCRS(participant.group)
            messageList.add(AddressMessage("TTP", Role.TTP, ttpPublicKeyBytes, community.myPeer.publicKey.keyToBin()))
            Handler(Looper.getMainLooper()).post { Toast.makeText(context.applicationContext, "Received CRS from TTP", Toast.LENGTH_LONG).show() }
        } catch (e: Exception) {
            Log.e(TAG, "getGroupDescriptionAndCRS: FAILED", e)
            Handler(Looper.getMainLooper()).post { Toast.makeText(context.applicationContext, "Connection to TTP failed: ${e.message}", Toast.LENGTH_LONG).show() }
        } finally {
            socket?.close()
        }
    }

    override fun register(userName: String, publicKey: Element, nameTTP: String) {
        val targetDevice = discoverDeviceWithRoleBlocking(Role.TTP)
            ?: throw TimeoutException("Could not find TTP to register with.")
        var socket: BluetoothSocket? = null
        try {
            socket = targetDevice.createInsecureRfcommSocketToServiceRecord(TTP_SERVICE_UUID)
            connectWithTimeout(socket)
            Log.i(TAG, "register: Connection established. Proceeding with registration.")
            val output = ObjectOutputStream(socket.outputStream)
            val input = ObjectInputStream(socket.inputStream)
            val ttpAddress = addressBookManager.getAddressByName(nameTTP)
            output.writeObject("REGISTER_AT_TTP")
            output.writeObject(userName)
            output.writeObject(publicKey.toBytes())
            output.writeObject(ttpAddress.publicKey.toBytes())
            output.flush()
            val result = input.readObject() as String
            if (result != "SUCCESS") throw Exception("Registration failed with response: $result")
            Handler(Looper.getMainLooper()).post { Toast.makeText(context.applicationContext, "Registration SUCCESS", Toast.LENGTH_LONG).show() }
        } catch (e: Exception) {
            Log.e(TAG, "register: Registration FAILED", e)
            Handler(Looper.getMainLooper()).post { Toast.makeText(context.applicationContext, "Registration FAIL: ${e.message}", Toast.LENGTH_LONG).show() }
        } finally {
            socket?.close()
        }
    }

    override fun getBlindSignatureRandomness(publicKey: Element, bankName: String, group: BilinearGroup): Element {
        val targetDevice = discoverDeviceWithRoleBlocking(Role.Bank)
            ?: throw TimeoutException("Could not find Bank to get randomness from.")
        var socket: BluetoothSocket? = null
        try {
            socket = targetDevice.createInsecureRfcommSocketToServiceRecord(BANK_SERVICE_UUID)
            connectWithTimeout(socket)
            val output = ObjectOutputStream(socket.outputStream)
            val input = ObjectInputStream(socket.inputStream)
            output.writeObject("BLIND_SIGNATURE_RANDOMNESS_REQUEST")
            output.writeObject(publicKey.toBytes())
            output.flush()
            return group.gElementFromBytes(input.readObject() as ByteArray)
        } finally {
            socket?.close()
        }
    }

    override fun requestBlindSignature(publicKey: Element, bankName: String, challenge: BigInteger): BigInteger {
        val targetDevice = discoverDeviceWithRoleBlocking(Role.Bank)
            ?: throw TimeoutException("Could not find Bank to get signature from.")
        var socket: BluetoothSocket? = null
        try {
            socket = targetDevice.createInsecureRfcommSocketToServiceRecord(BANK_SERVICE_UUID)
            connectWithTimeout(socket)
            val output = ObjectOutputStream(socket.outputStream)
            val input = ObjectInputStream(socket.inputStream)
            output.writeObject("BLIND_SIGNATURE_REQUEST")
            output.writeObject(publicKey.toBytes())
            output.writeObject(challenge)
            output.flush()
            return input.readObject() as BigInteger
        } finally {
            socket?.close()
        }
    }

    private fun discoverAndSelectUser(): BluetoothDevice? {
        Log.d(TAG, "discoverAndSelectUser: Starting discovery to find nearby users.")
        val foundDevices = mutableListOf<BluetoothDevice>()
        val discoveryLatch = CountDownLatch(1)
        val selectionLatch = CountDownLatch(1)
        var selectedDevice: BluetoothDevice? = null

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        // Simple filter for phones with a name
                        if (device != null && device.name != null &&
                            device.bluetoothClass?.deviceClass == BluetoothClass.Device.PHONE_SMART &&
                            foundDevices.none { it.address == device.address }) {
                            Log.i(TAG, "discoverAndSelectUser: Found potential user: ${device.name}")
                            foundDevices.add(device)
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        Log.d(TAG, "discoverAndSelectUser: Discovery finished.")
                        discoveryLatch.countDown()
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(receiver, filter)
        if (bluetoothAdapter.isDiscovering) bluetoothAdapter.cancelDiscovery()
        bluetoothAdapter.startDiscovery()

        // Wait for discovery to finish
        discoveryLatch.await(DISCOVERY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (bluetoothAdapter.isDiscovering) bluetoothAdapter.cancelDiscovery()
        try { context.unregisterReceiver(receiver) } catch (e: Exception) {}

        // --- Now, prompt the user on the main thread ---
        Handler(Looper.getMainLooper()).post {
            if (foundDevices.isEmpty()) {
                Toast.makeText(context, "No nearby users found", Toast.LENGTH_SHORT).show()
                selectionLatch.countDown()
                return@post
            }

            val deviceNames = foundDevices.map { it.name ?: "Unnamed Device" }.toTypedArray()
            AlertDialog.Builder(context)
                .setTitle("Choose a User to Pay")
                .setItems(deviceNames) { _, which ->
                    selectedDevice = foundDevices[which]
                    selectionLatch.countDown()
                }
                .setOnCancelListener { selectionLatch.countDown() }
                .setNegativeButton("Cancel") { _, _ -> selectionLatch.countDown() }
                .create()
                .show()
        }

        selectionLatch.await() // Wait for the user to make a choice
        return selectedDevice
    }


    fun startSession(): Boolean {
        return try {
            if (activeDevice != null) {
                Log.d(TAG, "startSession: Session already active with ${activeDevice?.name}")
                return true
            }
            // Use the new, direct discovery and selection method
            val selectedDevice = discoverAndSelectUser() ?: run {
                Log.e(TAG, "startSession: No user was selected or found.")
                return false
            }
            activeDevice = selectedDevice
            Log.i(TAG, "startSession: Session started with ${activeDevice?.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "startSession failed", e)
            false
        }
    }

    fun endSession() {
        activeDevice = null
    }

    override fun requestTransactionRandomness(userNameReceiver: String, group: BilinearGroup): RandomizationElements {
        if (!startSession()) {
            throw TimeoutException("Could not start a session with another User.")
        }
        var socket: BluetoothSocket? = null
        try {
            socket = activeDevice!!.createInsecureRfcommSocketToServiceRecord(USER_SERVICE_UUID)
            connectWithTimeout(socket)
            val output = ObjectOutputStream(socket.outputStream)
            val input = ObjectInputStream(socket.inputStream)
            output.writeObject("TRANSACTION_RANDOMNESS_REQUEST")
            output.writeObject(participant.publicKey.toBytes())
            output.writeObject(this.bankPublicKey.toBytes())
            output.flush()
            val randBytes = input.readObject() as RandomizationElementsBytes
            return randBytes.toRandomizationElements(group)
        } finally {
            socket?.close()
        }
    }

    override fun sendTransactionDetails(userNameReceiver: String, transactionDetails: TransactionDetails): String {
        if (activeDevice == null) {
            throw IllegalStateException("No active device session for sending transaction details.")
        }
        var socket: BluetoothSocket? = null
        try {
            socket = activeDevice!!.createInsecureRfcommSocketToServiceRecord(USER_SERVICE_UUID)
            connectWithTimeout(socket)
            val output = ObjectOutputStream(socket.outputStream)
            val input = ObjectInputStream(socket.inputStream)
            output.writeObject("TRANSACTION_DETAILS")
            output.writeObject(participant.publicKey.toBytes())
            output.writeObject(transactionDetails.toTransactionDetailsBytes())
            output.flush()
            return input.readObject() as String
        } finally {
            socket?.close()
        }
    }

    //<editor-fold desc="Boilerplate and Unchanged Methods">
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

    override fun getPublicKeyOf(name: String, group: BilinearGroup): Element {
        if (!startSession()) throw Exception("No peer connected")
        var socket: BluetoothSocket? = null
        try {
            // Note: This SERVICE_UUID is generic. Assumes connected peer will respond.
            socket = activeDevice!!.createInsecureRfcommSocketToServiceRecord(USER_SERVICE_UUID)
            connectWithTimeout(socket)
            val output = ObjectOutputStream(socket.outputStream)
            val input = ObjectInputStream(socket.inputStream)
            output.writeObject("GET_PUBLIC_KEY")
            output.flush()
            val publicKeyBytes = input.readObject() as ByteArray
            this.bankPublicKey = group.gElementFromBytes(publicKeyBytes)
            return this.bankPublicKey
        } finally {
            socket?.close()
        }
    }

    private fun waitForMessage(messageType: CommunityMessageType): ICommunityMessage {
        var loops = 0
        val timeOutInMS = 10000
        val sleepDuration: Long = 100
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
        if (participant !is TTP) return
        val groupBytes = participant.group.toGroupElementBytes()
        val crsBytes = (participant as TTP).crs.toCRSBytes()
        val peer = message.requestingPeer
        community.sendGroupDescriptionAndCRS(groupBytes, crsBytes, participant.publicKey.toBytes(), peer)
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
        val bankPublicKey = if (participant is Bank) {
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
        if (participant !is TTP) return
        val ttp = participant as TTP
        val publicKey = ttp.group.gElementFromBytes(message.userPKBytes)
        ttp.registerUser(message.userName, publicKey)
    }

    private fun handleAddressRequestMessage(message: AddressRequestMessage) {
        val role = getParticipantRole()
        community.sendAddressReply(participant.name, role, participant.publicKey.toBytes(), message.requestingPeer)
    }

    private fun handleFraudControlRequestMessage(message: FraudControlRequestMessage) {
        if (getParticipantRole() != Role.TTP) return
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
    }

    private fun getParticipantRole(): Role {
        return when (participant) {
            is User -> Role.User
            is TTP -> Role.TTP
            is Bank -> Role.Bank
            else -> throw Exception("Unknown role")
        }
    }
    //</editor-fold>
}
