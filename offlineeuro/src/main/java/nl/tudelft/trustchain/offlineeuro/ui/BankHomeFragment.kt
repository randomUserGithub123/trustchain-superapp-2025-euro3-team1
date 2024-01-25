package nl.tudelft.trustchain.offlineeuro.ui

import android.os.Bundle
import nl.tudelft.trustchain.offlineeuro.R
import nl.tudelft.trustchain.offlineeuro.db.RegisteredUserManager

class BankHomeFragment : OfflineEuroBaseFragment(R.layout.fragment_bank_home) {

    override fun onCreate(savedInstanceState: Bundle?) {
        val test = RegisteredUserManager(context)
        test.getUserCount()
        super.onCreate(savedInstanceState)
    }
}
