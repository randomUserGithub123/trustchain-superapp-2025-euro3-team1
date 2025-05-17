package nl.tudelft.trustchain.offlineeuro

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.entity.User
import nl.tudelft.trustchain.offlineeuro.entity.DigitalEuro
import nl.tudelft.trustchain.offlineeuro.communication.BluetoothCommunicationProtocol
import org.junit.Before
import org.junit.Test
import org.junit.Assert
import org.mockito.Mockito.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any

class BluetoothTransferTest {
    private lateinit var sender: User
    private lateinit var receiver: User
    private lateinit var group: BilinearGroup
    
    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockBluetoothAdapter: BluetoothAdapter
    
    @Mock
    private lateinit var mockBluetoothDevice: BluetoothDevice
    
    @Mock
    private lateinit var mockBluetoothSocket: BluetoothSocket

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        
        group = BilinearGroup()
        
        // Setup basic mock behavior
        `when`(mockBluetoothAdapter.isEnabled).thenReturn(true)
        `when`(mockBluetoothAdapter.getRemoteDevice(anyString())).thenReturn(mockBluetoothDevice)
        `when`(mockBluetoothDevice.createRfcommSocketToServiceRecord(any())).thenReturn(mockBluetoothSocket)
        
        // Initialize sender
        sender = User(
            name = "sender",
            group = group,
            context = mockContext,
            communicationProtocol = BluetoothCommunicationProtocol(mockBluetoothAdapter),
            runSetup = true
        )
        
        // Initialize receiver
        receiver = User(
            name = "receiver",
            group = group,
            context = mockContext,
            communicationProtocol = BluetoothCommunicationProtocol(mockBluetoothAdapter),
            runSetup = true
        )
    }

    @Test
    fun testBluetoothDeviceDiscovery() {
        val protocol = BluetoothCommunicationProtocol(mockBluetoothAdapter)
        
        `when`(mockBluetoothAdapter.startDiscovery()).thenReturn(true)
        `when`(mockBluetoothAdapter.bondedDevices).thenReturn(setOf(mockBluetoothDevice))
        
        val devices = protocol.discoverDevices()
        Assert.assertTrue("Should find at least one device", devices.isNotEmpty())
        verify(mockBluetoothAdapter).startDiscovery()
    }

    @Test
    fun testBluetoothEuroTransfer() {
        // Mock successful Bluetooth connection
        `when`(mockBluetoothAdapter.getRemoteDevice(anyString())).thenReturn(mockBluetoothDevice)
        `when`(mockBluetoothDevice.createRfcommSocketToServiceRecord(any())).thenReturn(mockBluetoothSocket)
        
        // Perform euro transfer
        val initialSenderBalance = sender.getBalance()
        val initialReceiverBalance = receiver.getBalance()
        
        // Transfer euro
        val result = sender.sendDigitalEuroTo(receiver.name)
        
        // Verify transfer was successful
        Assert.assertEquals("Transfer should be successful", "Transfer successful", result)
        Assert.assertEquals("Sender balance should decrease by 1", initialSenderBalance - 1, sender.getBalance())
        Assert.assertEquals("Receiver balance should increase by 1", initialReceiverBalance + 1, receiver.getBalance())
    }

    @Test
    fun testBluetoothTransferFailure() {
        // Mock Bluetooth connection failure
        `when`(mockBluetoothAdapter.getRemoteDevice(anyString())).thenThrow(IllegalArgumentException())
        
        try {
            sender.sendDigitalEuroTo(receiver.name)
            Assert.fail("Should throw exception on Bluetooth failure")
        } catch (e: Exception) {
            Assert.assertTrue("Should throw appropriate exception", e is IllegalArgumentException)
        }
    }

    @Test
    fun testDoubleSpendingOverBluetooth() {
        // Mock successful Bluetooth connection
        `when`(mockBluetoothAdapter.getRemoteDevice(anyString())).thenReturn(mockBluetoothDevice)
        `when`(mockBluetoothDevice.createRfcommSocketToServiceRecord(any())).thenReturn(mockBluetoothSocket)
        
        // Attempt double spending
        val result1 = sender.sendDigitalEuroTo(receiver.name)
        val result2 = sender.doubleSpendDigitalEuroTo(receiver.name)
        
        // Verify double spending was detected
        Assert.assertTrue("First transfer should be successful", result1.contains("successful"))
        Assert.assertTrue("Double spending should be detected", result2.contains("double spending"))
    }
} 