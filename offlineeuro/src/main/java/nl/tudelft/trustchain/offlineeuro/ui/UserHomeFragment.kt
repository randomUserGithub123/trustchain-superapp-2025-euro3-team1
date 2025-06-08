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
import nl.tudelft.trustchain.offlineeuro.CallbackLibrary

class UserHomeFragment : OfflineEuroBaseFragment(R.layout.fragment_user_home) {
    private lateinit var user: User
    private lateinit var balanceText: TextView
    private lateinit var bloomFilterSizeText: TextView
    private lateinit var bloomFilterElementsText: TextView
    private lateinit var bloomFilterFalsePositiveText: TextView
    private lateinit var bloomFilterCurrentFalsePositiveText: TextView

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

        try {
            if (ParticipantHolder.user != null) {
                user = ParticipantHolder.user!!
                communicationProtocol = user.communicationProtocol as BluetoothCommunicationProtocol
                updateBloomFilterStats()
            } else {
                community = getIpv8().getOverlay<OfflineEuroCommunity>()!!
                val group = BilinearGroup(PairingTypes.FromFile, context = context)
                val addressBookManager = AddressBookManager(context, group)
                communicationProtocol = BluetoothCommunicationProtocol(addressBookManager, community, context)

                user = User(
                    userName,
                    group,
                    context,
                    communicationProtocol = communicationProtocol,
                    onDataChangeCallback = onDataChangeCallback
                )
                ParticipantHolder.user = user
                updateBloomFilterStats()
            }

            withdrawButton.setOnClickListener {
                thread {
                    try {
                        user.withdrawEuro()
                    } catch (e: Exception) {
                        requireActivity().runOnUiThread {
                            Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }

            sendButton.setOnClickListener {
                thread {
                    try {
                        user.sendDigitalEuroTo(userName)
                    } catch (e: Exception) {
                        requireActivity().runOnUiThread {
                            Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
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
        bloomFilterFalsePositiveText.text = "False Positive Rate: ${(bloomFilter.falsePositiveRate * 100).toInt()}%"
        bloomFilterCurrentFalsePositiveText.text = "Current False Positive Rate: ${(bloomFilter.getCurrentFalsePositiveRate() * 100).toInt()}%"
    }

    private val onDataChangeCallback: (String?) -> Unit = { message ->
        if (this::user.isInitialized) {
            requireActivity().runOnUiThread {
                message?.let {
                    Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                }
                updateBloomFilterStats()
            }
        }
    }
}
