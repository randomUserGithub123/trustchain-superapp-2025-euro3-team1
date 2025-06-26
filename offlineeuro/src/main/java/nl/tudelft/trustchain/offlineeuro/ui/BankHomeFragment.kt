package nl.tudelft.trustchain.offlineeuro.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import nl.tudelft.trustchain.offlineeuro.R
import nl.tudelft.trustchain.offlineeuro.communication.BluetoothCommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.PairingTypes
import nl.tudelft.trustchain.offlineeuro.db.AddressBookManager
import nl.tudelft.trustchain.offlineeuro.db.DepositedEuroManager
import nl.tudelft.trustchain.offlineeuro.entity.Bank

class BankHomeFragment : OfflineEuroBaseFragment(R.layout.fragment_bank_home) {
    private lateinit var bank: Bank
    private lateinit var bloomFilterSizeText: TextView
    private lateinit var bloomFilterElementsText: TextView
    private lateinit var bloomFilterFalsePositiveText: TextView
    private lateinit var bloomFilterCurrentFalsePositiveText: TextView
    private lateinit var bloomFilterEstimatedElementsText: TextView
    private lateinit var bloomFilterRawStateText: TextView
    private lateinit var communicationProtocol: BluetoothCommunicationProtocol
    private lateinit var community: OfflineEuroCommunity
    private val TAG = "BankHomeFragment_DEBUG" // Tag for filtering logs

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        if (ParticipantHolder.bank != null) {
            bank = ParticipantHolder.bank!!
        } else {
            activity?.title = "Bank"
            community = getIpv8().getOverlay<OfflineEuroCommunity>()!!

            val group = BilinearGroup(PairingTypes.FromFile, context = context)
            val addressBookManager = AddressBookManager(context, group)
            val depositedEuroManager = DepositedEuroManager(context, group)
            communicationProtocol =
                BluetoothCommunicationProtocol(
                    addressBookManager,
                    community,
                    requireContext()
                )
            bank =
                Bank(
                    "Bank",
                    group,
                    communicationProtocol,
                    context,
                    depositedEuroManager,
                    onDataChangeCallback = onDataChangeCallback
                )
        }

        bloomFilterSizeText = view.findViewById(R.id.bloom_filter_size)
        bloomFilterElementsText = view.findViewById(R.id.bloom_filter_elements)
        bloomFilterFalsePositiveText = view.findViewById(R.id.bloom_filter_false_positive)
        bloomFilterCurrentFalsePositiveText = view.findViewById(R.id.bloom_filter_current_false_positive)
        bloomFilterEstimatedElementsText = view.findViewById(R.id.bloom_filter_estimated_elements)
        bloomFilterRawStateText = view.findViewById(R.id.bloom_filter_raw_state)

        onDataChangeCallback(null)
        updateBloomFilterStats()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        val protocol = bank.communicationProtocol
        if (protocol is BluetoothCommunicationProtocol) {
            protocol.stopServer()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!requireActivity().isChangingConfigurations) {
            ParticipantHolder.bank = null
        }
    }


    fun updateBloomFilterStats() {
        Log.d(TAG, "updateBloomFilterStats: Function CALLED.")
        val bloomFilter = bank.getBloomFilter()
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

    private val onDataChangeCallback: (String?) -> Unit = { message ->
        if (this::bank.isInitialized) {
            requireActivity().runOnUiThread {
                val context = requireContext()
                CallbackLibrary.bankCallback(context, message, requireView(), bank)
                updateBloomFilterStats()
            }
        }
    }
}
