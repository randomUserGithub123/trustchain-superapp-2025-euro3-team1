package nl.tudelft.trustchain.offlineeuro.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TableLayout
import android.widget.Toast
import nl.tudelft.trustchain.offlineeuro.R
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
import nl.tudelft.trustchain.offlineeuro.libraries.TableHelpers

class BankHomeFragment : OfflineEuroBaseFragment(R.layout.fragment_bank_home) {


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setSyncUserListButton(view)
        super.onViewCreated(view, savedInstanceState)
    }

    private fun updateUserList(view: View) {
        val table = view.findViewById<TableLayout>(R.id.bank_home_userlist)
        val bank = getIpv8().getOverlay<OfflineEuroCommunity>()!!.bank
        val users = bank.getRegisteredUsers()
        TableHelpers.removeAllButFirstRow(table)
        TableHelpers.addRegisteredUsersToTable(table, users)
    }

    private fun setSyncUserListButton(view: View) {
        val syncButton = view.findViewById<Button>(R.id.bank_home_sync_user_button)
        val parentObject = this
        syncButton.setOnClickListener {
            Toast.makeText(context, "Syncing user list....", Toast.LENGTH_SHORT).show()
            parentObject.updateUserList(view)
            Toast.makeText(context, "Syncing done", Toast.LENGTH_SHORT).show()
        }
    }
}
