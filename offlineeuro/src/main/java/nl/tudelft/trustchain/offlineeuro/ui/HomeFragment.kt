package nl.tudelft.trustchain.offlineeuro.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import nl.tudelft.trustchain.offlineeuro.R
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity

class HomeFragment : OfflineEuroBaseFragment(R.layout.fragment_home) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.JoinAsBankButton).setOnClickListener {
            Toast.makeText(context, "Pressed Bank", Toast.LENGTH_LONG).show()
        }

        view.findViewById<Button>(R.id.JoinAsUserButton).setOnClickListener {
            Toast.makeText(context, "Pressed User", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
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
        } catch (e : Exception) {
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

