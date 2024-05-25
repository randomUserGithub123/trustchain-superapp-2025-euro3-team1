package nl.tudelft.trustchain.offlineeuro.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import nl.tudelft.trustchain.offlineeuro.R
import nl.tudelft.trustchain.offlineeuro.communication.IPV8CommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.PairingTypes
import nl.tudelft.trustchain.offlineeuro.db.AddressBookManager
import nl.tudelft.trustchain.offlineeuro.entity.Address
import nl.tudelft.trustchain.offlineeuro.entity.Bank
import nl.tudelft.trustchain.offlineeuro.entity.TTP
import nl.tudelft.trustchain.offlineeuro.entity.User
import nl.tudelft.trustchain.offlineeuro.enums.Role

class AllRolesFragment : OfflineEuroBaseFragment(R.layout.fragment_all_roles_home) {
    private lateinit var iPV8CommunicationProtocol: IPV8CommunicationProtocol
    private lateinit var community: OfflineEuroCommunity

    private lateinit var ttp: TTP
    private lateinit var bank: Bank
    private lateinit var user: User

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        community = getIpv8().getOverlay<OfflineEuroCommunity>()!!
        val group = BilinearGroup(PairingTypes.FromFile, context = context)
        val addressBookManager = AddressBookManager(context, group)
        iPV8CommunicationProtocol = IPV8CommunicationProtocol(addressBookManager, community)
        ttp = TTP("TTP", iPV8CommunicationProtocol, context, group)

        bank = Bank("Bank", group, iPV8CommunicationProtocol, context, runSetup = false)
        user = User("TestUser", group, context, null, iPV8CommunicationProtocol, runSetup = false)

        bank.group = ttp.group
        bank.crs = ttp.crs
        bank.generateKeyPair()

        user.group = ttp.group
        user.crs = ttp.crs

        iPV8CommunicationProtocol.participant = ttp

        iPV8CommunicationProtocol.addressBookManager.insertAddress(Address(bank.name, Role.Bank, bank.publicKey, null))
        iPV8CommunicationProtocol.addressBookManager.insertAddress(Address(user.name, Role.User, user.publicKey, null))
        view.findViewById<Button>(R.id.all_roles_set_ttp).setOnClickListener {
            iPV8CommunicationProtocol.participant = ttp
            Toast.makeText(context, "Switched to TTP", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<Button>(R.id.all_roles_set_bank).setOnClickListener {
            iPV8CommunicationProtocol.participant = bank
            Toast.makeText(context, "Switched to Bank", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<Button>(R.id.all_roles_set_user).setOnClickListener {
            iPV8CommunicationProtocol.participant = user
            Toast.makeText(context, "Switched to User", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        try {
            val euroTokenCommunity = getIpv8().getOverlay<OfflineEuroCommunity>()
            if (euroTokenCommunity == null) {
                Toast.makeText(requireContext(), "Could not find community", Toast.LENGTH_LONG)
                    .show()
            }
            if (euroTokenCommunity != null) {
                Toast.makeText(requireContext(), "Found community", Toast.LENGTH_LONG)
                    .show()
            }
        } catch (e: Exception) {
            logger.error { e }
            Toast.makeText(
                requireContext(),
                "Failed to send transactions",
                Toast.LENGTH_LONG
            )
                .show()
        }
        return
    }
}
