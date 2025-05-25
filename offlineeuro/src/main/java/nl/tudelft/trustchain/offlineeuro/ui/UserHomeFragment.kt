package nl.tudelft.trustchain.offlineeuro.ui

import kotlin.concurrent.thread
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
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
import nl.tudelft.trustchain.offlineeuro.R
import nl.tudelft.trustchain.offlineeuro.entity.User
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.PairingTypes
import nl.tudelft.trustchain.offlineeuro.cryptography.CRSGenerator
import nl.tudelft.trustchain.offlineeuro.db.AddressBookManager

class UserHomeFragment : OfflineEuroBaseFragment(R.layout.fragment_user_home) {

    private lateinit var user: User
    private lateinit var balanceText: TextView

    private lateinit var communicationProtocol: BluetoothCommunicationProtocol
    private lateinit var community: OfflineEuroCommunity

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val userName = arguments?.getString("userName") ?: run {
            Toast.makeText(context, "Username is missing", Toast.LENGTH_LONG).show()
            return
        }

        val withdrawButton = view.findViewById<Button>(R.id.bluetooth_withdraw_button)
        val sendButton = view.findViewById<Button>(R.id.bluetooth_send_button)
        balanceText = view.findViewById(R.id.user_home_balance)

        try{
            if (ParticipantHolder.user != null) {
                user = ParticipantHolder.user!!
                communicationProtocol = user.communicationProtocol as BluetoothCommunicationProtocol
            } else {
                
                community = getIpv8().getOverlay<OfflineEuroCommunity>()!!

                val group = BilinearGroup(PairingTypes.FromFile, context = context)
                val addressBookManager = AddressBookManager(context, group)
                communicationProtocol = BluetoothCommunicationProtocol(
                    addressBookManager, 
                    community,
                    requireContext()
                )
                user = User(
                    userName, 
                    group, 
                    context, 
                    null, 
                    communicationProtocol, 
                    onDataChangeCallback = onUserDataChangeCallBack
                )
                // communicationProtocol.scopePeers()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "User init failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }

        updateBalance()
        Toast.makeText(context, "Welcome, $userName! Now trying to register at TTP", Toast.LENGTH_SHORT).show()

        withdrawButton.setOnClickListener {
            thread {
                try {
                    
                    val protocol = user.communicationProtocol
                    if (protocol is BluetoothCommunicationProtocol) {
                        if (!protocol.startSession()) {
                            throw Exception("startSession() ERROR")
                        }
                    }

                    user.withdrawDigitalEuro("Bank")

                    requireActivity().runOnUiThread {
                        updateBalance()
                        Toast.makeText(requireContext(), "Withdraw successful", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "Withdraw failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    val protocol = user.communicationProtocol
                    if (protocol is BluetoothCommunicationProtocol) {
                        protocol.endSession()
                    }
                }
            }
        }

        sendButton.setOnClickListener {
            thread {
                try {
                    
                    val protocol = user.communicationProtocol
                    if (protocol is BluetoothCommunicationProtocol) {
                        if (!protocol.startSession()) {
                            throw Exception("startSession() ERROR")
                        }
                    }

                    val receiverName = "Receiver"
                    user.sendDigitalEuroTo(receiverName)
                    updateBalance()
                    Toast.makeText(context, "Sent 1 euro", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "Send failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    val protocol = user.communicationProtocol
                    if (protocol is BluetoothCommunicationProtocol) {
                        protocol.endSession()
                    }
                }
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

    private val onUserDataChangeCallBack: (String?) -> Unit = { message ->
        requireActivity().runOnUiThread {
            message?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
