//package nl.tudelft.trustchain.offlineeuro.ui
//
//import android.os.Build
//import android.os.Bundle
//import android.view.View
//import android.widget.Button
//import android.widget.CheckBox
//import android.widget.TextView
//import android.widget.Toast
//import androidx.annotation.RequiresApi
//import nl.tudelft.trustchain.offlineeuro.R
//import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
//import nl.tudelft.trustchain.offlineeuro.entity.CommunicationState
//
//@RequiresApi(Build.VERSION_CODES.O)
//class BankSelectedFragment : OfflineEuroBaseFragment(R.layout.fragment_user_bank_selected) {
//
//    val community: OfflineEuroCommunity = getIpv8().getOverlay<OfflineEuroCommunity>()!!
//    val user = community.user
//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        super.onViewCreated(view, savedInstanceState)
//
//        val bankName: String? = arguments?.getString("bankName")
//        val userName: String? = arguments?.getString("userName")
//        user.name = userName!!
//        user.removeAllTokens()
//        view.findViewById<TextView>(R.id.user_bank_selected_bank_name).text = bankName!!
//        val balanceTextView = view.findViewById<TextView>(R.id.user_bank_selected_balance)
//        val tokensReceivedTextView = view.findViewById<TextView>(R.id.user_bank_selected_tokens_received)
//
//        val withdrawButton = view.findViewById<Button>(R.id.user_bank_selected_withdraw)
//        withdrawButton.setOnClickListener {
//            Toast.makeText(context, "Starting withdrawal....", Toast.LENGTH_SHORT).show()
//            user.withdrawToken(bankName, community)
//
//            var maxLoops = 10
//            while (user.communicationState == CommunicationState.INPROGRESS) {
//                maxLoops -= 1
//
//                if (maxLoops == 0)
//                    break
//                Thread.sleep(500)
//            }
//            if (user.communicationState == CommunicationState.COMPLETE) {
//                Toast.makeText(context, "Withdrawal Succeeded", Toast.LENGTH_SHORT).show()
//                balanceTextView.text = "Number of tokens withdrawn: ${user.getBalance()}"
//            }
//            else {
//                Toast.makeText(context, "Withdrawal Failed", Toast.LENGTH_SHORT).show()
//            }
//
//        }
//
//        val sendRandomPeerButton = view.findViewById<Button>(R.id.user_bank_selected_random_peer)
//
//        sendRandomPeerButton.setOnClickListener {
//
//            if (user.getBalance() == 0) {
//                Toast.makeText(context, "No balance, withdraw first!", Toast.LENGTH_SHORT).show()
//                return@setOnClickListener
//            }
//
//            val checkBox = view.findViewById<CheckBox>(R.id.user_bank_selected_double_spend)
//            val sendSucceeded = user.sendTokenToRandomPeer(community, keepToken = checkBox.isChecked)
//
//
//            Toast.makeText(context, "Starting sending a token to a random peer....", Toast.LENGTH_SHORT).show()
//            var maxLoops = 10
//            while (user.communicationState == CommunicationState.INPROGRESS) {
//                maxLoops -= 1
//
//                if (maxLoops == 0)
//                    break
//                Thread.sleep(500)
//            }
//            if (user.communicationState == CommunicationState.COMPLETE) {
//                Toast.makeText(context, "Sending Tokens Completed", Toast.LENGTH_SHORT).show()
//                balanceTextView.text = "Number of tokens withdrawn: ${user.getBalance()}"
//            }
//            else {
//                Toast.makeText(context, "Sending Tokens Failed", Toast.LENGTH_SHORT).show()
//            }
//        }
//
//        val syncReceivedTokenButton = view.findViewById<Button>(R.id.user_bank_selected_sync_received_tokens)
//        syncReceivedTokenButton.setOnClickListener { syncReceivedTokens(tokensReceivedTextView) }
//
//        val depositTokensButton = view.findViewById<Button>(R.id.user_bank_selected_deposit_tokens)
//        depositTokensButton.setOnClickListener {
//            user.depositReceiptsAtBank(bankName, community)
//            syncReceivedTokens(tokensReceivedTextView)
//        }
//    }
//
//    fun syncReceivedTokens(tokensReceivedTextView: TextView) {
//        val receivedTokenCount = user.getReceipts().size
//        tokensReceivedTextView.text = "Number of tokens received: $receivedTokenCount"
//    }
//}
