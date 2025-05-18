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
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroupElementsBytes
import nl.tudelft.trustchain.offlineeuro.cryptography.CRSBytes
import nl.tudelft.trustchain.offlineeuro.cryptography.RandomizationElements
import nl.tudelft.trustchain.offlineeuro.entity.Participant
import nl.tudelft.trustchain.offlineeuro.entity.TransactionDetails
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.math.BigInteger
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeoutException
import kotlin.concurrent.thread
import nl.tudelft.trustchain.offlineeuro.entity.TTP

class BluetoothCommunicationProtocol(private val context: Context) : ICommunicationProtocol {

    override lateinit var participant: Participant

    private val bluetoothAdapter: BluetoothAdapter =
        BluetoothAdapter.getDefaultAdapter() ?: throw IllegalStateException("Bluetooth not supported")

    private val SERVICE_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val SERVICE_NAME = "OfflineEuroTransfer"

    private var serverThread: Thread? = null
    private var serverSocket: BluetoothServerSocket? = null
    @Volatile private var running: Boolean = true

    private val crsReady = CountDownLatch(1)
    @Volatile private var crsStarted = false

    private var alreadyConnectedDevice: BluetoothDevice? = null

    init {
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
                            Log.e("BluetoothProtocol", "Error accepting connection: ${e.message}", e)
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(context.applicationContext, "Server error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("BluetoothProtocol", "Fatal server socket error: ${e.message}", e)
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
                val input = ObjectInputStream(socket.inputStream)
                val output = ObjectOutputStream(socket.outputStream)

                when (val requestType = input.readObject() as String) {

                    "GROUP_CRS_REQUEST" -> {

                        val groupBytes = participant.group.toGroupElementBytes()
                        val crsBytes = participant.crs.toCRSBytes()
                        output.writeObject(groupBytes)
                        output.writeObject(crsBytes)
                        output.flush()
                    }
                    "REGISTRATION_REQUEST" -> {

                        val userName = input.readObject() as String
                        val publicKeyBytes = input.readObject() as ByteArray
                        if (participant is TTP) {
                            val ttp = participant as TTP
                            val publicKey = ttp.group.gElementFromBytes(publicKeyBytes)
                            ttp.registerUser(userName, publicKey)

                            output.writeObject("ACK")
                            output.flush()
                        }
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

        alreadyConnectedDevice?.let { return it }

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
                            rssi >= -40 &&
                            deviceClass == BluetoothClass.Device.PHONE_SMART &&
                            !foundDevices.contains(device)
                        ){
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

        alreadyConnectedDevice = foundDevices.firstOrNull()
        return alreadyConnectedDevice
    }

    fun clearPairedDevice() {
        alreadyConnectedDevice = null
    }

    override fun getGroupDescriptionAndCRS() {

        crsStarted = true

        thread {
            try {

                val device = discoverNearbyDeviceBlocking() ?: run {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "No peer found.", Toast.LENGTH_LONG).show()
                    }
                    return@thread
                }

                val socket = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
                socket.connect()

                val output = ObjectOutputStream(socket.outputStream)
                val input = ObjectInputStream(socket.inputStream)

                output.writeObject("GROUP_CRS_REQUEST")
                output.flush()

                val groupBytes = input.readObject() as BilinearGroupElementsBytes
                val crsBytes = input.readObject() as CRSBytes

                participant.group.updateGroupElements(groupBytes)
                participant.crs = crsBytes.toCRS(participant.group)

                socket.close()

            } catch (e: Exception) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                crsReady.countDown()
            }
        }
    }

    override fun register(userName: String, publicKey: Element, nameTTP: String) {
        thread {
            try {

                if (crsStarted) {
                    crsReady.await()
                }

                val device = discoverNearbyDeviceBlocking() ?: return@thread
                val socket = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
                socket.connect()

                val output = ObjectOutputStream(socket.outputStream)
                val input = ObjectInputStream(socket.inputStream)

                output.writeObject("REGISTRATION_REQUEST")
                output.writeObject(userName)
                output.writeObject(publicKey.toBytes())
                output.flush()

                val ack = input.readObject() as? String
                if (ack != "ACK") {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Error: no ACK", Toast.LENGTH_SHORT).show()
                    }
                }

                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "You have registered with TTP!", Toast.LENGTH_SHORT).show()
                }

                socket.close()

                clearPairedDevice()

            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun getBlindSignatureRandomness(publicKey: Element, bankName: String, group: BilinearGroup): Element {
        throw NotImplementedError()
    }

    override fun requestBlindSignature(publicKey: Element, bankName: String, challenge: BigInteger): BigInteger {
        throw NotImplementedError()
    }

    override fun requestTransactionRandomness(userNameReceiver: String, group: BilinearGroup): RandomizationElements {
        throw NotImplementedError()
    }

    override fun sendTransactionDetails(userNameReceiver: String, transactionDetails: TransactionDetails): String {
        throw NotImplementedError()
    }

    override fun requestFraudControl(
        firstProof: nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahaiProof,
        secondProof: nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahaiProof,
        nameTTP: String
    ): String {
        throw NotImplementedError()
    }

    override fun getPublicKeyOf(name: String, group: BilinearGroup): Element {
        throw NotImplementedError()
    }
}
