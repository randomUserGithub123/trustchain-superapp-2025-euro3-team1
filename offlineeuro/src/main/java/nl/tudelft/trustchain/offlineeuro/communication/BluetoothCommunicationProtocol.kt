package nl.tudelft.trustchain.offlineeuro.communication

import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
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

class BluetoothCommunicationProtocol(private val context: Context) : ICommunicationProtocol {

    override lateinit var participant: Participant

    private val bluetoothAdapter: BluetoothAdapter =
        BluetoothAdapter.getDefaultAdapter() ?: throw IllegalStateException("Bluetooth not supported")

    private val SERVICE_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val SERVICE_NAME = "OfflineEuroTransfer"

    init {
        startServer()
    }

    private fun startServer() {
        thread {
            try {
                val serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
                while (true) {
                    val socket = serverSocket.accept() ?: continue
                    handleIncomingConnection(socket)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun handleIncomingConnection(socket: BluetoothSocket) {
        thread {
            try {
                val input = ObjectInputStream(socket.inputStream)
                val output = ObjectOutputStream(socket.outputStream)

                when (val requestType = input.readObject() as String) {
                    "RANDOMIZATION_REQUEST" -> {
                        val publicKey = input.readObject() as Element
                        val randomization = participant.generateRandomizationElements(publicKey)
                        output.writeObject(randomization)
                        output.flush()
                    }
                    // Other request types can go here
                }

                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ✅ Blocking device discovery with RSSI filter and phone type
    private fun blockingDiscoverNearbySmartphone(): BluetoothDevice {
        val latch = CountDownLatch(1)
        var result: BluetoothDevice? = null
        var exception: Exception? = null

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == BluetoothDevice.ACTION_FOUND) {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                    val deviceClass = device?.bluetoothClass?.deviceClass

                    if (device != null && rssi > -40 && deviceClass == BluetoothClass.Device.PHONE_SMART) {
                        result = device
                        latch.countDown()
                        try {
                            context?.unregisterReceiver(this)
                        } catch (_: Exception) {}
                        bluetoothAdapter.cancelDiscovery()
                    }
                }
            }
        }

        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        context.registerReceiver(receiver, filter)
        bluetoothAdapter.startDiscovery()

        Handler(Looper.getMainLooper()).postDelayed({
            if (latch.count > 0) {
                bluetoothAdapter.cancelDiscovery()
                try {
                    context.unregisterReceiver(receiver)
                } catch (_: Exception) {}
                exception = TimeoutException("No suitable device found in 10 seconds")
                latch.countDown()
            }
        }, 10_000)

        latch.await()
        exception?.let { throw it }
        return result ?: throw IllegalStateException("Discovery returned no device")
    }

    // ✅ Interface implementation without suspending
    override fun requestTransactionRandomness(userNameReceiver: String, group: BilinearGroup): RandomizationElements {
        var result: RandomizationElements? = null
        var exception: Exception? = null
        val latch = CountDownLatch(1)

        thread {
            try {
                val device = blockingDiscoverNearbySmartphone()
                val socket = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
                socket.connect()
                val output = ObjectOutputStream(socket.outputStream)
                val input = ObjectInputStream(socket.inputStream)

                output.writeObject("RANDOMIZATION_REQUEST")
                output.writeObject(participant.publicKey)
                output.flush()

                result = input.readObject() as RandomizationElements
                socket.close()
            } catch (e: Exception) {
                exception = e
            } finally {
                latch.countDown()
            }
        }

        latch.await()
        exception?.let { throw it }
        return result ?: throw IllegalStateException("No result received")
    }

    // --- Stub implementations (to be implemented later) ---
    override fun getGroupDescriptionAndCRS() = Unit
    override fun register(userName: String, publicKey: Element, nameTTP: String) = Unit
    override fun getBlindSignatureRandomness(publicKey: Element, bankName: String, group: BilinearGroup): Element {
        throw NotImplementedError()
    }
    override fun requestBlindSignature(publicKey: Element, bankName: String, challenge: BigInteger): BigInteger {
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
