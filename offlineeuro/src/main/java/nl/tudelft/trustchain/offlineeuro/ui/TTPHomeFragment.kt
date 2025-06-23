package nl.tudelft.trustchain.offlineeuro.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import nl.tudelft.trustchain.offlineeuro.R
import nl.tudelft.trustchain.offlineeuro.communication.BluetoothCommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.communication.IPV8CommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.PairingTypes
import nl.tudelft.trustchain.offlineeuro.db.AddressBookManager
import nl.tudelft.trustchain.offlineeuro.entity.TTP

class TTPHomeFragment : OfflineEuroBaseFragment(R.layout.fragment_ttp_home) {
    private lateinit var ttp: TTP
    private lateinit var bloomFilterSizeText: TextView
    private lateinit var bloomFilterElementsText: TextView
    private lateinit var bloomFilterFalsePositiveText: TextView
    private lateinit var bloomFilterCurrentFalsePositiveText: TextView
    private lateinit var bloomFilterEstimatedElementsText: TextView
    private lateinit var bloomFilterRawStateText: TextView
    private var communicationProtocol: Any? = null
    private lateinit var community: OfflineEuroCommunity

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        if (ParticipantHolder.ttp != null) {
            ttp = ParticipantHolder.ttp!!
        } else {
            activity?.title = "TTP"
            community = getIpv8().getOverlay<OfflineEuroCommunity>()!!

            val group = BilinearGroup(PairingTypes.FromFile, context = context)
            val addressBookManager = AddressBookManager(context, group)

            communicationProtocol = IPV8CommunicationProtocol(addressBookManager, community)

            ttp =
                TTP(
                    name = "TTP",
                    group = group,
                    communicationProtocol = communicationProtocol as nl.tudelft.trustchain.offlineeuro.communication.ICommunicationProtocol,
                    context = context,
                    onDataChangeCallback = onDataChangeCallback
                )

            ParticipantHolder.ttp = ttp
        }

        bloomFilterSizeText = view.findViewById(R.id.bloom_filter_size)
        bloomFilterElementsText = view.findViewById(R.id.bloom_filter_elements)
        bloomFilterFalsePositiveText = view.findViewById(R.id.bloom_filter_false_positive)
        bloomFilterCurrentFalsePositiveText = view.findViewById(R.id.bloom_filter_current_false_positive)
        bloomFilterEstimatedElementsText = view.findViewById(R.id.bloom_filter_estimated_elements)
        bloomFilterRawStateText = view.findViewById(R.id.bloom_filter_raw_state)

        updateBloomFilterStats()
        onDataChangeCallback(null)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (communicationProtocol is BluetoothCommunicationProtocol) {
            (communicationProtocol as BluetoothCommunicationProtocol).stopServer()
        }
    }

    private fun updateBloomFilterStats() {
        val bloomFilter = ttp.getBloomFilter()
        bloomFilterSizeText.text = "Size: ${bloomFilter.getBitArraySize()} bytes"
        bloomFilterElementsText.text = "Expected Elements: ${bloomFilter.expectedElements}"
        bloomFilterFalsePositiveText.text = "False Positive Rate: ${(bloomFilter.falsePositiveRate * 100)}%"
                bloomFilterCurrentFalsePositiveText.text = "Current False Positive Rate: ${"%.5f".format(
            bloomFilter.getCurrentFalsePositiveRate() * 100
        )}%"
        bloomFilterRawStateText.text = "Raw Bloom Filter: ${bloomFilter.toHexString()}"
        bloomFilterEstimatedElementsText.text = "Estimated Elements: ${"%.2f".format(bloomFilter.getApproximateElementCount())}"
    }

    private val onDataChangeCallback: (String?) -> Unit = { message ->
        if (this::ttp.isInitialized) {
            requireActivity().runOnUiThread {
                val context = requireContext()
                CallbackLibrary.ttpCallback(context, message, requireView(), ttp)
                updateBloomFilterStats()
            }
        }
    }
}
