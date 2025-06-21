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
    private val context: Context,
    override var participant: Participant
) : ICommunicationProtocol {

    private val TAG = "BluetoothProtocol"

    val messageList = MessageList(this::handleRequestMessage)

    private val sleepDuration: Long = 100
    private val timeOutInMS = 10000

    private val bluetoothAdapter: BluetoothAdapter =
        BluetoothAdapter.getDefaultAdapter() ?: throw IllegalStateException("Bluetooth not supported")

    private val SERVICE_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val TTP_SERVICE_UUID: UUID = UUID.fromString("a8a2f32c-5321-4a24-8272-52465e17e39a")
    private val BANK_SERVICE_UUID: UUID = UUID.fromString("b3e65e6b-42f4-4a27-89b5-4b9a91d2d3a3")
    private val USER_SERVICE_UUID: UUID = UUID.fromString("c8d348a6-5e58-4e18-842b-56e6d1e4e6f4")


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
        Log.d(TAG, "startServer: Initializing Bluetooth server...")

        // Determine which UUID to use based on the participant's role
        val serviceUuid = when (participant) {
            is TTP -> {
                Log.d(TAG, "startServer: Participant is a TTP, using TTP_SERVICE_UUID")
                TTP_SERVICE_UUID
            }
            is Bank -> {
                Log.d(TAG, "startServer: Participant is a Bank, using BANK_SERVICE_UUID")
                BANK_SERVICE_UUID
            }
            is User -> {
                Log.d(TAG, "startServer: Participant is a User, using USER_SERVICE_UUID")
                USER_SERVICE_UUID
            }
            else -> {
                Log.e(TAG, "startServer: Participant has an unknown role. Cannot start server.")
                return // Or throw an exception
            }
        }

        serverThread = thread(start = true) {
            try {
                // Use the determined role-specific UUID here
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, serviceUuid)
                Log.d(TAG, "startServer: Server socket listening on UUID $serviceUuid. Waiting for connections...")

                while (running) {
                    try {
                        val socket = serverSocket?.accept()
                        if (socket != null) {
                            Log.i(TAG, "startServer: Incoming connection from ${socket.remoteDevice.name} [${socket.remoteDevice.address}]")
                            thread { handleIncomingConnection(socket) }
                        }
                    } catch (e: Exception) {
                        if (running) {
                            Log.e(TAG, "startServer: Error while accepting connection", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "startServer: Server thread initialization failed", e)
            } finally {
                Log.d(TAG, "startServer: Bluetooth server thread exiting.")
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
                Log.d(TAG, "handleIncomingConnection: Handling connection from ${socket.remoteDevice.name} [${socket.remoteDevice.address}]")
                // It's crucial to initialize the ObjectOutputStream BEFORE the ObjectInputStream to avoid a deadlock.
                val output = ObjectOutputStream(socket.outputStream)
                val input = ObjectInputStream(socket.inputStream)

                val requestType = input.readObject() as String
                Log.d(TAG, "handleIncomingConnection: Received request type: '$requestType'")

                when (requestType) {

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
                        Log.d(TAG, "handleIncomingConnection: Handling REGISTER_AT_TTP request.")

                        val userName = input.readObject() as String
                        val publicKeyBytes = input.readObject() as ByteArray
                        val ttpPublicKeyBytes = input.readObject() as ByteArray
                        Log.d(TAG, "handleIncomingConnection: Received registration for user '$userName', pk size: ${publicKeyBytes.size}, ttp pk size: ${ttpPublicKeyBytes.size}")

                        if (participant !is TTP) {
                            val errorMsg = "This device is not a TTP. It is a ${participant::class.simpleName}."
                            Log.e(TAG, "handleIncomingConnection: $errorMsg")
                            output.writeObject("ERROR: $errorMsg")
                            output.flush()
                            throw Exception(errorMsg)
                        }

                        val ttp = participant as TTP
                        val publicKey = ttp.group.gElementFromBytes(publicKeyBytes)

                        Log.d(TAG, "handleIncomingConnection: Calling ttp.registerUser...")
                        ttp.registerUser(userName, publicKey)
                        Log.i(TAG, "handleIncomingConnection: Successfully registered user '$userName'.")

                        Log.d(TAG, "handleIncomingConnection: Sending 'SUCCESS' response.")
                        output.writeObject("SUCCESS")
                        output.flush()
                        Log.d(TAG, "handleIncomingConnection: 'SUCCESS' response sent.")
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

                socket.close()
                Log.d(TAG, "handleIncomingConnection: Socket closed for ${socket.remoteDevice.address}")

            } catch (e: Exception) {
                if (e.message?.contains("closed") == true || e is java.io.EOFException) {
                    Log.i(TAG, "handleIncomingConnection: Socket closed normally by the other device.")
                } else {
                    Log.e(TAG, "handleIncomingConnection: Exception during connection", e)
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context.applicationContext, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun discoverDeviceWithRoleBlocking(targetRole: Role): BluetoothDevice? {
        Log.d(TAG, "discoverDeviceWithRoleBlocking: Looking for a device with role: $targetRole")
        val latch = CountDownLatch(1)
        var foundDevice: BluetoothDevice? = null
        val appContext = context.applicationContext

        // 1. Get the target UUID for the role we are looking for
        val targetUuid = when (targetRole) {
            Role.TTP -> TTP_SERVICE_UUID
            Role.Bank -> BANK_SERVICE_UUID
            Role.User -> USER_SERVICE_UUID
        }
        Log.d(TAG, "discoverDeviceWithRoleBlocking: Target Service UUID: $targetUuid")

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // FIX: Use the safe call operator '?.' because 'intent' can be null.
                val action = intent?.action
                val deviceFromIntent: BluetoothDevice? = intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

                when (action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = deviceFromIntent ?: return

                        // FIX: Also apply the safe call here and provide a default value if the intent is null.
                        // Using '?: return' ensures we don't proceed with a null RSSI value.
                        val rssi = intent?.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE) ?: return

                        Log.d(TAG, "onReceive: ACTION_FOUND: ${device.name} [${device.address}] | RSSI: $rssi")

                        if (rssi >= -100) {
                            Log.d(TAG, "onReceive: Device is close enough. Fetching its services (SDP query)...")
                            device.fetchUuidsWithSdp()
                        }
                    }

                    BluetoothDevice.ACTION_UUID -> {
                        val device = deviceFromIntent ?: return
                        val uuids = device.uuids
                        Log.d(TAG, "onReceive: ACTION_UUID for ${device.name}. Found UUIDs: ${uuids?.joinToString()}")

                        if (uuids?.any { it.uuid == targetUuid } == true) {
                            Log.i(TAG, "onReceive: SUCCESS! Found device ${device.name} with target role $targetRole.")
                            foundDevice = device
                            bluetoothAdapter.cancelDiscovery()
                            latch.countDown()
                        }
                    }

                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        Log.i(TAG, "onReceive: ACTION_DISCOVERY_FINISHED. Releasing latch if not already done.")
                        // latch.countDown()
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

        if (bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery()
        }

        Log.d(TAG, "discoverDeviceWithRoleBlocking: Starting discovery...")
        bluetoothAdapter.startDiscovery()

        // Wait for a result or timeout
        latch.await(30, java.util.concurrent.TimeUnit.SECONDS)

        // Cleanup
        bluetoothAdapter.cancelDiscovery()
        context.unregisterReceiver(receiver)

        if (foundDevice == null) {
            Log.w(TAG, "discoverDeviceWithRoleBlocking: Could not find any device with role $targetRole.")
        }

        return foundDevice
    }

    private fun discoverNearbyDeviceBlocking(): BluetoothDevice? {
        Log.d(TAG, "discoverNearbyDeviceBlocking: Starting device discovery process...")
        val foundDevices = mutableListOf<BluetoothDevice>()
        val latch = CountDownLatch(1)
        val appContext = context.applicationContext

        if (!bluetoothAdapter.isEnabled) {
            Log.e(TAG, "discoverNearbyDeviceBlocking: Bluetooth is not enabled.")
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(appContext, "Enable Bluetooth first", Toast.LENGTH_LONG).show()
            }
            return null
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
        val locationEnabled = locationManager?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true
        if (!locationEnabled) {
            Log.e(TAG, "discoverNearbyDeviceBlocking: Location services are not enabled.")
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(appContext, "Enable location services for Bluetooth discovery", Toast.LENGTH_LONG).show()
            }
            return null
        }

        Log.d(TAG, "discoverNearbyDeviceBlocking: Unpairing previously bonded phones to ensure fresh discovery.")
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
                when (action) {
                    BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                        Log.i(TAG, "onReceive: ACTION_DISCOVERY_STARTED")
                    }

                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        Log.i(TAG, "onReceive: ACTION_DISCOVERY_FINISHED")
                        latch.countDown()
                    }

                    BluetoothDevice.ACTION_FOUND -> {
                        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                        val name = device?.name ?: "Unnamed"
                        val address = device?.address ?: "Unknown"
                        val deviceClass = device?.bluetoothClass?.deviceClass

                        Log.d(TAG, "onReceive: ACTION_FOUND: $name [$address] | RSSI: $rssi | Class: $deviceClass")

                        if (device != null && !foundDevices.contains(device)) {
                            val isPhone = deviceClass == BluetoothClass.Device.PHONE_SMART
                            val isCloseEnough = rssi >= -65

                            Log.d(TAG, "onReceive: Checking device '$name'. Is phone? $isPhone. Is close enough? $isCloseEnough (RSSI: $rssi)")

                            if (isPhone && isCloseEnough) {
                                Log.i(TAG, "onReceive: Found suitable target device: $name. Stopping discovery.")
                                foundDevices.add(device)
                                bluetoothAdapter.cancelDiscovery()
                                latch.countDown()
                            }
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
            Log.d(TAG, "discoverNearbyDeviceBlocking: Already discovering, cancelling previous one.")
            bluetoothAdapter.cancelDiscovery()
        }

        if (!bluetoothAdapter.startDiscovery()) {
            Log.e(TAG, "discoverNearbyDeviceBlocking: startDiscovery() returned false. Discovery could not be started.")
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
            return null
        }
        Log.d(TAG, "discoverNearbyDeviceBlocking: startDiscovery() successful.")

        Handler(Looper.getMainLooper()).postDelayed({
            if (latch.count > 0) {
                Log.w(TAG, "discoverNearbyDeviceBlocking: Discovery timed out after 40 seconds.")
                bluetoothAdapter.cancelDiscovery()
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(appContext, "Discovery Timeout", Toast.LENGTH_SHORT).show()
                }
                latch.countDown()
            }
        }, 40_000)

        latch.await()

        try {
            context.unregisterReceiver(receiver)
            Log.d(TAG, "discoverNearbyDeviceBlocking: BroadcastReceiver unregistered.")
        } catch (e: Exception) {
            Log.w(TAG, "discoverNearbyDeviceBlocking: Receiver already unregistered.", e)
        }

        val resultDevice = foundDevices.firstOrNull()
        if (resultDevice == null) {
            Log.w(TAG, "discoverNearbyDeviceBlocking: Discovery finished, but no suitable device was found.")
        } else {
            Log.i(TAG, "discoverNearbyDeviceBlocking: Discovery finished. Selected device: ${resultDevice.name}")
        }
        return resultDevice
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
        Log.d(TAG, "startSession: Attempting to start a new session.")
        try {
            if (activeDevice != null) {
                Log.d(TAG, "startSession: Session already active with device ${activeDevice?.name}")
                return true
            }

            val device = discoverNearbyDeviceBlocking()
            if (device == null) {
                Log.e(TAG, "startSession: discoverNearbyDeviceBlocking failed to find a device.")
                return false
            }
            activeDevice = device
            Log.i(TAG, "startSession: Session started successfully with device ${activeDevice?.name}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "startSession: startSession() failed", e)
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
        Log.d(TAG, "getGroupDescriptionAndCRS: Looking for a TTP device.")
        activeDevice = discoverDeviceWithRoleBlocking(Role.TTP)

        if (activeDevice == null) {
            val errorMsg = "Could not find a TTP device to get group description."
            Log.e(TAG, "getGroupDescriptionAndCRS: $errorMsg")
            throw TimeoutException(errorMsg)
        }

        Log.d(TAG, "getGroupDescriptionAndCRS: Found TTP device ${activeDevice?.name}. Connecting...")
        var socket: BluetoothSocket? = null
        try{
            socket = activeDevice!!.createRfcommSocketToServiceRecord(TTP_SERVICE_UUID)
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

        } catch(e: Exception){
            Log.e(TAG, "getGroupDescriptionAndCRS: FAILED", e)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context.applicationContext, "getGroupDescriptionAndCRS() FAIL: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } finally {
            socket?.close()
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
        Log.d(TAG, "register: Called for user '$userName' to register at TTP '$nameTTP'")

        // Use the new role-based discovery function
        activeDevice = discoverDeviceWithRoleBlocking(Role.TTP)

        if (activeDevice == null) {
            Log.e(TAG, "register: Could not find a TTP device. Aborting registration.")
            throw Exception("No TTP peer found")
        }

        Log.d(TAG, "register: Found TTP device ${activeDevice?.name}. Proceeding with registration.")

        // The call to startSession() has been removed. This is the fix.

        var socket: BluetoothSocket? = null
        try {
            // Now this will correctly use the TTP device you discovered.
            socket = activeDevice!!.createRfcommSocketToServiceRecord(TTP_SERVICE_UUID)
            Log.d(TAG, "register: RFCOMM socket created. Attempting to connect...")
            socket.connect()
            Log.i(TAG, "register: Socket connected successfully to ${activeDevice?.address}")

            // ... a lot of code from the original function
            val output = ObjectOutputStream(socket.outputStream)
            val input = ObjectInputStream(socket.inputStream)

            val ttpAddress = addressBookManager.getAddressByName(nameTTP)
            val ttpPublicKeyBytes = ttpAddress.publicKey.toBytes()

            Log.d(TAG, "register: Sending request type 'REGISTER_AT_TTP'")
            output.writeObject("REGISTER_AT_TTP")
            Log.d(TAG, "register: Sending userName '$userName'")
            output.writeObject(userName)
            Log.d(TAG, "register: Sending public key (size: ${publicKey.toBytes().size})")
            output.writeObject(publicKey.toBytes())
            Log.d(TAG, "register: Sending TTP public key (size: ${ttpPublicKeyBytes.size})")
            output.writeObject(ttpPublicKeyBytes)
            output.flush()
            Log.d(TAG, "register: All data sent and flushed. Waiting for response...")

            val result = input.readObject() as String
            Log.i(TAG, "register: Received response from TTP: '$result'")
            if (result != "SUCCESS") {
                throw Exception("Registration failed with response: $result")
            }

            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context.applicationContext, "register() SUCCESS", Toast.LENGTH_LONG).show()
            }

        } catch(e: Exception) {
            Log.e(TAG, "register: Registration FAILED", e)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context.applicationContext, "register() FAIL: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } finally {
            try {
                socket?.close()
                Log.d(TAG, "register: Socket closed.")
            } catch (e: Exception) {
                Log.e(TAG, "register: Error closing socket", e)
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
        Log.d(TAG, "getBlindSignatureRandomness: Looking for a Bank device.")
        activeDevice = discoverDeviceWithRoleBlocking(Role.Bank) // 1. Look for a BANK

        if (activeDevice == null) {
            Log.e(TAG, "getBlindSignatureRandomness: Could not find a Bank device.")
            throw Exception("No Bank peer found")
        }

        Log.d(TAG, "getBlindSignatureRandomness: Found Bank device ${activeDevice?.name}. Connecting...")
        var socket: BluetoothSocket? = null
        try {
            // 2. Connect using the BANK_SERVICE_UUID
            socket = activeDevice!!.createRfcommSocketToServiceRecord(BANK_SERVICE_UUID)
            socket.connect()

            val output = ObjectOutputStream(socket.outputStream)
            val input = ObjectInputStream(socket.inputStream)

            output.writeObject("BLIND_SIGNATURE_RANDOMNESS_REQUEST")
            output.writeObject(publicKey.toBytes())
            output.flush()

            val randomnessBytes = input.readObject() as ByteArray
            return group.gElementFromBytes(randomnessBytes)

        } catch(e: Exception) {
            Log.e(TAG, "getBlindSignatureRandomness: FAILED", e)
            throw e // Re-throw the exception
        } finally {
            socket?.close()
        }
    }

    override fun requestBlindSignature(
        publicKey: Element,
        bankName: String,
        challenge: BigInteger
    ): BigInteger {
        Log.d(TAG, "requestBlindSignature: Looking for a Bank device.")
        activeDevice = discoverDeviceWithRoleBlocking(Role.Bank)

        if (activeDevice == null) {
            val errorMsg = "Could not find a Bank device."
            Log.e(TAG, "requestBlindSignature: $errorMsg")
            throw TimeoutException(errorMsg)
        }

        Log.d(TAG, "requestBlindSignature: Found Bank device ${activeDevice?.name}. Connecting...")
        var socket: BluetoothSocket? = null
        try {
            // Connect using the BANK_SERVICE_UUID
            socket = activeDevice!!.createRfcommSocketToServiceRecord(BANK_SERVICE_UUID)
            socket.connect()

            val output = ObjectOutputStream(socket.outputStream)
            val input = ObjectInputStream(socket.inputStream)

            output.writeObject("BLIND_SIGNATURE_REQUEST")
            output.writeObject(publicKey.toBytes())
            output.writeObject(challenge)
            output.flush()

            val signature = input.readObject() as BigInteger
            Log.i(TAG, "requestBlindSignature: Successfully received blind signature from Bank.")
            return signature

        } catch (e: Exception) {
            Log.e(TAG, "requestBlindSignature: FAILED", e)
            throw e
        } finally {
            socket?.close()
        }
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
        Log.d(TAG, "requestTransactionRandomness: Looking for a User device.")
        activeDevice = discoverDeviceWithRoleBlocking(Role.User)

        if (activeDevice == null) {
            val errorMsg = "Could not find another User device for transaction."
            Log.e(TAG, "requestTransactionRandomness: $errorMsg")
            throw TimeoutException(errorMsg)
        }

        Log.d(TAG, "requestTransactionRandomness: Found User device ${activeDevice?.name}. Connecting...")
        var socket: BluetoothSocket? = null
        try {
            // Connect using the USER_SERVICE_UUID
            socket = activeDevice!!.createRfcommSocketToServiceRecord(USER_SERVICE_UUID)
            socket.connect()

            val output = ObjectOutputStream(socket.outputStream)
            val input = ObjectInputStream(socket.inputStream)

            output.writeObject("TRANSACTION_RANDOMNESS_REQUEST")
            output.writeObject(participant.publicKey.toBytes())
            output.writeObject(this.bankPublicKey.toBytes())
            output.flush()

            val randBytes = input.readObject() as RandomizationElementsBytes
            Log.i(TAG, "requestTransactionRandomness: Successfully received transaction randomness.")
            return randBytes.toRandomizationElements(group)

        } catch (e: Exception) {
            Log.e(TAG, "requestTransactionRandomness: FAILED", e)
            throw e
        } finally {
            socket?.close()
        }
    }

    override fun sendTransactionDetails(
        userNameReceiver: String,
        transactionDetails: TransactionDetails
    ): String {
        // Note: This function assumes a session is already active from requestTransactionRandomness
        if (activeDevice == null) {
            val errorMsg = "No active device session for sending transaction details. Must request randomness first."
            Log.e(TAG, "sendTransactionDetails: $errorMsg")
            throw IllegalStateException(errorMsg)
        }

        Log.d(TAG, "sendTransactionDetails: Sending details to User device ${activeDevice?.name}...")
        var socket: BluetoothSocket? = null
        try {
            // Connect using the USER_SERVICE_UUID
            socket = activeDevice!!.createRfcommSocketToServiceRecord(USER_SERVICE_UUID)
            socket.connect()

            val output = ObjectOutputStream(socket.outputStream)
            val input = ObjectInputStream(socket.inputStream)

            output.writeObject("TRANSACTION_DETAILS")
            output.writeObject(participant.publicKey.toBytes())
            output.writeObject(transactionDetails.toTransactionDetailsBytes())
            output.flush()

            val result = input.readObject() as String
            Log.i(TAG, "sendTransactionDetails: Successfully received transaction result: $result")
            return result

        } catch (e: Exception) {
            Log.e(TAG, "sendTransactionDetails: FAILED", e)
            throw e
        } finally {
            socket?.close()
        }
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
