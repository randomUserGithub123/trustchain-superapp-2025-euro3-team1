package nl.tudelft.trustchain.offlineeuro.ui

import kotlin.concurrent.thread
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.EditText
import android.widget.Switch
import android.widget.Toast
import nl.tudelft.trustchain.offlineeuro.communication.BluetoothCommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
import nl.tudelft.trustchain.offlineeuro.R
import nl.tudelft.trustchain.offlineeuro.entity.User
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.PairingTypes
import nl.tudelft.trustchain.offlineeuro.db.AddressBookManager

class UserHomeFragment : OfflineEuroBaseFragment(R.layout.fragment_user_home) {
    private lateinit var user: User
    private lateinit var balanceText: TextView

    private lateinit var dummyToggle: Switch
    private lateinit var dummySizeInput: EditText

    private lateinit var statsSentView: TextView
    private lateinit var statsReceivedView: TextView

    private lateinit var communicationProtocol: BluetoothCommunicationProtocol
    private lateinit var community: OfflineEuroCommunity

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        val userName =
            arguments?.getString("userName") ?: run {
                Toast.makeText(context, "Username is missing", Toast.LENGTH_LONG).show()
                return
            }

        val withdrawButton = view.findViewById<Button>(R.id.bluetooth_withdraw_button)
        val sendButton = view.findViewById<Button>(R.id.bluetooth_send_button)
        balanceText = view.findViewById(R.id.user_home_balance)

        dummyToggle       = view.findViewById(R.id.dummy_toggle)
        dummySizeInput    = view.findViewById(R.id.dummy_size_input)
        statsSentView     = view.findViewById(R.id.stats_sent)
        statsReceivedView = view.findViewById(R.id.stats_received)

        try {
            if (ParticipantHolder.user != null) {
                user = ParticipantHolder.user!!
                communicationProtocol = user.communicationProtocol as BluetoothCommunicationProtocol
            } else {
                community = getIpv8().getOverlay<OfflineEuroCommunity>()!!

                val group = BilinearGroup(PairingTypes.FromFile, context = context)
                val addressBookManager = AddressBookManager(context, group)
                communicationProtocol =
                    BluetoothCommunicationProtocol(
                        addressBookManager,
                        community,
                        requireContext()
                    )
                user =
                    User(
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
        updateStats()
        Toast.makeText(context, "Welcome, $userName! Now trying to register at TTP", Toast.LENGTH_SHORT).show()

        dummyToggle.setOnCheckedChangeListener { _, isChecked ->
            val kb = dummySizeInput.text.toString().toIntOrNull() ?: 0
            communicationProtocol.dummySizeKb = if (isChecked) kb else 0
        }
        dummySizeInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = Unit
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val kb = s?.toString()?.toIntOrNull() ?: 0
                communicationProtocol.dummySizeKb = if (dummyToggle.isChecked) kb else 0
            }
        })

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
                        updateStats()
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
                    requireActivity().runOnUiThread {
                        updateBalance()
                        updateStats()
                        Toast.makeText(requireContext(), "Sent 1 euro", Toast.LENGTH_SHORT).show()
                    }
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

        ParticipantHolder.user = null
    }

    private fun updateBalance() {
        val balance = user.getBalance()
        balanceText.text = "Balance: $balance"
    }

    private fun updateStats() {
        val sent     = communicationProtocol.bytesSent
        val received = communicationProtocol.bytesReceived
        statsSentView.text     = "Total sent: $sent B"
        statsReceivedView.text = "Total recv: $received B"
    }

    private val onUserDataChangeCallBack: (String?) -> Unit = { message ->
        requireActivity().runOnUiThread {
            message?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
            }
            if (this::user.isInitialized) {
                updateBalance()
                updateStats()
            }
        }
    }
}
