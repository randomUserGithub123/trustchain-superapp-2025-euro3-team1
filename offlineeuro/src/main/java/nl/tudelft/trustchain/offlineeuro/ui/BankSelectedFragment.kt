package nl.tudelft.trustchain.offlineeuro.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import nl.tudelft.trustchain.offlineeuro.R

class BankSelectedFragment : OfflineEuroBaseFragment(R.layout.fragment_user_bank_selected) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val bankName: String? = arguments?.getString("bankName")
        val userName: String? = arguments?.getString("userName")
        view.findViewById<TextView>(R.id.user_bank_selected_bank_name).text = bankName!!

    }
}
