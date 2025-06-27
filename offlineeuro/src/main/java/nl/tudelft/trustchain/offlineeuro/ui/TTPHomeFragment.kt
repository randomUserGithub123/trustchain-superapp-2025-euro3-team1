package nl.tudelft.trustchain.offlineeuro.ui

import android.content.ContentValues.TAG
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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

    private var userListNeedsUpdate = false

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

        updateBloomFilterStats()
        onDataChangeCallback(null)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (communicationProtocol is BluetoothCommunicationProtocol) {
            (communicationProtocol as BluetoothCommunicationProtocol).stopServer()
        }
    }

    override fun onResume() {
        super.onResume()
        // If an update was flagged while the fragment was not visible, apply it now.
        if (userListNeedsUpdate) {
            Log.d(TAG, "Fragment resumed, applying pending UI update for user list.")
            updateUserList(requireView(), ttp)
            userListNeedsUpdate = false // Reset the flag
        }
    }

    private fun updateUserList(view: View, ttp: TTP) {
        if (!isAdded) return // Safety check

        val table = view.findViewById<LinearLayout>(R.id.tpp_home_registered_user_list) ?: return
        Log.d("UI_DEBUG", "1. Before clearing, the table has ${table.childCount} children.")

        TableHelpers.removeAllRows(table)

        Log.d("UI_DEBUG", "2. After clearing, the table should have 1 child (the header). It has: ${table.childCount}")

        val users = ttp.getRegisteredUsers()
        Log.d("UI_DEBUG", "3. Database has ${users.size} users. Adding them now.")

        TableHelpers.addRegisteredUsersToTable(table, users)

        Log.d("UI_DEBUG", "4. After adding, the table has ${table.childCount} children.")
    }



    private fun updateBloomFilterStats() {
        Log.d(TAG, "updateBloomFilterStats: Function CALLED.")
        val bloomFilter = ttp.getBloomFilter()
        val rawFilterHex = bloomFilter.toHexString()
        Log.d(TAG, "updateBloomFilterStats: Raw filter content is '$rawFilterHex'")
    }

    private val onDataChangeCallback: (String?) -> Unit = { message ->

        if (isAdded) {
            requireActivity().runOnUiThread {
                if (this::ttp.isInitialized) {
                    message?.let {
                        Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                    }
                    // The UI is ready, so update it now.
                    updateUserList(requireView(), ttp)
                    updateBloomFilterStats()
                }
            }
        } else {
            // The fragment is not visible. Flag that an update is needed.
            userListNeedsUpdate = true
            Log.w(TAG, "onDataChangeCallback triggered, but Fragment is not attached. Flagging for update on resume.")
        }
    }
}
