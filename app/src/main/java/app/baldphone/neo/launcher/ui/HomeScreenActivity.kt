package app.baldphone.neo.launcher.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognizerIntent
import android.util.Log
import android.view.View

import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle

import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import app.baldphone.neo.R
import app.baldphone.neo.activities.BaseActivity
import app.baldphone.neo.data.Prefs
import app.baldphone.neo.extensions.applyTopBarInsets
import app.baldphone.neo.extensions.ensureValidSurface
import app.baldphone.neo.features.notifications.ui.NotificationsActivity
import app.baldphone.neo.launcher.apps.data.AppsRepository
import app.baldphone.neo.launcher.ui.topbar.BatteryIconView
import app.baldphone.neo.launcher.ui.topbar.FlashlightButton
import app.baldphone.neo.launcher.ui.topbar.NotificationsButton
import app.baldphone.neo.launcher.ui.topbar.SoundButton
import app.baldphone.neo.permissions.PermissionManager
import app.baldphone.neo.permissions.PermissionRepository
import app.baldphone.neo.settings.ui.SettingsActivity
import app.baldphone.neo.ui.dialogs.BaldSnackbar
import app.baldphone.neo.ui.dialogs.showErrorSnackbar
import app.baldphone.neo.utils.HomeAppUtils
import app.baldphone.neo.utils.startActivitySafe

import com.bald.uriah.baldphone.adapters.BaldPagerAdapter
import com.bald.uriah.baldphone.views.ViewPagerHolder
import com.bald.uriah.baldphone.views.home.NotesView

class HomeScreenActivity : BaseActivity() {
    val recognizerManager = NotesView.RecognizerManager()

    var baldPagerAdapter: BaldPagerAdapter? = null
        private set

    private var viewPagerHolder: ViewPagerHolder? = null

    private val speechRecognizerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val spokenText =
                    result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                        ?: return@registerForActivityResult
                recognizerManager.onSpeechRecognizerResult(spokenText)
            }
        }

    private var lastBackPressedTime = 0L
    private var isFirstResume = true
    private var permissionBannerDismissed = false
    private var permissionBanner: View? = null
    private var initialPrefsSnapshot: PrefsSnapshot? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate")

        installSplashScreen()
        super.onCreate(savedInstanceState)

        initialPrefsSnapshot = PrefsSnapshot.capture()

        setContentView(R.layout.home_screen)
        setupViews()
        observeStateFlows()
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart")

        // Handle prefs that might change while we were stopped
        if (initialPrefsSnapshot != null && initialPrefsSnapshot != PrefsSnapshot.capture()) {
            recreate()
            return
        }
    }

    override fun onResume() {
        super.onResume()
        Log.v(TAG, "onResume")

        if (isFinishing || isDestroyed) return

        // User may have returned from Settings after granting missing permissions.
        if (!permissionBannerDismissed) {
            PermissionRepository.refresh()
        }

        if (isFirstResume) {
            isFirstResume = false
            permissionBanner?.visibility = View.GONE
            lifecycleScope.launch {
                delay(PERMISSION_BANNER_DELAY.milliseconds)
                showPermissionBanner()
            }
        } else {
            showPermissionBanner()
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        // Workaround for invalid surface on some devices (e.g. Xiaomi) — deferred to let
        // the window settle before checking.
        // ensureValidSurface() guards against stale state.
        lifecycleScope.launch {
            delay(SURFACE_CHECK_DELAY.milliseconds)
            ensureValidSurface()
        }
    }

    override fun onPause() {
        Log.d(TAG, "onPause")
        super.onPause()
    }

    override fun onStop() {
        Log.d(TAG, "onStop")
        super.onStop()
    }

    override fun onDestroy() {
        recognizerManager.setHomeScreen(null)
        viewPagerHolder = null
        baldPagerAdapter = null
        permissionBanner = null
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (isFinishing || isDestroyed) return

        setIntent(intent)
        val isHomeLaunch = isHomeLaunch(intent)
        if (isHomeLaunch) {
            updateViewPager(animate = true, resetToHome = true)
        }
    }

    fun displaySpeechRecognizer() {
        try {
            speechRecognizerLauncher.launch(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
            )
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "Speech recognizer not found: ${e.message}")
            showErrorSnackbar(R.string.no_app_was_found)
        }
    }

    private fun setupViews() {
        viewPagerHolder = findViewById(R.id.view_pager_holder)

        val topBar = findViewById<View>(R.id.top_bar)
        if (resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) {
            topBar.applyTopBarInsets()
        }
        setupTopBarButtons()

        val adapter = BaldPagerAdapter(this)
        baldPagerAdapter = adapter
        viewPagerHolder?.run {
            setViewPagerAdapter(adapter)
            setCurrentItem(adapter.startingPage)
        }

        recognizerManager.setHomeScreen(this)
        setupPermissionBanner()

        onBackPressedDispatcher.addCallback(this) {
            Log.v(TAG, "onBackPressed")
            handleBackPressed(this)
        }
    }

    private fun handleBackPressed(callback: OnBackPressedCallback) {
        val holder = viewPagerHolder
        val adapter = baldPagerAdapter

        if (holder == null || adapter == null) {
            callback.isEnabled = false
            onBackPressedDispatcher.onBackPressed()
            callback.isEnabled = true
            return
        }

        if (holder.viewPager.currentItem != adapter.startingPage) {
            holder.setCurrentItem(adapter.startingPage)
            return
        }

        val isHomeApp = HomeAppUtils.isDefaultLauncher(this)
        if (!isHomeApp) {
            val currentTime = SystemClock.elapsedRealtime()
            if (currentTime - lastBackPressedTime < BACK_PRESS_TIMEOUT) {
                finish()
            } else {
                lastBackPressedTime = currentTime
                BaldSnackbar.show(
                    this,
                    R.string.press_back_again_to_exit,
                    BaldSnackbar.TYPE_INFO,
                    BaldSnackbar.LENGTH_LONG
                )
            }
        }
    }

    private fun observeStateFlows() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    AppsRepository.pinnedAppsFlow.collect {
                        updateViewPager(animate = false, resetToHome = false)
                    }
                }
            }
        }
    }

    /**
     * Refreshes [baldPagerAdapter] app list.
     * Optionally resets the visible page to [BaldPagerAdapter.startingPage].
     */
    private fun updateViewPager(animate: Boolean, resetToHome: Boolean) {
        val adapter = baldPagerAdapter ?: return
        val holder = viewPagerHolder ?: return

        adapter.obtainAppList()
        if (resetToHome) {
            holder.viewPager.setCurrentItem(adapter.startingPage, animate)
        }
        holder.onDataChanged()
    }

    private fun setupTopBarButtons() {
        val battery = findViewById<BatteryIconView>(R.id.battery)
        val flash = findViewById<FlashlightButton>(R.id.flash)
        val sound = findViewById<SoundButton>(R.id.sound)
        val notifications = findViewById<NotificationsButton>(R.id.notifications)

        battery.observeBatteryState(this)
        battery.setOnClickListener {
            BaldSnackbar.show(
                this,
                battery.detailedContentDescription,
                BaldSnackbar.TYPE_INFO,
                BaldSnackbar.LENGTH_LONG
            )
        }

        flash.bind(this) { onGranted ->
            PermissionManager.checkOrRequest(this, PermissionManager.CAMERA) { result ->
                if (result == PermissionManager.GRANTED) {
                    onGranted.run()
                }
            }
        }

        sound.bind(this)

        notifications.bind(this)
        notifications.setOnClickListener {
            startActivitySafe(Intent(this, NotificationsActivity::class.java))
        }
    }

    private fun setupPermissionBanner() {
        val banner = findViewById<View>(R.id.permission_banner) ?: return
        permissionBanner = banner

        banner.findViewById<View>(R.id.permission_banner_text).setOnClickListener {
            startActivity(
                Intent(
                    this,
                    SettingsActivity::class.java
                ).setData("myapp://settings/system/permissions".toUri())
            )
        }
        banner.findViewById<View>(R.id.permission_banner_close).setOnClickListener {
            banner.visibility = View.GONE
            permissionBannerDismissed = true
        }
    }

    private fun showPermissionBanner() {
        val banner = permissionBanner ?: return
        val hasPermission = PermissionRepository.isMandatoryGranted(this)
        if (hasPermission) {
            banner.visibility = View.GONE
        } else if (!permissionBannerDismissed) {
            banner.visibility = View.VISIBLE
        }
    }

    /** Snapshot for detecting background preference changes */
    private data class PrefsSnapshot(
        val accessibilityLevel: Int,
        val noteVisible: Boolean
    ) {
        companion object {
            fun capture(): PrefsSnapshot =
                PrefsSnapshot(
                    accessibilityLevel = Prefs.accessibilityLevel.value,
                    noteVisible = Prefs.noteVisible
                )
        }
    }

    companion object {
        private val TAG = HomeScreenActivity::class.java.simpleName

        private const val BACK_PRESS_TIMEOUT = 3000L
        private const val PERMISSION_BANNER_DELAY = 2000L
        private const val SURFACE_CHECK_DELAY = 500L
    }
}

/** Returns `true` if the [intent] was launched from the Home button. */
private fun isHomeLaunch(intent: Intent?): Boolean {
    val isHome = intent?.hasCategory(Intent.CATEGORY_HOME) == true
    val source =
        when {
            intent == null -> "null intent"
            isHome -> "HOME"
            intent.action == Intent.ACTION_MAIN -> "LAUNCHER"
            else -> "UNKNOWN"
        }
    Log.d(HomeScreenActivity::class.java.simpleName, "launchSource: $source")
    return isHome
}
