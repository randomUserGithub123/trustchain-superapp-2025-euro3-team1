package nl.tudelft.trustchain.offlineeuro.ui

import android.os.Bundle
import android.view.View
import nl.tudelft.trustchain.offlineeuro.R
import nl.tudelft.trustchain.offlineeuro.communication.IPV8CommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.communication.BluetoothCommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.PairingTypes
import nl.tudelft.trustchain.offlineeuro.db.AddressBookManager
import nl.tudelft.trustchain.offlineeuro.db.DepositedEuroManager
import nl.tudelft.trustchain.offlineeuro.entity.Bank

class BankHomeFragment : OfflineEuroBaseFragment(R.layout.fragment_bank_home) {
    private lateinit var bank: Bank
    private lateinit var communicationProtocol: BluetoothCommunicationProtocol
    private lateinit var community: OfflineEuroCommunity

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        if (ParticipantHolder.bank != null) {
            bank = ParticipantHolder.bank!!
        } else {
            activity?.title = "Bank"
            community = getIpv8().getOverlay<OfflineEuroCommunity>()!!
            val group = BilinearGroup(PairingTypes.FromFile, context = context)
            val addressBookManager = AddressBookManager(context, group)
            val depositedEuroManager = DepositedEuroManager(context, group)

            bank =
                Bank(
                    "Bank",
                    group,
                    context,
                    depositedEuroManager,
                    onDataChangeCallback = onDataChangeCallBack
                )

            val communicationProtocol = BluetoothCommunicationProtocol(
                addressBookManager,
                community,
                requireContext(),
                bank
            )

            bank.communicationProtocol = communicationProtocol

            this.communicationProtocol = communicationProtocol

            bank.runBankSetup()
        }
        onDataChangeCallBack(null)
    }

    override fun onDestroyView() {
        super.onDestroyView()

        val protocol = bank.communicationProtocol
        if (protocol is BluetoothCommunicationProtocol) {
            protocol.stopServer()
        }

        ParticipantHolder.bank = null
    }

    private val onDataChangeCallBack: (String?) -> Unit = { message ->
        if (this::bank.isInitialized) {
            requireActivity().runOnUiThread {
                val context = requireContext()
                CallbackLibrary.bankCallback(context, message, requireView(), bank)
            }
        }
    }
}
