package nl.tudelft.trustchain.offlineeuro.ui

import kotlin.concurrent.thread
import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import nl.tudelft.trustchain.offlineeuro.communication.NfcCommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
import nl.tudelft.trustchain.offlineeuro.R
import nl.tudelft.trustchain.offlineeuro.entity.User
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.PairingTypes
import nl.tudelft.trustchain.offlineeuro.db.AddressBookManager
import nl.tudelft.trustchain.offlineeuro.ui.ParticipantHolder

/**
 * UserHomeFragment now enables NFC‐reader mode and implements NfcAdapter.ReaderCallback.
 * When Android discovers a Tag (i.e. when the remote reader taps this device),
 * onTagDiscovered(...) fires and calls attachTag(...) on our NfcCommunicationProtocol.
 *
 * The rest of the UI logic remains identical: buttons call startSession()/endSession()
 * just as before, but under the hood APDUs go over NFC instead of Bluetooth.
 */
class UserHomeFragment : OfflineEuroBaseFragment(R.layout.fragment_user_home),
    NfcAdapter.ReaderCallback
{

    private lateinit var user: User
    private lateinit var balanceText: TextView

    private lateinit var communicationProtocol: NfcCommunicationProtocol
    private lateinit var community: OfflineEuroCommunity

    // NFC adapter reference for enabling reader mode:
    private var nfcAdapter: NfcAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize NFC adapter for this activity:
        nfcAdapter = NfcAdapter.getDefaultAdapter(requireContext())

        val userName = arguments?.getString("userName") ?: run {
            Toast.makeText(context, "Username is missing", Toast.LENGTH_LONG).show()
            return
        }

        val withdrawButton = view.findViewById<Button>(R.id.bluetooth_withdraw_button)
        val sendButton     = view.findViewById<Button>(R.id.bluetooth_send_button)
        balanceText        = view.findViewById(R.id.user_home_balance)

        try {
            if (ParticipantHolder.user != null) {
                user = ParticipantHolder.user!!
                communicationProtocol = user.communicationProtocol as NfcCommunicationProtocol
            } else {
                community = getIpv8().getOverlay<OfflineEuroCommunity>()!!

                val group = BilinearGroup(PairingTypes.FromFile, context = context)
                val addressBookManager = AddressBookManager(requireContext(), group)

                // Exactly same constructor signature as Bluetooth version:
                communicationProtocol = NfcCommunicationProtocol(
                    addressBookManager,
                    community,
                    requireContext()
                )
                user = User(
                    userName,
                    group,
                    context,
                    null,
                    communicationProtocol,
                    onDataChangeCallback = onUserDataChangeCallBack
                )
                // If you used scopePeers() previously, you can still call it here:
                // communicationProtocol.scopePeers()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "User init failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }

        updateBalance()
        Toast.makeText(context, "Welcome, $userName! Now trying to register at TTP", Toast.LENGTH_SHORT).show()

        withdrawButton.setOnClickListener {
            thread {
                try {
                    val protocol = user.communicationProtocol
                    if (protocol is NfcCommunicationProtocol) {
                        if (!protocol.startSession()) {
                            throw Exception("startSession() ERROR (no Tag attached)")
                        }
                    }

                    user.withdrawDigitalEuro("Bank")

                    requireActivity().runOnUiThread {
                        updateBalance()
                        Toast.makeText(requireContext(), "Withdraw successful", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "Withdraw failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    val protocol = user.communicationProtocol
                    if (protocol is NfcCommunicationProtocol) {
                        protocol.endSession()
                    }
                }
            }
        }

        sendButton.setOnClickListener {
            thread {
                try {
                    val protocol = user.communicationProtocol
                    if (protocol is NfcCommunicationProtocol) {
                        if (!protocol.startSession()) {
                            throw Exception("startSession() ERROR (no Tag attached)")
                        }
                    }

                    val receiverName = "Receiver"
                    user.sendDigitalEuroTo(receiverName)

                    requireActivity().runOnUiThread {
                        updateBalance()
                        Toast.makeText(requireContext(), "Sent 1 euro", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "Send failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    val protocol = user.communicationProtocol
                    if (protocol is NfcCommunicationProtocol) {
                        protocol.endSession()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableReaderMode(
            requireActivity() as Activity,
            this,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null
        )
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(requireActivity() as Activity)
    }

    override fun onTagDiscovered(tag: Tag) {
        try {
            communicationProtocol.attachTag(tag)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "onTagDiscovered FAILED: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        val protocol = user.communicationProtocol
        if (protocol is NfcCommunicationProtocol) {
            protocol.endSession()
        }

        ParticipantHolder.user = null
    }

    private fun updateBalance() {
        val balance = user.getBalance()
        balanceText.text = "Balance: $balance"
    }

    private val onUserDataChangeCallBack: (String?) -> Unit = { message ->
        requireActivity().runOnUiThread {
            message?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
            }
            if (this::user.isInitialized) {
                updateBalance()
            }
        }
    }
}
