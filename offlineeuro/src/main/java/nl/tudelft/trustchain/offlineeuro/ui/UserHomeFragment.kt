package nl.tudelft.trustchain.offlineeuro.ui

import kotlin.concurrent.thread
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
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
    private lateinit var bloomFilterSizeText: TextView
    private lateinit var bloomFilterElementsText: TextView
    private lateinit var bloomFilterFalsePositiveText: TextView
    private lateinit var bloomFilterCurrentFalsePositiveText: TextView
    private lateinit var bloomFilterRawStateText: TextView
    private lateinit var bloomFilterEstimatedElementsText: TextView

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
        bloomFilterSizeText = view.findViewById(R.id.bloom_filter_size)
        bloomFilterElementsText = view.findViewById(R.id.bloom_filter_elements)
        bloomFilterFalsePositiveText = view.findViewById(R.id.bloom_filter_false_positive)
        bloomFilterCurrentFalsePositiveText = view.findViewById(R.id.bloom_filter_current_false_positive)
        bloomFilterRawStateText = view.findViewById(R.id.bloom_filter_raw_state)
        bloomFilterEstimatedElementsText = view.findViewById(R.id.bloom_filter_estimated_elements)

        try {
            if (ParticipantHolder.user != null) {
                user = ParticipantHolder.user!!
                communicationProtocol = user.communicationProtocol as BluetoothCommunicationProtocol
                updateBloomFilterStats()
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
                updateBloomFilterStats()
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
                        updateBloomFilterStats()
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
                        Toast.makeText(requireContext(), "Sent 1 euro", Toast.LENGTH_SHORT).show()
                        updateBloomFilterStats()
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

    private fun updateBloomFilterStats() {
        val bloomFilter = user.getBloomFilter()
        bloomFilterSizeText.text = "Size: ${bloomFilter.getBitArraySize()} bytes"
        bloomFilterElementsText.text = "Expected Elements: ${bloomFilter.expectedElements}"
        bloomFilterFalsePositiveText.text = "False Positive Rate: ${(bloomFilter.falsePositiveRate * 100)}%"
        bloomFilterCurrentFalsePositiveText.text = "Current False Positive Rate: ${"%.5f".format(
            bloomFilter.getCurrentFalsePositiveRate() * 100
        )}%"
        bloomFilterRawStateText.text = "Raw Bloom Filter: ${bloomFilter.toHexString()}"
        bloomFilterEstimatedElementsText.text = "Estimated Elements: ${"%.2f".format(bloomFilter.getApproximateElementCount())}"
    }

    private val onUserDataChangeCallBack: (String?) -> Unit = { message ->
        if (this::user.isInitialized) {
            requireActivity().runOnUiThread {
                message?.let {
                    Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                }
                updateBloomFilterStats()
                updateBalance()
            }
        }
    }
}
