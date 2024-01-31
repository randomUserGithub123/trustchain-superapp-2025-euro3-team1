package nl.tudelft.trustchain.offlineeuro.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import nl.tudelft.trustchain.offlineeuro.R
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
import nl.tudelft.trustchain.offlineeuro.entity.User

class UserHomeFragment : OfflineEuroBaseFragment(R.layout.fragment_user_home) {

    private lateinit var user: User
    private lateinit var community: OfflineEuroCommunity

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        community = getIpv8().getOverlay<OfflineEuroCommunity>()!!
        user = community.user
        Toast.makeText(context, "Trying to find banks", Toast.LENGTH_SHORT).show()
        community.findBank()
        val parentContext = context
        val registerButton = view.findViewById<Button>(R.id.user_home_register_button)
        registerButton.setOnClickListener {
//            val toast: Toast = if (user.bankRegistrationManager.getBanks.isEmpty()) {
//                Toast.makeText(parentContext, "No valid Bank found", Toast.LENGTH_SHORT)
//            } else {
//                val bank = user.banks.values.first()
//                Toast.makeText(parentContext, "Could register at ${bank.second.name}", Toast.LENGTH_SHORT)
//            }
//
//            toast.show()

        }
    }






}
