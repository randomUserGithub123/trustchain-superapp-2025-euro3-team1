package nl.tudelft.trustchain.offlineeuro

import nl.tudelft.trustchain.common.BaseActivity

class OfflineEuroMainActivity : BaseActivity() {

    override val navigationGraph = R.navigation.nav_graph_offlineeuro
    override val bottomNavigationMenu = R.menu.offlineeuro_navigation_menu

    /**
     * The values for shared preferences used by this activity.
     */
    object EurotokenPreferences {
        const val EUROTOKEN_SHARED_PREF_NAME = "eurotoken"
        const val DEMO_MODE_ENABLED = "demo_mode_enabled"
    }
}
