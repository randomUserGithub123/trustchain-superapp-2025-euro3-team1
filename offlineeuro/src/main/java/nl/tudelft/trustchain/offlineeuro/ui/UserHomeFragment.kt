package nl.tudelft.trustchain.offlineeuro.ui

import kotlin.concurrent.thread
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import nl.tudelft.trustchain.offlineeuro.communication.BluetoothCommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
import nl.tudelft.trustchain.offlineeuro.R
import nl.tudelft.trustchain.offlineeuro.communication.NotRightServiceException
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
    private val TAG = "UserHomeFragment_DEBUG"

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
        val depositButton = view.findViewById<Button>(R.id.bluetooth_deposit_button)
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
                var isWithdrawalSuccessful = false
                val maxRetries = 5 // Set a limit to avoid getting stuck forever
                var attempts = 0

                while (!isWithdrawalSuccessful && attempts < maxRetries) {
                    attempts++
                    try {
                        val protocol = user.communicationProtocol
                        if (protocol is BluetoothCommunicationProtocol) {
                            // This finds a new device on each loop if the last one failed
                            requireActivity().runOnUiThread {
                                val message = if (attempts == 1) "Searching for bank..." else "Wrong device, searching again... (Attempt $attempts)"
                                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                            }
                            if (!protocol.startSession()) {
                                throw Exception("No nearby devices found.") // No devices found, stop trying
                            }
                        }

                        user.withdrawDigitalEuro("Bank")
                        isWithdrawalSuccessful = true
                        requireActivity().runOnUiThread {
                            updateBalance()
                            Toast.makeText(requireContext(), "Withdraw successful", Toast.LENGTH_LONG).show()
                        }

                    } catch (e: NotRightServiceException) {
                        Log.w("WithdrawProcess", "Attempt $attempts: Connected to a non-bank peer. Retrying...")
                    } catch (e: Exception) {
                        // This is for all other, non-recoverable errors (no connection, etc.)
                        Log.e("WithdrawProcess", "Withdrawal failed with a fatal error on attempt $attempts", e)
                        requireActivity().runOnUiThread {
                            Toast.makeText(requireContext(), "Withdraw failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                        // Stop the loop since this is a fatal error
                        break
                    } finally {
                        val protocol = user.communicationProtocol
                        if (protocol is BluetoothCommunicationProtocol) {
                            protocol.endSession()
                        }
                    }
                }

                // Check if the loop finished because of too many retries
                if (!isWithdrawalSuccessful && attempts >= maxRetries) {
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "Could not find the bank after $maxRetries attempts.", Toast.LENGTH_LONG).show()
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

        depositButton.setOnClickListener {
            thread {
                var isDepositSuccessful = false
                val maxRetries = 5 // Set a limit to avoid getting stuck forever
                var attempts = 0

                while (!isDepositSuccessful && attempts < maxRetries) {
                    attempts++
                    try {
                        val protocol = user.communicationProtocol
                        if (protocol is BluetoothCommunicationProtocol) {
                            // This finds a new device on each loop if the last one failed
                            requireActivity().runOnUiThread {
                                val message = if (attempts == 1) "Searching for bank to deposit..." else "Wrong device, searching again... (Attempt $attempts)"
                                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                            }
                            if (!protocol.startSession()) {
                                throw Exception("No nearby devices found.") // No devices found, stop trying
                            }
                        }

                        user.depositDigitalEuro("Bank")
                        isDepositSuccessful = true

                    } catch (e: NotRightServiceException) {
                        Log.w("DepositProcess", "Attempt $attempts: Connected to a non-bank peer. Retrying...")

                    } catch (e: Exception) {
                        // This is for all other, non-recoverable errors (no connection, etc.)
                        Log.e("DepositProcess", "Deposit failed with a fatal error on attempt $attempts", e)
                        requireActivity().runOnUiThread {
                            Toast.makeText(requireContext(), "Deposit failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                        break
                    } finally {
                        val protocol = user.communicationProtocol
                        if (protocol is BluetoothCommunicationProtocol) {
                            protocol.endSession()
                        }
                    }
                }

                // Check if the loop finished because of too many retries
                if (!isDepositSuccessful && attempts >= maxRetries) {
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "Could not find the bank after $maxRetries attempts.", Toast.LENGTH_LONG).show()
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

    override fun onDestroy() {
        super.onDestroy()
        if (!requireActivity().isChangingConfigurations) {
            ParticipantHolder.user = null
        }
    }

    private fun updateBalance() {
        val balance = user.getBalance()
        balanceText.text = "Balance: $balance"
    }

    private fun updateBloomFilterStats() {
        Log.d(TAG, "updateBloomFilterStats: Function CALLED.")
        val bloomFilter = user.getBloomFilter()
        val rawFilterHex = bloomFilter.toHexString()
        Log.d(TAG, "updateBloomFilterStats: Raw filter content is '$rawFilterHex'")

        bloomFilterSizeText.text = "Size: ${bloomFilter.getBitArraySize()} bytes"
        bloomFilterElementsText.text = "Expected Elements: ${bloomFilter.expectedElements}"
        bloomFilterFalsePositiveText.text = "False Positive Rate: ${(bloomFilter.falsePositiveRate * 100)}%"
        bloomFilterCurrentFalsePositiveText.text = "Current False Positive Rate: ${"%.5f".format(
            bloomFilter.getCurrentFalsePositiveRate() * 100
        )}%"
        bloomFilterRawStateText.text = "Raw Bloom Filter: $rawFilterHex"
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
