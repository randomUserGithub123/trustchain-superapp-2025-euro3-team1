package nl.tudelft.trustchain.offlineeuro.ui.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import nl.tudelft.trustchain.offlineeuro.R
import nl.tudelft.trustchain.offlineeuro.databinding.FragmentBluetoothTransferBinding
import nl.tudelft.trustchain.offlineeuro.entity.User
import nl.tudelft.trustchain.offlineeuro.communication.BluetoothCommunicationProtocol

class BluetoothTransferFragment : Fragment() {
    private var _binding: FragmentBluetoothTransferBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var deviceAdapter: BluetoothDeviceAdapter
    private lateinit var user: User

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            startBluetoothScan()
        } else {
            Toast.makeText(requireContext(), "Bluetooth permissions required", Toast.LENGTH_SHORT).show()
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            updateBluetoothStatus()
            startBluetoothScan()
        }
    }

    private val discoveredDevices = mutableSetOf<BluetoothDevice>()
    private val deviceDiscoveryReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        discoveredDevices.add(it)
                        deviceAdapter.submitList((bluetoothAdapter.bondedDevices + discoveredDevices).toList())
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    binding.scanButton.isEnabled = true
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBluetoothTransferBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        setupRecyclerView()
        setupButtons()
        updateBluetoothStatus()

        // Register for broadcasts when a device is discovered
        val filter = android.content.IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        requireContext().registerReceiver(deviceDiscoveryReceiver, filter)
    }

    private fun setupRecyclerView() {
        deviceAdapter = BluetoothDeviceAdapter { device ->
            showTransferConfirmation(device)
        }
        
        binding.devicesRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = deviceAdapter
        }
    }

    private fun setupButtons() {
        binding.toggleBluetoothButton.setOnClickListener {
            if (!bluetoothAdapter.isEnabled) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                enableBluetoothLauncher.launch(enableBtIntent)
            } else {
                bluetoothAdapter.disable()
                updateBluetoothStatus()
            }
        }

        binding.scanButton.setOnClickListener {
            if (checkBluetoothPermissions()) {
                startBluetoothScan()
            }
        }
    }

    private fun updateBluetoothStatus() {
        val isEnabled = bluetoothAdapter.isEnabled
        binding.bluetoothStatus.text = if (isEnabled) "Bluetooth is enabled" else "Bluetooth is disabled"
        binding.toggleBluetoothButton.text = if (isEnabled) "Disable Bluetooth" else "Enable Bluetooth"
        binding.scanButton.isEnabled = isEnabled
    }

    private fun checkBluetoothPermissions(): Boolean {
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        if (permissions.any { permission ->
            ActivityCompat.checkSelfPermission(requireContext(), permission) != PackageManager.PERMISSION_GRANTED
        }) {
            bluetoothPermissionLauncher.launch(permissions)
            return false
        }
        return true
    }

    private fun startBluetoothScan() {
        if (!bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.startDiscovery()
        }
        
        val devices = (bluetoothAdapter.bondedDevices + getScannedDevices()).toSet()
        deviceAdapter.submitList(devices.toList())
    }

    private fun getScannedDevices(): Set<BluetoothDevice> {
        binding.scanButton.isEnabled = false
        discoveredDevices.clear()
        return discoveredDevices
    }

    private fun showTransferConfirmation(device: BluetoothDevice) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Confirm Transfer")
            .setMessage("Do you want to send 1 euro to ${device.name}?")
            .setPositiveButton("Send") { _, _ ->
                performTransfer(device)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performTransfer(device: BluetoothDevice) {
        try {
            val result = user.sendDigitalEuroTo(device.name)
            Toast.makeText(requireContext(), result, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Transfer failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireContext().unregisterReceiver(deviceDiscoveryReceiver)
        bluetoothAdapter.cancelDiscovery()
        _binding = null
    }
} 