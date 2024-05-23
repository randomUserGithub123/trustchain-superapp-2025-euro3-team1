package nl.tudelft.trustchain.offlineeuro.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TableLayout
import android.widget.Toast
import nl.tudelft.trustchain.offlineeuro.R
import nl.tudelft.trustchain.offlineeuro.communication.IPV8CommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.PairingTypes
import nl.tudelft.trustchain.offlineeuro.db.AddressBookManager
import nl.tudelft.trustchain.offlineeuro.entity.TTP
import nl.tudelft.trustchain.offlineeuro.libraries.TableHelpers

class TTPHomeFragment : OfflineEuroBaseFragment(R.layout.fragment_ttp_home) {
    private lateinit var ttp: TTP
    private lateinit var iPV8CommunicationProtocol: IPV8CommunicationProtocol
    private lateinit var community: OfflineEuroCommunity

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)
        setSyncUserListButton(view)
        community = getIpv8().getOverlay<OfflineEuroCommunity>()!!
        val group = BilinearGroup(PairingTypes.FromFile, context = context)
        val addressBookManager = AddressBookManager(context, group)
        iPV8CommunicationProtocol = IPV8CommunicationProtocol(addressBookManager, community)
        ttp = TTP("TTP", iPV8CommunicationProtocol, context, group)
    }

    private fun updateUserList(view: View) {
        val table = view.findViewById<TableLayout>(R.id.ttp_home_userlist)
        val users = ttp.getRegisteredUsers()
        TableHelpers.removeAllButFirstRow(table)
        TableHelpers.addRegisteredUsersToTable(table, users)
    }

    private fun setSyncUserListButton(view: View) {
        val syncButton = view.findViewById<Button>(R.id.ttp_home_sync_user_button)
        val parentObject = this
        syncButton.setOnClickListener {
            Toast.makeText(context, "Syncing user list....", Toast.LENGTH_SHORT).show()
            parentObject.updateUserList(view)
            Toast.makeText(context, "Syncing done", Toast.LENGTH_SHORT).show()
        }
    }
}
