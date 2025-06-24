package nl.tudelft.trustchain.offlineeuro.communication

import android.Manifest
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
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
import nl.tudelft.trustchain.offlineeuro.community.message.CommunityMessageType
import nl.tudelft.trustchain.offlineeuro.community.message.FraudControlReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.FraudControlRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.ICommunityMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TTPRegistrationMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionRandomizationElementsRequestMessage
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.RandomizationElements
import nl.tudelft.trustchain.offlineeuro.cryptography.RandomizationElementsBytes
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahaiProof
import nl.tudelft.trustchain.offlineeuro.cryptography.BloomFilter
import nl.tudelft.trustchain.offlineeuro.libraries.GrothSahaiSerializer
import androidx.core.app.ActivityCompat
import java.io.IOException

class BluetoothCommunicationProtocol(
    val addressBookManager: AddressBookManager,
    val community: OfflineEuroCommunity,
    private val context: Context
) : ICommunicationProtocol {
    val messageList = MessageList(this::handleRequestMessage)

    private val sleepDuration: Long = 100
    private val timeOutInMS = 20000

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

    private fun checkBluetoothPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            return ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun startServer() {
        if (!checkBluetoothPermissions()) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context.applicationContext, "Bluetooth permissions are required", Toast.LENGTH_LONG).show()
            }
            return
        }

        serverThread =
            thread(start = true) {
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
                } catch (e: SecurityException) {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context.applicationContext, "Bluetooth permission denied: ${e.message}", Toast.LENGTH_LONG).show()
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

                    "BLOOM_FILTER_REQUEST" -> {
                        handleBloomFilterRequest(output)
                    }

                    "BLOOM_FILTER_REPLY" -> {
                        val bloomFilterBytes = input.readObject() as ByteArray
                        val expectedElements = input.readObject() as Int
                        val falsePositiveRate = input.readObject() as Double
                        handleBloomFilterReply(bloomFilterBytes, expectedElements, falsePositiveRate)
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
            Log.w("BluetoothDiscovery", "Bluetooth is not enabled.")
            return null
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
        val locationEnabled = locationManager?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true
        if (!locationEnabled) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(appContext, "Enable location services", Toast.LENGTH_LONG).show()
            }
            Log.w("BluetoothDiscovery", "Location services are not enabled.")
            return null
        }

        if (!checkBluetoothPermissions()) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(appContext, "Bluetooth permissions are required", Toast.LENGTH_LONG).show()
            }
            Log.e("BluetoothDiscovery", "Missing required Bluetooth permissions.")
            return null
        }

        // Unpairing existing smart phones can help with fresh discovery sessions
        try {
            Log.i("BluetoothDiscovery", "Checking bonded devices to unpair existing phones...")
            val bondedDevices = bluetoothAdapter.bondedDevices
            for (device in bondedDevices) {
                val deviceClass = device.bluetoothClass?.deviceClass
                if (deviceClass == BluetoothClass.Device.PHONE_SMART) {
                    unpairDevice(device)
                    Log.i("BluetoothDiscovery", "Unpaired previously bonded phone: ${device.name} [${device.address}]")
                }
            }
        } catch (e: SecurityException) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(appContext, "Bluetooth permission denied: ${e.message}", Toast.LENGTH_LONG).show()
            }
            Log.e("BluetoothDiscovery", "SecurityException while unpairing devices.", e)
            return null
        }

        Handler(Looper.getMainLooper()).post {
            Toast.makeText(appContext, "Starting Bluetooth discovery...", Toast.LENGTH_SHORT).show()
        }
        Log.i("BluetoothDiscovery", "Setting up BroadcastReceiver and starting discovery.")

        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context?,
                    intent: Intent?
                ) {
                    val action = intent?.action

                    when (action) {
                        BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                            Log.i("BluetoothDiscovery", "Discovery process has started.")
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(appContext, "Discovery started", Toast.LENGTH_SHORT).show()
                            }
                        }

                        BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                            Log.i("BluetoothDiscovery", "Discovery process has finished.")
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(appContext, "Discovery finished", Toast.LENGTH_SHORT).show()
                            }
                            latch.countDown()
                        }

                        BluetoothDevice.ACTION_FOUND -> {
                            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                            val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)

                            // --- Start of Detailed Logging ---
                            if (device == null) {
                                Log.w("BluetoothDiscovery", "Found a device but it was null in the intent.")
                                return
                            }

                            var deviceName = "Unknown Name"
                            var deviceAddress = "Unknown Address"
                            var deviceClass = "Unknown Class"
                            try {
                                if (checkBluetoothPermissions()) {
                                    deviceName = device.name ?: "Unnamed"
                                    deviceAddress = device.address
                                    deviceClass = when(device.bluetoothClass?.deviceClass) {
                                        BluetoothClass.Device.PHONE_SMART -> "PHONE_SMART"
                                        BluetoothClass.Device.COMPUTER_LAPTOP -> "COMPUTER_LAPTOP"
                                        else -> "OTHER (${device.bluetoothClass?.deviceClass})"
                                    }
                                }
                            } catch (e: SecurityException) {
                                Log.e("BluetoothDiscovery", "Permission error getting device details.", e)
                            }

                            Log.d("BluetoothDiscovery", "--- Device Found: '$deviceName' [$deviceAddress], RSSI: $rssi, Class: $deviceClass ---")

                            // Filtering Logic with Logs
                            if (rssi < -60) {
                                Log.d("BluetoothDiscovery", "REJECTED '$deviceName': Signal too weak (RSSI: $rssi).")
                                return
                            }
                            if (device.bluetoothClass?.deviceClass != BluetoothClass.Device.PHONE_SMART) {
                                Log.d("BluetoothDiscovery", "REJECTED '$deviceName': Not a smart phone (Class: $deviceClass).")
                                return
                            }
                            if (foundDevices.contains(device)) {
                                Log.d("BluetoothDiscovery", "REJECTED '$deviceName': Already in the list of candidates.")
                                return
                            }

                            // If all checks pass
                            Log.i("BluetoothDiscovery", "ACCEPTED '$deviceName' as a candidate. Stopping discovery.")
                            // --- End of Detailed Logging ---

                            foundDevices.add(device)
                            try {
                                if (checkBluetoothPermissions()) {
                                    bluetoothAdapter.cancelDiscovery()
                                }
                            } catch (e: SecurityException) {
                                Log.e("BluetoothDiscovery", "Failed to cancel discovery after finding a device.", e)
                            }
                            latch.countDown()
                        }
                    }
                }
            }

        val filter =
            IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                addAction(BluetoothDevice.ACTION_FOUND)
            }
        context.registerReceiver(receiver, filter)

        try {
            if (checkBluetoothPermissions()) {
                if (bluetoothAdapter.isDiscovering) {
                    Log.w("BluetoothDiscovery", "Discovery was already running. Cancelling before starting new one.")
                    bluetoothAdapter.cancelDiscovery()
                }
                val started = bluetoothAdapter.startDiscovery()
                if (started) {
                    Log.i("BluetoothDiscovery", "startDiscovery() returned true. Waiting for results.")
                } else {
                    Log.e("BluetoothDiscovery", "startDiscovery() returned false. Aborting.")
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(appContext, "startDiscovery() failed", Toast.LENGTH_LONG).show()
                    }
                    try {
                        context.unregisterReceiver(receiver)
                    } catch (_: Exception) {
                    }
                    return null
                }
            }
        } catch (e: SecurityException) {
            Log.e("BluetoothDiscovery", "SecurityException on starting discovery.", e)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(appContext, "Bluetooth permission denied: ${e.message}", Toast.LENGTH_LONG).show()
            }
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
            return null
        }

        // Timeout logic
        Handler(Looper.getMainLooper()).postDelayed({
            if (latch.count > 0) {
                try {
                    if (checkBluetoothPermissions()) {
                        if (bluetoothAdapter.isDiscovering) {
                            Log.w("BluetoothDiscovery", "Discovery timed out after 40 seconds. Cancelling.")
                            bluetoothAdapter.cancelDiscovery()
                        }
                    }
                } catch (e: SecurityException) {
                    Log.e("BluetoothDiscovery", "Failed to cancel discovery on timeout.", e)
                }
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(appContext, "TIMEOUT", Toast.LENGTH_SHORT).show()
                }
                latch.countDown() // Release the main thread
            }
        }, 40_000)

        Log.i("BluetoothDiscovery", "Waiting on latch...")
        latch.await()
        Log.i("BluetoothDiscovery", "Latch released. Unregistering receiver.")

        try {
            context.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            Log.w("BluetoothDiscovery", "Receiver was already unregistered. This is okay.", e)
        }

        if (foundDevices.isEmpty()){
            Log.w("BluetoothDiscovery", "No suitable devices were found.")
        } else {
            Log.i("BluetoothDiscovery", "Returning first suitable device: ${foundDevices.firstOrNull()?.name}")
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

    // ///////////////////////////
    //
    //      USER -> TTP
    //
    // ///////////////////////////

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

    // ///////////////////////////
    //
    //      USER -> BANK
    //
    // ///////////////////////////

    override fun getBlindSignatureRandomness(
        publicKey: Element,
        bankName: String,
        group: BilinearGroup
    ): Element {
        if (!startSession()) throw Exception("No peer connected")

        val socket = createSocket() ?: throw Exception("Failed to create socket")
        try {
            socket.connect()
            val output = ObjectOutputStream(socket.outputStream)
            val input = ObjectInputStream(socket.inputStream)

            output.writeObject("BLIND_SIGNATURE_RANDOMNESS_REQUEST")
            output.writeObject(publicKey.toBytes())
            output.flush()

            val randomnessBytes = input.readObject() as ByteArray
            return group.gElementFromBytes(randomnessBytes)
        } catch (e: Exception) {
            // Log the exception and rethrow a more specific one
            Log.e("BluetoothProtocol", "getBlindSignatureRandomness failed", e)
            throw Exception("Failed to get blind signature randomness: ${e.message}")
        } finally {
            try {
                socket.close()
            } catch (e: IOException) {
                Log.e("BluetoothProtocol", "Error closing socket in getBlindSignatureRandomness", e)
            }
        }
    }

    override fun requestBlindSignature(
        publicKey: Element,
        bankName: String,
        challenge: BigInteger
    ): BigInteger {
        if (!startSession()) throw Exception("No peer connected")

        val socket = createSocket() ?: throw Exception("Failed to create socket")
        try {
            socket.connect()
            val output = ObjectOutputStream(socket.outputStream)
            val input = ObjectInputStream(socket.inputStream)

            output.writeObject("BLIND_SIGNATURE_REQUEST")
            output.writeObject(publicKey.toBytes())
            output.writeObject(challenge)
            output.flush()

            return input.readObject() as BigInteger
        } catch (e: Exception) {
            Log.e("BluetoothProtocol", "requestBlindSignature failed", e)
            throw Exception("Failed to request blind signature: ${e.message}")
        } finally {
            try {
                socket.close()
            } catch (e: IOException) {
                Log.e("BluetoothProtocol", "Error closing socket in requestBlindSignature", e)
            }
        }
    }

    // ///////////////////////////
    //
    //      USER -> USER
    //
    // ///////////////////////////

    override fun requestTransactionRandomness(
        userNameReceiver: String,
        group: BilinearGroup
    ): RandomizationElements {
        if (!startSession()) throw Exception("No peer connected")

        val socket = createSocket() ?: throw Exception("Failed to create socket")
        try {
            socket.connect()
            val output = ObjectOutputStream(socket.outputStream)
            val input = ObjectInputStream(socket.inputStream)

            output.writeObject("TRANSACTION_RANDOMNESS_REQUEST")
            output.writeObject(participant.publicKey.toBytes())
            output.writeObject(this.bankPublicKey.toBytes())
            output.flush()

            val randBytes = input.readObject() as RandomizationElementsBytes
            return randBytes.toRandomizationElements(group)
        } catch (e: Exception) {
            Log.e("BluetoothProtocol", "requestTransactionRandomness failed", e)
            throw Exception("Failed to request transaction randomness: ${e.message}")
        } finally {
            try {
                socket.close()
            } catch (e: IOException) {
                Log.e("BluetoothProtocol", "Error closing socket in requestTransactionRandomness", e)
            }
        }
    }

    override fun sendTransactionDetails(
        userNameReceiver: String,
        transactionDetails: TransactionDetails
    ): String {
        if (!startSession()) throw Exception("No peer connected")

        val socket = createSocket() ?: throw Exception("Failed to create socket")
        try {
            socket.connect()
            val output = ObjectOutputStream(socket.outputStream)
            val input = ObjectInputStream(socket.inputStream)

            output.writeObject("TRANSACTION_DETAILS")
            output.writeObject(participant.publicKey.toBytes())
            output.writeObject(transactionDetails.toTransactionDetailsBytes())
            output.flush()

            return input.readObject() as String
        } catch (e: Exception) {
            Log.e("BluetoothProtocol", "sendTransactionDetails failed", e)
            throw Exception("Failed to send transaction details: ${e.message}")
        } finally {
            try {
                socket.close()
            } catch (e: IOException) {
                Log.e("BluetoothProtocol", "Error closing socket in sendTransactionDetails", e)
            }
        }
    }

    // ///////////////////////////
    //
    //      BANK -> TTP
    //
    // ///////////////////////////

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

        val socket = createSocket() ?: throw Exception("Failed to create socket")
        try {
            socket.connect()
            val output = ObjectOutputStream(socket.outputStream)
            val input = ObjectInputStream(socket.inputStream)

            output.writeObject("GET_PUBLIC_KEY")
            output.flush()

            val publicKeyBytes = input.readObject() as ByteArray
            this.bankPublicKey = group.gElementFromBytes(publicKeyBytes)
            return this.bankPublicKey
        } catch (e: Exception) {
            Log.e("BluetoothProtocol", "getPublicKeyOf failed", e)
            throw Exception("Failed to get public key of '$name': ${e.message}")
        } finally {
            try {
                socket.close()
            } catch (e: IOException) {
                Log.e("BluetoothProtocol", "Error closing socket in getPublicKeyOf", e)
            }
        }
    }

    override fun requestBloomFilter(participantName: String): BloomFilter {
        if (!startSession()) throw Exception("No peer connected")

        val socket = createSocket() ?: throw Exception("Failed to create socket")
        try {
            socket.connect()
            val output = ObjectOutputStream(socket.outputStream)
            val input = ObjectInputStream(socket.inputStream)

            output.writeObject("BLOOM_FILTER_REQUEST")
            output.flush()

            val replyType = input.readObject() as String
            if (replyType != "BLOOM_FILTER_REPLY") {
                throw Exception("Unexpected reply type: $replyType")
            }

            val bloomFilterBytes = input.readObject() as ByteArray
            val expectedElements = input.readObject() as Int
            val falsePositiveRate = input.readObject() as Double

            return BloomFilter.fromBytes(bloomFilterBytes, expectedElements, falsePositiveRate)
        } catch (e: Exception) {
            Log.e("BluetoothProtocol", "requestBloomFilter failed", e)
            throw Exception("Failed to request bloom filter: ${e.message}")
        } finally {
            try {
                socket.close()
            } catch (e: IOException) {
                Log.e("BluetoothProtocol", "Error closing socket in requestBloomFilter", e)
            }
        }
    }

    override fun sendBloomFilter(
        participantName: String,
        bloomFilter: BloomFilter
    ) {
        if (!startSession()) throw Exception("No peer connected")

        val socket = createSocket() ?: throw Exception("Failed to create socket")
        try {
            socket.connect()
            val output = ObjectOutputStream(socket.outputStream)

            // Note: sendBloomFilter doesn't seem to expect a reply from the other side.
            // If it did, you would need an ObjectInputStream as well.
            output.writeObject("BLOOM_FILTER_REPLY")
            output.writeObject(bloomFilter.toBytes())
            output.writeObject(bloomFilter.expectedElements)
            output.writeObject(bloomFilter.falsePositiveRate)
            output.flush()
        } catch (e: Exception) {
            Log.e("BluetoothProtocol", "sendBloomFilter failed", e)
            throw Exception("Failed to send bloom filter: ${e.message}")
        } finally {
            try {
                socket.close()
            } catch (e: IOException) {
                Log.e("BluetoothProtocol", "Error closing socket in sendBloomFilter", e)
            }
        }
    }

    private fun handleBloomFilterRequest(output: ObjectOutputStream) {
        if (participant is User || participant is Bank || participant is TTP) {
            val bloomFilter =
                when (participant) {
                    is User -> (participant as User).getBloomFilter()
                    is Bank -> (participant as Bank).getBloomFilter()
                    is TTP -> (participant as TTP).getBloomFilter()
                    else -> throw Exception("Unsupported participant type")
                }
            output.writeObject("BLOOM_FILTER_REPLY")
            output.writeObject(bloomFilter.toBytes())
            output.writeObject(bloomFilter.expectedElements)
            output.writeObject(bloomFilter.falsePositiveRate)
            output.flush()
        }
    }

    private fun handleBloomFilterReply(
        bloomFilterBytes: ByteArray,
        expectedElements: Int,
        falsePositiveRate: Double
    ) {
        val bloomFilter = BloomFilter.fromBytes(bloomFilterBytes, expectedElements, falsePositiveRate)
        when (participant) {
            is User -> (participant as User).updateBloomFilter(bloomFilter)
            is Bank -> (participant as Bank).updateBloomFilter(bloomFilter)
            is TTP -> (participant as TTP).updateBloomFilter(bloomFilter)
            else -> throw Exception("Unsupported participant type")
        }
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

    private fun createSocket(): BluetoothSocket? {
        if (!checkBluetoothPermissions()) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context.applicationContext, "Bluetooth permissions are required", Toast.LENGTH_LONG).show()
            }
            return null
        }

        if (activeDevice == null) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context.applicationContext, "No active device selected for connection.", Toast.LENGTH_SHORT).show()
            }
            return null
        }

        return try {
            activeDevice!!.createRfcommSocketToServiceRecord(SERVICE_UUID)
        } catch (e: SecurityException) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context.applicationContext, "Bluetooth permission denied: ${e.message}", Toast.LENGTH_LONG).show()
            }
            null
        } catch (e: Exception) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context.applicationContext, "Failed to create socket: ${e.message}", Toast.LENGTH_LONG).show()
            }
            null
        }
    }
}
