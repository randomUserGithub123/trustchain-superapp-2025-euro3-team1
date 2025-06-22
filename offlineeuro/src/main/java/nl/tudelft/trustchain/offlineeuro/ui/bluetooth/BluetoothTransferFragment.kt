package nl.tudelft.trustchain.offlineeuro.ui.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Check if all the requested permissions were granted
        if (permissions.values.all { it }) {
            Log.d("Permissions", "All requested permissions granted by user.")
            startBluetoothScan()
        } else {
            Log.d("Permissions", "One or more permissions were denied by user.")
            Toast.makeText(requireContext(), "All Bluetooth permissions are required to continue", Toast.LENGTH_LONG).show()
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
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let {
                        // Avoid adding devices without a name to the list
                        if (!it.name.isNullOrEmpty()) {
                            discoveredDevices.add(it)
                            deviceAdapter.submitList((bluetoothAdapter.bondedDevices + discoveredDevices).toList())
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
            if (checkBluetoothPermissions()) { // Also check permissions before disabling
                if (!bluetoothAdapter.isEnabled) {
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    enableBluetoothLauncher.launch(enableBtIntent)
                } else {
                    bluetoothAdapter.disable()
                    updateBluetoothStatus()
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
        val isEnabled = bluetoothAdapter.isEnabled
        binding.bluetoothStatus.text = if (isEnabled) "Bluetooth is enabled" else "Bluetooth is disabled"
        binding.toggleBluetoothButton.text = if (isEnabled) "Disable Bluetooth" else "Enable Bluetooth"
        binding.scanButton.isEnabled = isEnabled
    }

    /**
     * THIS IS THE MODIFIED FUNCTION
     */
    private fun checkBluetoothPermissions(): Boolean {
        // Determine the required permissions based on the Android version
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // For Android 12 (API 31) and higher
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE // <-- The crucial missing permission
            )
        } else {
            // For Android 11 (API 30) and lower
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        // Check if we already have all the required permissions
        val allPermissionsGranted = requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED
        }

        if (allPermissionsGranted) {
            Log.d("Permissions", "All Bluetooth permissions are already granted.")
            return true // Permissions are granted, proceed with the action
        } else {
            Log.d("Permissions", "Requesting permissions: ${requiredPermissions.joinToString()}")
            // Request the missing permissions
            bluetoothPermissionLauncher.launch(requiredPermissions)
            return false // Permissions are not yet granted
        }
    }

    private fun startBluetoothScan() {
        if (checkBluetoothPermissions()) { // Always check permissions before starting a scan
            if (bluetoothAdapter.isDiscovering) {
                bluetoothAdapter.cancelDiscovery()
            }

            discoveredDevices.clear()
            deviceAdapter.submitList(bluetoothAdapter.bondedDevices.toList()) // Show bonded devices immediately

            bluetoothAdapter.startDiscovery()
            binding.scanButton.isEnabled = false
        }
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
        // This is a placeholder for your actual transfer logic
        // You would likely use your BluetoothCommunicationProtocol here
        Toast.makeText(requireContext(), "Initiating transfer to ${device.name}", Toast.LENGTH_SHORT).show()
        // try {
        //     val result = user.sendDigitalEuroTo(device.name)
        //     Toast.makeText(requireContext(), result, Toast.LENGTH_SHORT).show()
        // } catch (e: Exception) {
        //     Toast.makeText(requireContext(), "Transfer failed: ${e.message}", Toast.LENGTH_SHORT).show()
        // }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireContext().unregisterReceiver(deviceDiscoveryReceiver)
        if (checkBluetoothPermissions()) { // Permissions needed to cancel discovery
            bluetoothAdapter.cancelDiscovery()
        }
        _binding = null
    }
}
