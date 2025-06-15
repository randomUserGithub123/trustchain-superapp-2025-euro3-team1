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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import nl.tudelft.trustchain.offlineeuro.databinding.FragmentBluetoothTransferBinding
import nl.tudelft.trustchain.offlineeuro.entity.User

class BluetoothTransferFragment : Fragment() {
    private var _binding: FragmentBluetoothTransferBinding? = null
    private val binding get() = _binding!!

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var deviceAdapter: BluetoothDeviceAdapter
    private lateinit var user: User

    private val bluetoothPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions.all { it.value }) {
                startBluetoothScan()
            } else {
                Toast.makeText(requireContext(), "Bluetooth permissions required", Toast.LENGTH_SHORT).show()
            }
        }

    private val enableBluetoothLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                updateBluetoothStatus()
                startBluetoothScan()
            }
        }

    private val discoveredDevices = mutableSetOf<BluetoothDevice>()
    private val deviceDiscoveryReceiver =
        object : android.content.BroadcastReceiver() {
            override fun onReceive(
                context: android.content.Context?,
                intent: android.content.Intent?
            ) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        device?.let {
                            discoveredDevices.add(it)
                            if (checkBluetoothPermissions()) {
                                try {
                                    deviceAdapter.submitList((bluetoothAdapter.bondedDevices + discoveredDevices).toList())
                                } catch (e: SecurityException) {
                                    // Ignore security exception, just show discovered devices
                                    deviceAdapter.submitList(discoveredDevices.toList())
                                }
                            } else {
                                deviceAdapter.submitList(discoveredDevices.toList())
                            }
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

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        setupRecyclerView()
        setupButtons()
        updateBluetoothStatus()

        // Register for broadcasts when a device is discovered
        val filter =
            android.content.IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
        requireContext().registerReceiver(deviceDiscoveryReceiver, filter)
    }

    private fun setupRecyclerView() {
        deviceAdapter =
            BluetoothDeviceAdapter { device ->
                showTransferConfirmation(device)
            }

        binding.devicesRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = deviceAdapter
        }
    }

    private fun setupButtons() {
        binding.toggleBluetoothButton.setOnClickListener {
            if (!checkBluetoothPermissions()) {
                return@setOnClickListener
            }

            if (!bluetoothAdapter.isEnabled) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                enableBluetoothLauncher.launch(enableBtIntent)
            } else {
                try {
                    bluetoothAdapter.disable()
                    updateBluetoothStatus()
                } catch (e: SecurityException) {
                    Toast.makeText(requireContext(), "Bluetooth permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.scanButton.setOnClickListener {
            if (checkBluetoothPermissions()) {
                startBluetoothScan()
            }
        }
    }

    private fun updateBluetoothStatus() {
        if (!checkBluetoothPermissions()) {
            return
        }

        val isEnabled =
            try {
                bluetoothAdapter.isEnabled
            } catch (e: SecurityException) {
                false
            }

        binding.bluetoothStatus.text = if (isEnabled) "Bluetooth is enabled" else "Bluetooth is disabled"
        binding.toggleBluetoothButton.text = if (isEnabled) "Disable Bluetooth" else "Enable Bluetooth"
        binding.scanButton.isEnabled = isEnabled
    }

    private fun checkBluetoothPermissions(): Boolean {
        val permissions =
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )

        if (permissions.any { permission ->
                ActivityCompat.checkSelfPermission(requireContext(), permission) != PackageManager.PERMISSION_GRANTED
            }
        ) {
            bluetoothPermissionLauncher.launch(permissions)
            return false
        }
        return true
    }

    private fun startBluetoothScan() {
        if (!checkBluetoothPermissions()) {
            return
        }

        val isDiscovering =
            try {
                bluetoothAdapter.isDiscovering
            } catch (e: SecurityException) {
                false
            }

        if (!isDiscovering) {
            try {
                bluetoothAdapter.startDiscovery()
            } catch (e: SecurityException) {
                Toast.makeText(requireContext(), "Bluetooth permission denied", Toast.LENGTH_SHORT).show()
                return
            }
        }

        val devices =
            try {
                (bluetoothAdapter.bondedDevices + getScannedDevices()).toSet()
            } catch (e: SecurityException) {
                Toast.makeText(requireContext(), "Bluetooth permission denied", Toast.LENGTH_SHORT).show()
                emptySet()
            }
        deviceAdapter.submitList(devices.toList())
    }

    private fun getScannedDevices(): Set<BluetoothDevice> {
        binding.scanButton.isEnabled = false
        discoveredDevices.clear()
        return discoveredDevices
    }

    private fun showTransferConfirmation(device: BluetoothDevice) {
        val deviceName =
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                device.name ?: "Unknown Device"
            } else {
                "Unknown Device"
            }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Confirm Transfer")
            .setMessage("Do you want to send 1 euro to $deviceName?")
            .setPositiveButton("Send") { _, _ ->
                performTransfer(device)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performTransfer(device: BluetoothDevice) {
        try {
            val deviceName =
                if (ActivityCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    device.name ?: "Unknown Device"
                } else {
                    "Unknown Device"
                }
            val result = user.sendDigitalEuroTo(deviceName)
            Toast.makeText(requireContext(), result, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Transfer failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireContext().unregisterReceiver(deviceDiscoveryReceiver)
        if (checkBluetoothPermissions()) {
            try {
                if (bluetoothAdapter.isDiscovering) {
                    bluetoothAdapter.cancelDiscovery()
                }
            } catch (e: SecurityException) {
                // Ignore security exception on cleanup
            }
        }
        _binding = null
    }
}
