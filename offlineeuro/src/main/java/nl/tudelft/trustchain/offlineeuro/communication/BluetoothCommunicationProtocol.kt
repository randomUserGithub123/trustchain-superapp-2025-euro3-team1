package nl.tudelft.trustchain.offlineeuro.communication

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahaiProof
import nl.tudelft.trustchain.offlineeuro.cryptography.RandomizationElements
import nl.tudelft.trustchain.offlineeuro.entity.Participant
import nl.tudelft.trustchain.offlineeuro.entity.TransactionDetails
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.math.BigInteger
import java.util.UUID

class BluetoothCommunicationProtocol(
    private val bluetoothAdapter: BluetoothAdapter
) : ICommunicationProtocol {
    override lateinit var participant: Participant
    private val SERVICE_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val SERVICE_NAME = "OfflineEuroTransfer"
    private var serverSocket: BluetoothServerSocket? = null
    private var clientSocket: BluetoothSocket? = null
    private val connectedDevices = mutableMapOf<String, BluetoothDevice>()
    private lateinit var group: BilinearGroup

    init {
        startServer()
    }

    private fun startServer() {
        serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
        Thread {
            while (true) {
                try {
                    val socket = serverSocket?.accept()
                    socket?.let { handleIncomingConnection(it) }
                } catch (e: Exception) {
                    e.printStackTrace()
                    break
                }
            }
        }.start()
    }

    private fun handleIncomingConnection(socket: BluetoothSocket) {
        Thread {
            try {
                val input = ObjectInputStream(socket.inputStream)
                val output = ObjectOutputStream(socket.outputStream)
                
                when (val messageType = input.readObject() as String) {
                    "TRANSACTION" -> {
                        val transactionDetails = input.readObject() as TransactionDetails
                        val senderPublicKey = input.readObject() as Element
                        val result = participant.onReceivedTransaction(transactionDetails, participant.publicKey, senderPublicKey)
                        output.writeObject(result)
                    }
                    "RANDOMIZATION" -> {
                        val randomizationElements = createRandomizationElements()
                        output.writeObject(randomizationElements)
                    }
                    // Add other message types as needed
                }
                
                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun createRandomizationElements(): RandomizationElements {
        val publicKey = participant.publicKey
        return participant.generateRandomizationElements(publicKey)
    }

    fun discoverDevices(): Set<BluetoothDevice> {
        bluetoothAdapter.startDiscovery()
        return bluetoothAdapter.bondedDevices
    }

    private fun connectToDevice(deviceName: String): BluetoothSocket? {
        val device = connectedDevices[deviceName] ?: run {
            val pairedDevices = bluetoothAdapter.bondedDevices
            pairedDevices.find { it.name == deviceName }?.also {
                connectedDevices[deviceName] = it
            }
        }

        return device?.createRfcommSocketToServiceRecord(SERVICE_UUID)
    }

    override fun getGroupDescriptionAndCRS() {
        // Not needed ??
    }

    override fun register(userName: String, publicKey: Element, nameTTP: String) {
        // Not needed ??
    }

    override fun getBlindSignatureRandomness(publicKey: Element, bankName: String, group: BilinearGroup): Element {
        this.group = group
        val socket = connectToDevice(bankName) ?: throw IllegalStateException("Could not connect to bank")
        
        val output = ObjectOutputStream(socket.outputStream)
        val input = ObjectInputStream(socket.inputStream)
        
        output.writeObject("RANDOMNESS_REQUEST")
        output.writeObject(publicKey)
        
        val randomness = input.readObject() as Element
        socket.close()
        return randomness
    }

    override fun requestBlindSignature(publicKey: Element, bankName: String, challenge: BigInteger): BigInteger {
        val socket = connectToDevice(bankName) ?: throw IllegalStateException("Could not connect to bank")
        
        val output = ObjectOutputStream(socket.outputStream)
        val input = ObjectInputStream(socket.inputStream)
        
        output.writeObject("SIGNATURE_REQUEST")
        output.writeObject(publicKey)
        output.writeObject(challenge)
        
        val signature = input.readObject() as BigInteger
        socket.close()
        return signature
    }

    override fun requestTransactionRandomness(userNameReceiver: String, group: BilinearGroup): RandomizationElements {
        this.group = group
        val socket = connectToDevice(userNameReceiver) ?: throw IllegalStateException("Could not connect to receiver")
        
        val output = ObjectOutputStream(socket.outputStream)
        val input = ObjectInputStream(socket.inputStream)
        
        output.writeObject("RANDOMIZATION")
        
        val randomizationElements = input.readObject() as RandomizationElements
        socket.close()
        return randomizationElements
    }

    override fun sendTransactionDetails(userNameReceiver: String, transactionDetails: TransactionDetails): String {
        val socket = connectToDevice(userNameReceiver) ?: throw IllegalStateException("Could not connect to receiver")
        
        val output = ObjectOutputStream(socket.outputStream)
        val input = ObjectInputStream(socket.inputStream)
        
        output.writeObject("TRANSACTION")
        output.writeObject(transactionDetails)
        output.writeObject(participant.publicKey)
        
        val result = input.readObject() as String
        socket.close()
        return result
    }

    override fun requestFraudControl(firstProof: GrothSahaiProof, secondProof: GrothSahaiProof, nameTTP: String): String {
        val socket = connectToDevice(nameTTP) ?: throw IllegalStateException("Could not connect to TTP")
        
        val output = ObjectOutputStream(socket.outputStream)
        val input = ObjectInputStream(socket.inputStream)
        
        output.writeObject("FRAUD_CONTROL")
        output.writeObject(firstProof)
        output.writeObject(secondProof)
        
        val result = input.readObject() as String
        socket.close()
        return result
    }

    override fun getPublicKeyOf(name: String, group: BilinearGroup): Element {
        this.group = group
        val socket = connectToDevice(name) ?: throw IllegalStateException("Could not connect to $name")
        
        val output = ObjectOutputStream(socket.outputStream)
        val input = ObjectInputStream(socket.inputStream)
        
        output.writeObject("PUBLIC_KEY_REQUEST")
        
        val publicKey = input.readObject() as Element
        socket.close()
        return publicKey
    }

    fun close() {
        serverSocket?.close()
        clientSocket?.close()
    }
} 