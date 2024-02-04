package nl.tudelft.trustchain.offlineeuro.ui

import android.app.ActionBar.LayoutParams
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.navigation.fragment.findNavController
import nl.tudelft.trustchain.offlineeuro.R
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
import nl.tudelft.trustchain.offlineeuro.entity.BankDetails
import nl.tudelft.trustchain.offlineeuro.entity.BankRegistration
import nl.tudelft.trustchain.offlineeuro.entity.User

class UserHomeFragment : OfflineEuroBaseFragment(R.layout.fragment_user_home) {

    private lateinit var user: User
    private lateinit var community: OfflineEuroCommunity

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        community = getIpv8().getOverlay<OfflineEuroCommunity>()!!
        user = community.user
        Toast.makeText(context, "Trying to find banks", Toast.LENGTH_SHORT).show()
        community.findBank()
        val wrapper = this
        val syncButton = view.findViewById<Button>(R.id.user_home_sync_banks)
        syncButton.setOnClickListener {
            if (user.getBankRegistrations().isEmpty()) {
                Toast.makeText(wrapper.context, "No valid Bank founds please try again later", Toast.LENGTH_SHORT).show()
                community.findBank()
            }
            else
            {
                wrapper.syncBankList(view)
            }
        }


    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun syncBankList(view: View) {
        val banks = user.getBankRegistrations()
        val bankList = view.findViewById<LinearLayout>(R.id.user_home_bank_list)

        clearOldList(bankList)

        for (bank: BankRegistration in banks) {
            val isLast = bank == banks.last()
            addBankToLayout(bank, bankList, isLast)
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun addBankToLayout(bank: BankRegistration, bankList: LinearLayout, isLast: Boolean) {

        val layoutWrapper = LinearLayout(context)
        layoutWrapper.layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dpToPixels(50f))
        layoutWrapper.orientation = LinearLayout.HORIZONTAL
        layoutWrapper.weightSum = 1f

        val bankIcon = LinearLayout(context)
        val tintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.black))
        ViewCompat.setBackgroundTintList(bankIcon, tintList)
        bankIcon.setBackgroundResource(R.drawable.ic_baseline_account_balance_24)
        bankIcon.layoutParams = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 0.2f)

        val textView = TextView(context)
        textView.layoutParams = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 0.5f)
        textView.text = bank.bankDetails.name
        textView.textAlignment = View.TEXT_ALIGNMENT_CENTER
        textView.textSize = 30f
        textView.gravity = Gravity.CENTER

        val button = Button(context)
        button.layoutParams = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 0.25f)
        button.text = "Register"
        val wrapperContext = context
        button.setOnClickListener {
            Toast.makeText(wrapperContext, "Register at ${bank.bankDetails.name}", Toast.LENGTH_SHORT).show()
            showAlertDialog(bank.bankDetails)
        }


        layoutWrapper.addView(bankIcon)
        layoutWrapper.addView(textView)
        layoutWrapper.addView(button)

        bankList.addView(layoutWrapper)
        if (!isLast) {

            val divider = View(context)
            val layout = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dpToPixels(1f))
            layout.setMargins(0, dpToPixels(2f), 0, dpToPixels(2f))
            divider.layoutParams = layout
            val backgroundColor = ContextCompat.getColor(requireContext(), R.color.dark_gray)
            divider.setBackgroundColor(backgroundColor)
            bankList.addView(divider)
        }

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
    private fun showAlertDialog(bankDetails: BankDetails) {
        val alertDialogBuilder = AlertDialog.Builder(requireContext())

        val editText = EditText(requireContext())
        alertDialogBuilder.setView(editText)
        alertDialogBuilder.setTitle("Register at ${bankDetails.name}")
        alertDialogBuilder.setMessage("")
        // Set positive button
        alertDialogBuilder.setPositiveButton("Register!") { dialog, which ->
            val userInput = editText.text.toString()
            user.registerWithBank(bankDetails.name, community, userInput)
            moveToBankSelected(userInput, bankDetails.name)
        }

        // Set negative button
        alertDialogBuilder.setNegativeButton("Cancel") { dialog, which ->
            dialog.cancel()
        }

        // Create and show the AlertDialog
        val alertDialog = alertDialogBuilder.create()
        alertDialog.show()
    }

    private fun moveToBankSelected(userName: String, bankName: String) {
        val bundle = bundleOf("userName" to userName, "bankName" to bankName)
        findNavController().navigate(R.id.nav_userHome_bankSelected, bundle)
    }


}
