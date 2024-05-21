package nl.tudelft.trustchain.offlineeuro.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.lifecycle.lifecycleScope
import mu.KotlinLogging
import nl.tudelft.ipv8.attestation.trustchain.BlockListener
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.trustchain.common.contacts.ContactStore
import nl.tudelft.trustchain.common.eurotoken.GatewayStore
import nl.tudelft.trustchain.common.ui.BaseFragment
import nl.tudelft.trustchain.offlineeuro.R

open class OfflineEuroBaseFragment(contentLayoutId: Int = 0) : BaseFragment(contentLayoutId) {
    protected val logger = KotlinLogging.logger {}

    private val contactStore by lazy {
        ContactStore.getInstance(requireContext())
    }

    private val gatewayStore by lazy {
        GatewayStore.getInstance(requireContext())
    }

    private val onReceiveListener =
        object : BlockListener {
            override fun onBlockReceived(block: TrustChainBlock) {
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launchWhenResumed {
        }
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onCreateOptionsMenu(
        menu: Menu,
        inflater: MenuInflater
    ) {
        inflater.inflate(R.menu.offlineeuro_options, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.verifyBalance -> {
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
