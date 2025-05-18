package nl.tudelft.trustchain.offlineeuro.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import nl.tudelft.trustchain.offlineeuro.communication.BluetoothCommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.R
import nl.tudelft.trustchain.offlineeuro.entity.User
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.PairingTypes
import nl.tudelft.trustchain.offlineeuro.cryptography.CRSGenerator

class UserHomeFragment : OfflineEuroBaseFragment(R.layout.fragment_user_home) {

    private lateinit var user: User
    private lateinit var balanceText: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val name = arguments?.getString("userName") ?: run {
            Toast.makeText(context, "Username is missing", Toast.LENGTH_LONG).show()
            return
        }

        val withdrawButton = view.findViewById<Button>(R.id.bluetooth_withdraw_button)
        val sendButton = view.findViewById<Button>(R.id.bluetooth_send_button)
        balanceText = view.findViewById(R.id.user_home_balance)

        user = ParticipantHolder.user ?: run {
            try {
                val group = BilinearGroup(PairingTypes.FromFile, context = context)
                val protocol = BluetoothCommunicationProtocol(requireContext())

                val newUser = User(
                    name = name,
                    group = group,
                    context = context,
                    communicationProtocol = protocol,
                    runSetup = true,
                    onDataChangeCallback = onUserDataChangeCallback
                )

                ParticipantHolder.user = newUser
                newUser
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "User init failed: ${e.message}", Toast.LENGTH_LONG).show()
                throw e
            }
        }

        updateBalance()
        Toast.makeText(context, "Welcome, $name! Now trying to register at TTP", Toast.LENGTH_SHORT).show()

        withdrawButton.setOnClickListener {
            try {
                user.withdrawDigitalEuro("Bank")
                updateBalance()
                Toast.makeText(context, "Withdraw successful", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Withdraw failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        sendButton.setOnClickListener {
            try {
                val receiverName = "Receiver"
                user.sendDigitalEuroTo(receiverName)
                updateBalance()
                Toast.makeText(context, "Sent 1 euro", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Send failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        val protocol = user.communicationProtocol
        if (protocol is BluetoothCommunicationProtocol) {
            protocol.stopServer()
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
