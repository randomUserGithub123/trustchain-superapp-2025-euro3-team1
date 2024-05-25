package nl.tudelft.trustchain.offlineeuro.ui

import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import nl.tudelft.trustchain.offlineeuro.R
import nl.tudelft.trustchain.offlineeuro.communication.IPV8CommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.PairingTypes
import nl.tudelft.trustchain.offlineeuro.db.AddressBookManager
import nl.tudelft.trustchain.offlineeuro.entity.Bank
import nl.tudelft.trustchain.offlineeuro.entity.User
import nl.tudelft.trustchain.offlineeuro.libraries.TableHelpers

class UserHomeFragment : OfflineEuroBaseFragment(R.layout.fragment_user_home) {
    private lateinit var user: User
    private lateinit var community: OfflineEuroCommunity
    private lateinit var communicationProtocol: IPV8CommunicationProtocol

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        community = getIpv8().getOverlay<OfflineEuroCommunity>()!!
        val group = BilinearGroup(PairingTypes.FromFile, context = context)
        val addressBookManager = AddressBookManager(context, group)
        communicationProtocol = IPV8CommunicationProtocol(addressBookManager, community)
        user = User("Name", group, context, null, communicationProtocol)
        communicationProtocol.scopePeers()
        // syncBankList(view)

        view.findViewById<Button>(R.id.user_home_sync_addresses).setOnClickListener {
            syncBankList(view)
            Toast.makeText(context, "Syncing users....", Toast.LENGTH_SHORT).show()
        }
    }

    fun syncBankList(view: View) {
        communicationProtocol.scopePeers()
        val bankList = view.findViewById<TableLayout>(R.id.user_home_entity_list2)
        clearOldList(bankList)
        val addresses = communicationProtocol.addressBookManager.getAllAddresses()
        TableHelpers.addAddressesToTable(bankList, addresses, user)
    }

    private fun clearOldList(list: LinearLayout) {
        val childrenCount = list.childCount
        val childrenToBeRemoved = childrenCount - 1

        for (i in childrenToBeRemoved downTo 1) {
            val row = list.getChildAt(i)
            list.removeView(row)
        }
    }

    fun dpToPixels(dp: Float): Int {
        val scale = resources.displayMetrics.density
        return (dp * scale + 0.5f).toInt()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun showAlertDialog(bankDetails: Bank) {
        val alertDialogBuilder = AlertDialog.Builder(requireContext())

        val editText = EditText(requireContext())
        alertDialogBuilder.setView(editText)
        alertDialogBuilder.setTitle("Register at ${bankDetails.name}")
        alertDialogBuilder.setMessage("")
        // Set positive button
        alertDialogBuilder.setPositiveButton("Register!") { dialog, which ->
            editText.text.toString()
        }

        // Set negative button
        alertDialogBuilder.setNegativeButton("Cancel") { dialog, which ->
            dialog.cancel()
        }

        // Create and show the AlertDialog
        val alertDialog = alertDialogBuilder.create()
        alertDialog.show()
    }

    private fun moveToBankSelected(
        userName: String,
        bankName: String
    ) {
        val bundle = bundleOf("userName" to userName, "bankName" to bankName)
        findNavController().navigate(R.id.nav_userHome_bankSelected, bundle)
    }
}
