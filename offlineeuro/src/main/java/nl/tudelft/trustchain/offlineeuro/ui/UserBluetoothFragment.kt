package nl.tudelft.trustchain.offlineeuro.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import nl.tudelft.trustchain.offlineeuro.communication.BluetoothCommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.R
import nl.tudelft.trustchain.offlineeuro.entity.User
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.PairingTypes
import nl.tudelft.trustchain.offlineeuro.cryptography.CRSGenerator

class UserBluetoothFragment : OfflineEuroBaseFragment(R.layout.fragment_user_bluetooth) {

    private lateinit var user: User
    private lateinit var balanceText: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        user = ParticipantHolder.user ?: run {

            try {
                val name = arguments?.getString("userName") ?: "BluetoothUser"
                val group = BilinearGroup(PairingTypes.FromFile, context = context)
                val protocol = BluetoothCommunicationProtocol(requireContext())

                val (crs, _) = CRSGenerator.generateCRSMap(group)

                val newUser = User(
                    name = name,
                    group = group,
                    context = context,
                    communicationProtocol = protocol,
                    runSetup = true,
                    onDataChangeCallback = onUserDataChangeCallback
                )
                newUser.crs = crs
                newUser.generateKeyPair()

                ParticipantHolder.user = newUser
                newUser
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "User init failed: ${e.message}", Toast.LENGTH_LONG).show()
                throw e
            }
            
        }

        balanceText = view.findViewById(R.id.bluetooth_balance)
        updateBalance()

        view.findViewById<Button>(R.id.bluetooth_withdraw_button).setOnClickListener {
            try {
                user.withdrawDigitalEuro("Bank")
                updateBalance()
                Toast.makeText(context, "Withdraw successful", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Withdraw failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<Button>(R.id.bluetooth_send_button).setOnClickListener {
            try {
                // Placeholder: in future this would open a device picker or Bluetooth logic
                val receiverName = "Receiver" // or fetched from UI in future
                user.sendDigitalEuroTo(receiverName)
                updateBalance()
                Toast.makeText(context, "Sent 1 euro", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Send failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateBalance() {
        val balance = user.getBalance()
        balanceText.text = "Balance: $balance"
    }

    private val onUserDataChangeCallback: (String?) -> Unit = { message ->
        requireActivity().runOnUiThread {
            message?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
