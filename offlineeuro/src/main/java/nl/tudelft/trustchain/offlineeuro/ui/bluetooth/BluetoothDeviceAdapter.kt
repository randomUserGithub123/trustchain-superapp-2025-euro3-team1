package nl.tudelft.trustchain.offlineeuro.ui.bluetooth

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import nl.tudelft.trustchain.offlineeuro.databinding.ItemBluetoothDeviceBinding

class BluetoothDeviceAdapter(
    private val onSendClick: (BluetoothDevice) -> Unit
) : ListAdapter<BluetoothDevice, BluetoothDeviceAdapter.ViewHolder>(DeviceDiffCallback()) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding =
            ItemBluetoothDeviceBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemBluetoothDeviceBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(device: BluetoothDevice) {
            val context = binding.root.context
            val deviceName =
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    device.name ?: "Unknown Device"
                } else {
                    "Unknown Device"
                }

            binding.deviceName.text = deviceName
            binding.deviceAddress.text = device.address
            binding.sendButton.setOnClickListener { onSendClick(device) }
        }
    }

    private class DeviceDiffCallback : DiffUtil.ItemCallback<BluetoothDevice>() {
        override fun areItemsTheSame(
            oldItem: BluetoothDevice,
            newItem: BluetoothDevice
        ): Boolean {
            return oldItem.address == newItem.address
        }

        override fun areContentsTheSame(
            oldItem: BluetoothDevice,
            newItem: BluetoothDevice
        ): Boolean {
            return oldItem.address == newItem.address
        }
    }
}
