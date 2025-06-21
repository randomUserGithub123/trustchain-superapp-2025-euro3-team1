package nl.tudelft.trustchain.offlineeuro.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
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
        activity?.title = "Flexible role"
        community = getIpv8().getOverlay<OfflineEuroCommunity>()!!
        val group = BilinearGroup(PairingTypes.FromFile, context = context)
        val addressBookManager = AddressBookManager(context, group)
        iPV8CommunicationProtocol = IPV8CommunicationProtocol(addressBookManager, community)
        ttp = TTP("TTP", group, context, onDataChangeCallback = onTTPDataChangeCallback)

        bank = Bank("Bank", group, context, runSetup = false, onDataChangeCallback = onBankDataChangeCallBack)
        user =
            User(
                "TestUser",
                group,
                context,
                null,
                runSetup = false,
                onDataChangeCallback = onUserDataChangeCallBack
            )

        ttp.communicationProtocol = iPV8CommunicationProtocol
        bank.communicationProtocol = iPV8CommunicationProtocol
        user.communicationProtocol = iPV8CommunicationProtocol

        bank.group = ttp.group
        bank.crs = ttp.crs
        bank.generateKeyPair()

        user.group = ttp.group
        user.crs = ttp.crs

        ParticipantHolder.ttp = ttp
        ParticipantHolder.bank = bank
        ParticipantHolder.user = user

        iPV8CommunicationProtocol.participant = ttp

        ttp.registerUser(user.name, user.publicKey)
        ttp.registerUser(bank.name, bank.publicKey)

        iPV8CommunicationProtocol.addressBookManager.insertAddress(Address(bank.name, Role.Bank, bank.publicKey, null))
        iPV8CommunicationProtocol.addressBookManager.insertAddress(Address(user.name, Role.User, user.publicKey, null))
        iPV8CommunicationProtocol.addressBookManager.insertAddress(Address(ttp.name, Role.TTP, ttp.publicKey, null))

        prepareButtons(view)
        setTTPAsChild()
        updateUserList(view)
    }

    private fun prepareButtons(view: View) {
        val ttpButton = view.findViewById<Button>(R.id.all_roles_set_ttp)
        val bankButton = view.findViewById<Button>(R.id.all_roles_set_bank)
        val userButton = view.findViewById<Button>(R.id.all_roles_set_user)
        ttpButton.setOnClickListener {
            ttpButton.isEnabled = false
            bankButton.isEnabled = true
            userButton.isEnabled = true
            setTTPAsChild()
        }

        bankButton.setOnClickListener {
            ttpButton.isEnabled = true
            bankButton.isEnabled = false
            userButton.isEnabled = true
            setBankAsChild()
        }

        userButton.setOnClickListener {
            ttpButton.isEnabled = true
            bankButton.isEnabled = true
            userButton.isEnabled = false
            setUserAsChild()
        }
    }

    private fun setTTPAsChild() {
        iPV8CommunicationProtocol.participant = ttp
        val ttpFragment = TTPHomeFragment()
        childFragmentManager.beginTransaction()
            .replace(R.id.parent_fragment_container, ttpFragment)
            .commit()
        Toast.makeText(context, "Switched to TTP", Toast.LENGTH_SHORT).show()
    }

    private fun setBankAsChild() {
        iPV8CommunicationProtocol.participant = bank
        val bankFragment = BankHomeFragment()
        childFragmentManager.beginTransaction()
            .replace(R.id.parent_fragment_container, bankFragment)
            .commit()
        Toast.makeText(context, "Switched to Bank", Toast.LENGTH_SHORT).show()
    }

    private fun setUserAsChild() {
        iPV8CommunicationProtocol.participant = user
        val userFragment = UserHomeFragment()
        childFragmentManager.beginTransaction()
            .replace(R.id.parent_fragment_container, userFragment)
            .commit()
        Toast.makeText(context, "Switched to User", Toast.LENGTH_SHORT).show()
    }

    private val onBankDataChangeCallBack: (String?) -> Unit = { message ->
        if (this::bank.isInitialized) {
            requireActivity().runOnUiThread {
                CallbackLibrary.bankCallback(requireContext(), message, requireView(), bank)
            }
        }
    }

    private val onUserDataChangeCallBack: (String?) -> Unit = { message ->
        if (this::user.isInitialized) {
            requireActivity().runOnUiThread {
                val context = requireContext()
                CallbackLibrary.userCallback(
                    context,
                    message,
                    requireView(),
                    iPV8CommunicationProtocol,
                    user
                )
            }
        }
    }

    private val onTTPDataChangeCallback: (String?) -> Unit = { message ->
        if (this::ttp.isInitialized) {
            requireActivity().runOnUiThread {
                val context = requireContext()
                CallbackLibrary.ttpCallback(context, message, requireView(), ttp)
            }
        }
    }

    private fun updateUserList(view: View) {
        val table = view.findViewById<LinearLayout>(R.id.tpp_home_registered_user_list) ?: return
        val users = ttp.getRegisteredUsers()
        TableHelpers.removeAllButFirstRow(table)
        TableHelpers.addRegisteredUsersToTable(table, users)
    }
}
