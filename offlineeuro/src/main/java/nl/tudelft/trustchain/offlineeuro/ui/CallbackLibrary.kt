package nl.tudelft.trustchain.offlineeuro.ui

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import nl.tudelft.trustchain.offlineeuro.R
import nl.tudelft.trustchain.offlineeuro.communication.IPV8CommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.entity.Bank
import nl.tudelft.trustchain.offlineeuro.entity.TTP
import nl.tudelft.trustchain.offlineeuro.entity.User

object CallbackLibrary {
    fun bankCallback(
        context: Context,
        message: String?,
        view: View,
        bank: Bank
    ) {
        if (message != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
        val table = view.findViewById<LinearLayout>(R.id.bank_home_deposited_list)
        TableHelpers.removeAllButFirstRow(table)
        TableHelpers.addDepositedEurosToTable(table, bank)
    }

    fun ttpCallback(
        context: Context,
        message: String?,
        view: View,
        ttp: TTP
    ) {
        if (message != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
        updateUserList(view, ttp)
    }

    // In CallbackLibrary.kt

    private fun updateUserList(
        view: View,
        ttp: TTP
    ) {
        val table = view.findViewById<LinearLayout>(R.id.tpp_home_registered_user_list) ?: return
        Log.d("UI_DEBUG", "1. Before clearing, the table has ${table.childCount} children.")

        // This is the function you suspect is failing
        TableHelpers.removeAllRows(table)

        Log.d("UI_DEBUG", "2. After clearing, the table should have 1 child (the Fheader). It has: ${table.childCount}")

        val users = ttp.getRegisteredUsers()
        Log.d("UI_DEBUG", "3. Database has ${users.size} users. Adding them now.")

        TableHelpers.addRegisteredUsersToTable(table, users)

        Log.d("UI_DEBUG", "4. After adding, the table has ${table.childCount} children.")
    }

    fun userCallback(
        context: Context,
        message: String?,
        view: View,
        communicationProtocol: IPV8CommunicationProtocol,
        user: User
    ) {
        if (message != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
        val balanceField = view.findViewById<TextView>(R.id.user_home_balance)
        balanceField.text = user.getBalance().toString()
        // val addressList = view.findViewById<LinearLayout>(R.id.user_home_addresslist)
        // val addresses = communicationProtocol.addressBookManager.getAllAddresses()
        // TableHelpers.addAddressesToTable(addressList, addresses, user, context)
        view.refreshDrawableState()
    }
}
