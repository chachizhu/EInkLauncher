package com.motion.einklauncher

import android.app.Activity
import android.app.ActivityOptions
import android.app.Dialog
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputFilter
import android.text.TextUtils
import android.util.StateSet
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.util.Date

class MainActivity : Activity() {
    private lateinit var preferences: LauncherPreferences
    private lateinit var appsRepository: LaunchableAppsRepository
    private lateinit var updateVerifier: UpdatePackageVerifier
    private lateinit var updateInstaller: SystemUpdateInstaller
    private lateinit var iconCache: AppIconCache

    internal var updateClientFactory: () -> UpdateClient = { GitHubReleaseClient() }

    private var currentScreen = Screen.HOME
    private var firstRunManagement = false
    private var managementRefreshWhenResumed = false
    private var homeRefreshWhenResumed = false
    private var homePage = 0
    private var homePageCount = 1
    private var availablePage = 0
    private var cachedApps: List<LaunchableApp> = emptyList()
    private var homeApps: List<LaunchableApp> = emptyList()

    private var batteryView: TextView? = null
    private var datePrefixView: TextView? = null
    private var timeView: TextView? = null
    private var wifiView: ImageView? = null
    private var homeStatusView: LinearLayout? = null
    private var homeManagementButton: ImageView? = null
    private var homeAppsContainer: LinearLayout? = null
    private var homePagerSpacingView: View? = null
    private var homePageIndicatorView: TextView? = null
    private var previousHomePageButton: Button? = null
    private var nextHomePageButton: Button? = null
    private var selectedContainer: LinearLayout? = null
    private var selectedCountView: TextView? = null
    private var availableContainer: LinearLayout? = null
    private var pageIndicatorView: TextView? = null
    private var previousPageButton: Button? = null
    private var nextPageButton: Button? = null
    private var defaultLauncherContainer: LinearLayout? = null
    private var firstRunHintView: TextView? = null
    private var homeTextSizeValueView: TextView? = null
    private var homeGridContainer: LinearLayout? = null
    private val displayPresetButtons = mutableMapOf<DisplayPreset, Button>()
    private val homeClockModeButtons = mutableMapOf<HomeClockMode, Button>()
    private var updateContainer: LinearLayout? = null
    private var homeRoot: FrameLayout? = null
    private var homeErrorView: View? = null
    private var renameDialog: Dialog? = null
    private var renameDialogInput: EditText? = null
    private var renameDialogComponent: ComponentName? = null
    private var defaultLauncherErrorVisible = false

    private var currentTime = ""
    private var currentDate = ""
    private var currentBattery = ""
    private var currentCharging = false
    private var currentWifi: WifiIndicator = WifiIndicator.HIDDEN
    private var statusReceiverRegistered = false

    private val interSemiBold: Typeface by lazy { assetTypeface(INTER_SEMI_BOLD_ASSET_PATH) }

    private var installedVersionName = "?"
    private var updateState = UpdateState.IDLE
    private var availableUpdate: UpdateRelease? = null
    private var verifiedUpdate: VerifiedUpdate? = null
    private var activeUpdateClient: UpdateClient? = null
    private var activeUpdateThread: Thread? = null
    private var updateGeneration = 0L
    private var waitingForInstallPermission = false

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_BATTERY_CHANGED -> updateBattery(intent)
                Intent.ACTION_TIME_TICK,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_DATE_CHANGED,
                -> updateClock()
                WifiManager.WIFI_STATE_CHANGED_ACTION,
                WifiManager.NETWORK_STATE_CHANGED_ACTION,
                -> updateWifiState()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureWindow()
        preferences = LauncherPreferences(this)
        appsRepository = LaunchableAppsRepository(this)
        updateVerifier = UpdatePackageVerifier(this)
        updateInstaller = SystemUpdateInstaller(this)
        iconCache = AppIconCache(this)
        installedVersionName = try {
            updateVerifier.currentVersionName()
        } catch (_: UpdateException) {
            "?"
        }

        homePage = savedInstanceState?.getInt(STATE_HOME_PAGE) ?: 0
        availablePage = savedInstanceState?.getInt(STATE_AVAILABLE_PAGE) ?: 0
        val restoredScreen = savedInstanceState
            ?.getString(STATE_SCREEN)
            ?.let { saved -> Screen.entries.firstOrNull { it.name == saved } }
        val restoredRenameComponent = savedInstanceState
            ?.getString(STATE_RENAME_COMPONENT)
            ?.let(ComponentName::unflattenFromString)
        val restoredRenameDraft = savedInstanceState?.getString(STATE_RENAME_DRAFT)

        if (preferences.isFirstRun()) {
            showManagement(isFirstRun = true)
        } else if (restoredScreen == Screen.MANAGEMENT) {
            showManagement(isFirstRun = false)
        } else {
            showHome()
        }
        if (
            currentScreen == Screen.MANAGEMENT &&
            restoredRenameComponent != null &&
            restoredRenameComponent in preferences.selectedComponents()
        ) {
            val systemLabel = cachedApps
                .firstOrNull { it.componentName == restoredRenameComponent }
                ?.label
                ?: restoredRenameComponent.packageName
            showRenameAppDialog(
                component = restoredRenameComponent,
                systemLabel = systemLabel,
                initialDraft = restoredRenameDraft,
            )
        }
    }

    override fun onStart() {
        super.onStart()
        registerStatusReceiver()
    }

    override fun onResume() {
        super.onResume()
        hideSystemStatusBar()
        if (homeRefreshWhenResumed && currentScreen == Screen.HOME) {
            homeRefreshWhenResumed = false
            refreshHomeAppsIfChanged()
        }
        if (managementRefreshWhenResumed && currentScreen == Screen.MANAGEMENT) {
            managementRefreshWhenResumed = false
            cachedApps = appsRepository.loadApps()
            renderDefaultLauncherSection()
            renderSelectedApps()
            renderAvailableApps()
        }
        if (waitingForInstallPermission && currentScreen == Screen.MANAGEMENT) {
            waitingForInstallPermission = false
            updateState = if (updateInstaller.canRequestInstall()) {
                UpdateState.READY
            } else {
                UpdateState.INSTALL_PERMISSION_REQUIRED
            }
            renderUpdateSection()
        }
    }

    override fun onStop() {
        cancelActiveUpdateTask(restoreIdleState = true)
        if (currentScreen == Screen.MANAGEMENT) {
            managementRefreshWhenResumed = true
        } else {
            homeRefreshWhenResumed = true
        }
        unregisterStatusReceiver()
        super.onStop()
    }

    override fun onDestroy() {
        cancelActiveUpdateTask(restoreIdleState = false)
        renameDialog?.dismiss()
        renameDialog = null
        renameDialogInput = null
        renameDialogComponent = null
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        managementRefreshWhenResumed = false
        if (preferences.isFirstRun()) {
            showManagement(isFirstRun = true)
        } else if (currentScreen != Screen.HOME) {
            showHome()
        } else {
            hideSystemStatusBar()
            updateClock()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_SCREEN, currentScreen.name)
        outState.putInt(STATE_HOME_PAGE, homePage)
        outState.putInt(STATE_AVAILABLE_PAGE, availablePage)
        renameDialogComponent?.let { component ->
            outState.putString(STATE_RENAME_COMPONENT, component.flattenToString())
            outState.putString(STATE_RENAME_DRAFT, renameDialogInput?.text?.toString().orEmpty())
        }
        super.onSaveInstanceState(outState)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemStatusBar()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (currentScreen == Screen.MANAGEMENT) {
            if (firstRunManagement) {
                finishManagement()
            } else {
                showHome()
            }
        }
        // HOME is the root surface: Back intentionally does nothing here.
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val pageDelta = when (keyCode) {
            KeyEvent.KEYCODE_PAGE_UP -> -1
            KeyEvent.KEYCODE_PAGE_DOWN -> 1
            else -> 0
        }
        val isPaginationScreen = currentScreen == Screen.HOME || currentScreen == Screen.MANAGEMENT
        if (pageDelta != 0 && isPaginationScreen) {
            if (event?.repeatCount == 0) {
                if (currentScreen == Screen.HOME) {
                    changeHomePage(pageDelta, requestFocusOnFirstItem = true)
                } else {
                    val canChangePage = if (pageDelta < 0) {
                        previousPageButton?.isEnabled == true
                    } else {
                        nextPageButton?.isEnabled == true
                    }
                    if (canChangePage) {
                        availablePage += pageDelta
                        renderAvailableApps(requestFocusOnFirstItem = true)
                    }
                }
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            showManagement(isFirstRun = false)
            return true
        }
        val isPaginationScreen = currentScreen == Screen.HOME || currentScreen == Screen.MANAGEMENT
        val isPageKey = keyCode == KeyEvent.KEYCODE_PAGE_UP ||
            keyCode == KeyEvent.KEYCODE_PAGE_DOWN
        if (isPaginationScreen && isPageKey) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    @Deprecated("Deprecated by Android; kept for the API 29 RoleManager result callback.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_HOME_ROLE && currentScreen == Screen.MANAGEMENT) {
            renderDefaultLauncherSection()
        }
    }

    private fun showHome() {
        resetUpdateSession()
        currentScreen = Screen.HOME
        firstRunManagement = false
        clearHomeReferences()
        clearManagementReferences()

        val root = createBaseScreen()
        homeRoot = root
        homeErrorView = null

        homeApps = appsRepository.loadApps(preferences.selectedComponents())
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            clipToPadding = false
        }
        homeAppsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            clipToPadding = false
        }
        content.addView(homeAppsContainer, linearMatchWrapParams())

        val pagination = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        previousHomePageButton = actionButton(getString(R.string.previous_page)) {
            changeHomePage(-1)
        }
        homePageIndicatorView = plainText("", SMALL_TEXT_SIZE_SP).apply {
            gravity = Gravity.CENTER
        }
        nextHomePageButton = actionButton(getString(R.string.next_page)) {
            changeHomePage(1)
        }
        pagination.addView(previousHomePageButton, linearWrapParams())
        pagination.addView(
            homePageIndicatorView,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(8)
                marginEnd = dp(8)
            },
        )
        pagination.addView(nextHomePageButton, linearWrapParams())
        homePagerSpacingView = verticalSpace(HOME_PAGER_TOP_SPACING_DP)
        content.addView(homePagerSpacingView)
        content.addView(pagination, linearMatchWrapParams())

        val contentMaxWidthPx = dp(
            HomeGridPolicy.contentMaxWidthDp(
                columns = preferences.homeGridColumns(),
                columnWidthDp = HOME_MAX_COLUMN_WIDTH_DP,
                columnSpacingDp = HOME_COLUMN_SPACING_DP,
            ),
        )
        root.addView(
            content,
            FrameLayout.LayoutParams(
                minOf(
                    contentMaxWidthPx,
                    resources.displayMetrics.widthPixels - dp(HOME_SIDE_MARGIN_DP * 2),
                ).coerceAtLeast(0),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL or Gravity.TOP,
            ).apply {
                marginStart = dp(HOME_SIDE_MARGIN_DP)
                marginEnd = dp(HOME_SIDE_MARGIN_DP)
                topMargin = dp(HOME_CONTENT_TOP_RESERVE_DP)
                bottomMargin = dp(HOME_CONTENT_BOTTOM_RESERVE_DP)
            },
        )

        homeManagementButton = ImageView(this).apply {
            setImageResource(R.drawable.ic_settings)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(10), dp(10), dp(10), dp(10))
            isClickable = true
            isFocusable = true
            background = focusOnlyBackground()
            contentDescription = getString(R.string.management_button_description)
            setOnLongClickListener {
                showManagement(isFirstRun = false)
                true
            }
        }
        root.addView(
            homeManagementButton,
            FrameLayout.LayoutParams(
                dp(HOME_MANAGEMENT_BUTTON_SIZE_DP),
                dp(HOME_MANAGEMENT_BUTTON_SIZE_DP),
                Gravity.BOTTOM or Gravity.END,
            ).apply {
                marginEnd = dp(HOME_SIDE_MARGIN_DP)
                bottomMargin = dp(HOME_MANAGEMENT_BUTTON_MARGIN_DP)
            },
        )

        setContentView(root)
        renderHomePage()
        hideSystemStatusBar()
    }

    private fun renderHomePage(requestFocusOnFirstItem: Boolean = false) {
        val container = homeAppsContainer ?: return
        val preset = preferences.displayPreset()
        val rows = preferences.homeGridRows()
        val columns = preferences.homeGridColumns()
        val homeTextSizeSp = preferences.homeAppTextSizeSp()
        val homeTextSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            homeTextSizeSp.toFloat(),
            resources.displayMetrics,
        )
        val scaledTextHeightPx = kotlin.math.ceil(
            homeTextSizePx * HOME_TEXT_LINE_HEIGHT_FACTOR,
        ).toInt()
        val rowHeightPx = maxOf(
            dp(preset.homeRowHeightDp),
            scaledTextHeightPx,
        ) + dp(HOME_ROW_SPACING_DP)
        val page = SelectionPolicy.page(homeApps, homePage, rows * columns)
        homePage = page.pageIndex
        homePageCount = page.pageCount
        container.removeAllViews()
        container.minimumHeight = if (homeApps.isEmpty()) {
            0
        } else {
            (rows * rowHeightPx - dp(HOME_ROW_SPACING_DP)).coerceAtLeast(0)
        }

        var firstHomeAppView: View? = null
        if (homeApps.isEmpty()) {
            container.addView(
                plainText(getString(R.string.no_apps_title), HOME_EMPTY_TEXT_SIZE_SP).apply {
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                },
                linearWrapParams(),
            )
            container.addView(verticalSpace(8))
            container.addView(
                actionButton(getString(R.string.add_apps)) {
                    showManagement(isFirstRun = false)
                },
                linearWrapParams(),
            )
        } else {
            page.items.chunked(columns).forEachIndexed { rowIndex, rowApps ->
                if (rowIndex > 0) container.addView(verticalSpace(HOME_ROW_SPACING_DP))
                val gridRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                rowApps.forEachIndexed { columnIndex, app ->
                    if (columnIndex > 0) {
                        gridRow.addView(horizontalSpace(HOME_COLUMN_SPACING_DP))
                    }
                    val appView = homeAppButton(app, preset, homeTextSizeSp)
                    if (firstHomeAppView == null) firstHomeAppView = appView
                    gridRow.addView(
                        appView,
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                    )
                }
                // Keep cell widths equal when the final row is not completely filled.
                // Height must be exactly 0: a plain View measured with WRAP_CONTENT inside a
                // horizontal LinearLayout receives an AT_MOST height spec and getDefaultSize
                // expands it to the full spec size, inflating the row.
                repeat(columns - rowApps.size) {
                    gridRow.addView(horizontalSpace(HOME_COLUMN_SPACING_DP))
                    gridRow.addView(
                        View(this),
                        LinearLayout.LayoutParams(0, 0, 1f),
                    )
                }
                container.addView(gridRow, linearMatchWrapParams())
            }
        }

        val pagerVisible = page.pageCount > 1
        homePagerSpacingView?.visibility = if (pagerVisible) View.VISIBLE else View.GONE
        homePageIndicatorView?.apply {
            visibility = if (pagerVisible) View.VISIBLE else View.GONE
            text = getString(
                R.string.page_indicator,
                page.pageIndex + 1,
                page.pageCount,
            )
        }
        previousHomePageButton?.apply {
            visibility = if (pagerVisible) View.VISIBLE else View.GONE
            isEnabled = page.hasPrevious
        }
        nextHomePageButton?.apply {
            visibility = if (pagerVisible) View.VISIBLE else View.GONE
            isEnabled = page.hasNext
        }
        if (requestFocusOnFirstItem) firstHomeAppView?.requestFocus()
    }

    private fun changeHomePage(
        delta: Int,
        requestFocusOnFirstItem: Boolean = false,
    ): Boolean {
        val targetPage = (homePage + delta).coerceIn(0, homePageCount - 1)
        if (targetPage == homePage) return false
        homePage = targetPage
        renderHomePage(requestFocusOnFirstItem)
        return true
    }

    private fun refreshHomeAppsIfChanged() {
        val refreshedApps = appsRepository.loadApps(preferences.selectedComponents())
        if (refreshedApps == homeApps) return
        homeApps = refreshedApps
        renderHomePage()
    }

    private fun showManagement(isFirstRun: Boolean) {
        if (currentScreen == Screen.MANAGEMENT && !isFirstRun) return

        resetUpdateSession()
        currentScreen = Screen.MANAGEMENT
        firstRunManagement = isFirstRun
        clearHomeReferences()
        clearManagementReferences()
        defaultLauncherErrorVisible = false
        cachedApps = appsRepository.loadApps()

        val root = createBaseScreen()
        val scrollView = NoFlingScrollView(this).apply {
            setBackgroundColor(Color.WHITE)
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            isSmoothScrollingEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            setPadding(dp(MANAGEMENT_SIDE_MARGIN_DP), dp(8), dp(MANAGEMENT_SIDE_MARGIN_DP), dp(24))
        }
        scrollView.addView(
            content,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            plainText(getString(R.string.settings_title), TITLE_TEXT_SIZE_SP).apply {
                setTypeface(typeface, Typeface.BOLD)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        header.addView(
            actionButton(getString(R.string.done), ::finishManagement),
            linearWrapParams(),
        )
        root.addView(
            header,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(ACTION_HEIGHT_DP),
                Gravity.TOP,
            ).apply {
                topMargin = dp(MANAGEMENT_TOP_MARGIN_DP)
                marginStart = dp(MANAGEMENT_SIDE_MARGIN_DP)
                marginEnd = dp(MANAGEMENT_SIDE_MARGIN_DP)
            },
        )

        if (isFirstRun) {
            firstRunHintView = plainText(getString(R.string.first_run_hint), BODY_TEXT_SIZE_SP).apply {
                    setLineSpacing(0f, 1.15f)
                }
            content.addView(firstRunHintView, linearMatchWrapParams())
        }

        if (!isFirstRun) {
            content.addView(verticalSpace(16))
            defaultLauncherContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.START
            }
            content.addView(defaultLauncherContainer, linearMatchWrapParams())

            content.addView(verticalSpace(20))
            content.addView(
                sectionHeading(getString(R.string.home_display_preset)),
                linearMatchWrapParams(),
            )
            content.addView(verticalSpace(6))
            val textSizeControls = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(ACTION_HEIGHT_DP)
            }
            displayPresetButtons.clear()
            DisplayPreset.entries.forEachIndexed { index, preset ->
                val button = actionButton(displayPresetLabel(preset)) {
                    updateDisplayPreset(preset)
                }
                displayPresetButtons[preset] = button
                textSizeControls.addView(
                    button,
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                )
                if (index < DisplayPreset.entries.lastIndex) {
                    textSizeControls.addView(horizontalSpace(6))
                }
            }
            content.addView(textSizeControls, linearMatchWrapParams())
            content.addView(verticalSpace(6))
            homeTextSizeValueView = plainText("", SMALL_TEXT_SIZE_SP).apply {
                setLineSpacing(0f, 1.15f)
            }
            content.addView(homeTextSizeValueView, linearMatchWrapParams())
            renderHomeTextSizeSetting()

            content.addView(verticalSpace(16))
            content.addView(
                sectionHeading(getString(R.string.home_grid_title)),
                linearMatchWrapParams(),
            )
            content.addView(verticalSpace(6))
            homeGridContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.START
            }
            content.addView(homeGridContainer, linearMatchWrapParams())
            renderHomeGridSetting()

            content.addView(verticalSpace(16))
            content.addView(
                sectionHeading(getString(R.string.home_clock_mode_title)),
                linearMatchWrapParams(),
            )
            content.addView(verticalSpace(6))
            val clockModeControls = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(ACTION_HEIGHT_DP)
            }
            homeClockModeButtons.clear()
            HomeClockMode.entries.forEachIndexed { index, mode ->
                val button = actionButton(homeClockModeLabel(mode)) {
                    updateHomeClockMode(mode)
                }
                homeClockModeButtons[mode] = button
                clockModeControls.addView(
                    button,
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                )
                if (index < HomeClockMode.entries.lastIndex) {
                    clockModeControls.addView(horizontalSpace(6))
                }
            }
            content.addView(clockModeControls, linearMatchWrapParams())
            renderHomeClockModeSetting()
        }

        content.addView(verticalSpace(20))
        val selectedHeading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        selectedHeading.addView(
            sectionHeading(getString(R.string.selected_apps)),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        selectedCountView = plainText("", SMALL_TEXT_SIZE_SP).apply { gravity = Gravity.END }
        selectedHeading.addView(selectedCountView, linearWrapParams())
        content.addView(selectedHeading, linearMatchWrapParams())
        content.addView(verticalSpace(6))
        content.addView(
            plainText(getString(R.string.selected_apps_hint), SMALL_TEXT_SIZE_SP),
            linearMatchWrapParams(),
        )
        content.addView(verticalSpace(4))

        selectedContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
        }
        content.addView(selectedContainer, linearMatchWrapParams())

        content.addView(verticalSpace(20))
        content.addView(sectionHeading(getString(R.string.available_apps)), linearMatchWrapParams())
        content.addView(verticalSpace(6))
        availableContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
        }
        content.addView(availableContainer, linearMatchWrapParams())
        content.addView(verticalSpace(8))

        val pagination = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        previousPageButton = actionButton(getString(R.string.previous_page)) {
            availablePage -= 1
            renderAvailableApps()
        }
        pageIndicatorView = plainText("", SMALL_TEXT_SIZE_SP).apply {
            gravity = Gravity.CENTER
        }
        nextPageButton = actionButton(getString(R.string.next_page)) {
            availablePage += 1
            renderAvailableApps()
        }
        pagination.addView(previousPageButton, linearWrapParams())
        pagination.addView(
            pageIndicatorView,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(8)
                marginEnd = dp(8)
            },
        )
        pagination.addView(nextPageButton, linearWrapParams())
        content.addView(pagination, linearMatchWrapParams())

        if (!isFirstRun) {
            content.addView(verticalSpace(20))
            content.addView(sectionHeading(getString(R.string.updates_title)), linearMatchWrapParams())
            content.addView(verticalSpace(6))
            updateContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.START
            }
            content.addView(updateContainer, linearMatchWrapParams())
            renderUpdateSection()
        }

        root.addView(
            scrollView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ).apply {
                topMargin = dp(MANAGEMENT_CONTENT_TOP_MARGIN_DP)
            },
        )

        setContentView(root)
        renderDefaultLauncherSection()
        renderSelectedApps()
        renderAvailableApps()
        hideSystemStatusBar()
    }

    private fun finishManagement() {
        if (firstRunManagement && preferences.selectedComponents().isEmpty()) {
            val message = getString(R.string.first_run_selection_required)
            firstRunHintView?.apply {
                text = message
                setTypeface(typeface, Typeface.BOLD)
                announceForAccessibility(message)
            }
            return
        }
        val shouldRequestHomeRole = firstRunManagement && !isDefaultLauncher()
        if (firstRunManagement) preferences.markOnboardingComplete()
        showHome()
        if (shouldRequestHomeRole) requestDefaultLauncher()
    }

    private fun renderSelectedApps() {
        val container = selectedContainer ?: return
        val selected = preferences.selectedComponents()
        val appsByComponent = cachedApps.associateBy { it.componentName }
        selectedCountView?.text = getString(
            R.string.selected_count,
            selected.size,
            preferences.homeGridCapacity(),
        )
        container.removeAllViews()

        if (selected.isEmpty()) {
            container.addView(
                plainText(getString(R.string.no_selected_apps_hint), SMALL_TEXT_SIZE_SP),
                linearMatchWrapParams(),
            )
            return
        }

        selected.forEachIndexed { index, component ->
            val app = appsByComponent[component]
            val systemLabel = app?.label ?: component.packageName
            val alias = preferences.appAlias(component)
            val label = alias ?: systemLabel
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(MANAGEMENT_ROW_HEIGHT_DP)
            }
            val labelColumn = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
            }
            labelColumn.addView(
                plainText(label, BODY_TEXT_SIZE_SP).apply {
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    isClickable = true
                    isFocusable = true
                    background = focusOnlyBackground()
                    contentDescription = getString(R.string.rename_app, label)
                    setPadding(dp(4), dp(4), dp(4), dp(4))
                    setOnClickListener {
                        showRenameAppDialog(component, systemLabel)
                    }
                },
                linearMatchWrapParams(),
            )
            if (alias != null && alias != systemLabel) {
                labelColumn.addView(
                    plainText(
                        getString(R.string.system_app_name, systemLabel),
                        SMALL_TEXT_SIZE_SP,
                    ),
                    linearMatchWrapParams(),
                )
            }
            if (app == null) {
                labelColumn.addView(
                    plainText(getString(R.string.app_unavailable), SMALL_TEXT_SIZE_SP),
                    linearMatchWrapParams(),
                )
            }
            row.addView(
                labelColumn,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(8)
                },
            )

            val upButton = actionButton(getString(R.string.move_up)) {
                updateSelection { SelectionPolicy.moveUp(it, component) }
            }.apply {
                isEnabled = index > 0
                visibility = if (index > 0) View.VISIBLE else View.INVISIBLE
                contentDescription = getString(R.string.move_app_up, label)
            }
            val downButton = actionButton(getString(R.string.move_down)) {
                updateSelection { SelectionPolicy.moveDown(it, component) }
            }.apply {
                isEnabled = index < selected.lastIndex
                visibility = if (index < selected.lastIndex) View.VISIBLE else View.INVISIBLE
                contentDescription = getString(R.string.move_app_down, label)
            }
            val removeButton = actionButton(getString(R.string.remove)) {
                updateSelection { SelectionPolicy.remove(it, component) }
            }.apply {
                contentDescription = getString(R.string.remove_app, label)
            }
            row.addView(upButton, compactButtonParams())
            row.addView(horizontalSpace(4))
            row.addView(downButton, compactButtonParams())
            row.addView(horizontalSpace(4))
            row.addView(removeButton, compactButtonParams())
            container.addView(row, linearMatchWrapParams())
            if (index < selected.lastIndex) container.addView(verticalSpace(4))
        }
    }

    private fun renderAvailableApps(requestFocusOnFirstItem: Boolean = false) {
        val container = availableContainer ?: return
        val selected = preferences.selectedComponents()
        val capacity = preferences.homeGridCapacity()
        val selectedSet = selected.toHashSet()
        val available = cachedApps.filterNot { it.componentName in selectedSet }
        val page = SelectionPolicy.page(available, availablePage, AVAILABLE_PAGE_SIZE)
        availablePage = page.pageIndex
        container.removeAllViews()
        var firstAvailableRow: View? = null

        if (page.items.isEmpty()) {
            container.addView(
                plainText(getString(R.string.no_available_apps), SMALL_TEXT_SIZE_SP),
                linearMatchWrapParams(),
            )
        }

        page.items.forEachIndexed { index, app ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(MANAGEMENT_ROW_HEIGHT_DP)
                isClickable = selected.size < capacity
                isFocusable = isClickable
                if (isClickable) {
                    background = focusOnlyBackground()
                    contentDescription = getString(R.string.add_app, app.label)
                    setOnClickListener { addSelectedApp(app.componentName) }
                }
            }
            if (firstAvailableRow == null && row.isFocusable) firstAvailableRow = row
            row.addView(
                plainText(app.label, BODY_TEXT_SIZE_SP).apply {
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER_VERTICAL
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(8)
                },
            )
            row.addView(
                actionButton(getString(R.string.add)) {
                    addSelectedApp(app.componentName)
                }.apply {
                    contentDescription = getString(R.string.add_app, app.label)
                    isEnabled = selected.size < capacity
                    if (!isEnabled) text = getString(R.string.selection_full_short)
                },
                compactButtonParams(),
            )
            container.addView(row, linearMatchWrapParams())
            if (index < page.items.lastIndex) container.addView(verticalSpace(4))
        }

        pageIndicatorView?.text = if (page.items.isEmpty()) {
            getString(R.string.page_indicator, page.pageIndex + 1, page.pageCount)
        } else {
            val rangeStart = page.pageIndex * AVAILABLE_PAGE_SIZE + 1
            val rangeEnd = rangeStart + page.items.size - 1
            getString(
                R.string.available_page_indicator,
                rangeStart,
                rangeEnd,
                available.size,
                page.pageIndex + 1,
                page.pageCount,
            )
        }
        previousPageButton?.isEnabled = page.hasPrevious
        nextPageButton?.isEnabled = page.hasNext
        previousPageButton?.visibility = if (page.hasPrevious) View.VISIBLE else View.INVISIBLE
        nextPageButton?.visibility = if (page.hasNext) View.VISIBLE else View.INVISIBLE
        if (requestFocusOnFirstItem) firstAvailableRow?.requestFocus()
    }

    private fun addSelectedApp(component: ComponentName) {
        val capacity = preferences.homeGridCapacity()
        val selected = preferences.selectedComponents()
        if (selected.size >= capacity) return
        updateSelection { SelectionPolicy.add(it, component, capacity) }
    }

    private fun updateSelection(transform: (List<ComponentName>) -> List<ComponentName>) {
        val oldSelection = preferences.selectedComponents()
        val newSelection = transform(oldSelection)
        if (newSelection == oldSelection) return
        preferences.saveSelectedComponents(newSelection)
        iconCache.warmAsync(newSelection)
        renderSelectedApps()
        renderAvailableApps()
    }

    private fun updateDisplayPreset(preset: DisplayPreset) {
        if (preferences.hasExplicitDisplayPreset() && preferences.displayPreset() == preset) return
        preferences.saveDisplayPreset(preset)
        renderHomeTextSizeSetting()
    }

    private fun renderHomeTextSizeSetting() {
        val preset = preferences.displayPreset()
        val explicitPreset = preferences.hasExplicitDisplayPreset()
        val textSizeSp = preferences.homeAppTextSizeSp()
        homeTextSizeValueView?.text = if (explicitPreset) {
            getString(
                R.string.home_display_preset_summary,
                displayPresetLabel(preset),
                textSizeSp,
            )
        } else {
            getString(R.string.home_display_legacy_summary, textSizeSp)
        }
        displayPresetButtons.forEach { (buttonPreset, button) ->
            button.isSelected = explicitPreset && buttonPreset == preset
            button.contentDescription = getString(
                if (button.isSelected) {
                    R.string.display_preset_selected
                } else {
                    R.string.display_preset_option
                },
                displayPresetLabel(buttonPreset),
            )
        }
    }

    private fun displayPresetLabel(preset: DisplayPreset): String = getString(
        when (preset) {
            DisplayPreset.COMPACT -> R.string.display_preset_compact
            DisplayPreset.COMFORTABLE -> R.string.display_preset_comfortable
            DisplayPreset.LARGE -> R.string.display_preset_large
        },
    )

    private fun renderHomeGridSetting() {
        val container = homeGridContainer ?: return
        val rows = preferences.homeGridRows()
        val columns = preferences.homeGridColumns()
        container.removeAllViews()
        container.addView(
            homeGridStepperRow(
                label = getString(R.string.home_grid_rows),
                value = rows,
                minValue = HomeGridPolicy.MIN_ROWS,
                maxValue = HomeGridPolicy.MAX_ROWS,
                onStep = ::stepHomeGridRows,
            ),
            linearMatchWrapParams(),
        )
        container.addView(verticalSpace(4))
        container.addView(
            homeGridStepperRow(
                label = getString(R.string.home_grid_columns),
                value = columns,
                minValue = HomeGridPolicy.MIN_COLUMNS,
                maxValue = HomeGridPolicy.MAX_COLUMNS,
                onStep = ::stepHomeGridColumns,
            ),
            linearMatchWrapParams(),
        )
        container.addView(verticalSpace(6))
        container.addView(
            plainText(
                getString(R.string.home_grid_summary, rows, columns, rows * columns),
                SMALL_TEXT_SIZE_SP,
            ).apply {
                setLineSpacing(0f, 1.15f)
            },
            linearMatchWrapParams(),
        )
    }

    private fun homeGridStepperRow(
        label: String,
        value: Int,
        minValue: Int,
        maxValue: Int,
        onStep: (Int) -> Unit,
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(ACTION_HEIGHT_DP)
        }
        row.addView(
            plainText(label, BODY_TEXT_SIZE_SP),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        row.addView(
            actionButton(getString(R.string.decrease_symbol)) { onStep(-1) }.apply {
                isEnabled = value > minValue
                contentDescription = getString(R.string.home_grid_decrease, label)
            },
            compactButtonParams(),
        )
        row.addView(horizontalSpace(6))
        row.addView(
            plainText(value.toString(), BODY_TEXT_SIZE_SP).apply {
                gravity = Gravity.CENTER
                minWidth = dp(HOME_GRID_VALUE_WIDTH_DP)
            },
            linearWrapParams(),
        )
        row.addView(horizontalSpace(6))
        row.addView(
            actionButton(getString(R.string.increase_symbol)) { onStep(1) }.apply {
                isEnabled = value < maxValue
                contentDescription = getString(R.string.home_grid_increase, label)
            },
            compactButtonParams(),
        )
        return row
    }

    private fun stepHomeGridRows(delta: Int) {
        val current = preferences.homeGridRows()
        val next = HomeGridPolicy.normalizeRows(current + delta)
        if (next == current) return
        preferences.saveHomeGridRows(next)
        renderHomeGridSetting()
        renderSelectedApps()
        renderAvailableApps()
    }

    private fun stepHomeGridColumns(delta: Int) {
        val current = preferences.homeGridColumns()
        val next = HomeGridPolicy.normalizeColumns(current + delta)
        if (next == current) return
        preferences.saveHomeGridColumns(next)
        renderHomeGridSetting()
        renderSelectedApps()
        renderAvailableApps()
    }

    private fun updateHomeClockMode(mode: HomeClockMode) {
        if (preferences.homeClockMode() == mode) return
        preferences.saveHomeClockMode(mode)
        renderHomeClockModeSetting()
    }

    private fun renderHomeClockModeSetting() {
        val selectedMode = preferences.homeClockMode()
        homeClockModeButtons.forEach { (mode, button) ->
            button.isSelected = mode == selectedMode
            button.contentDescription = getString(
                if (button.isSelected) {
                    R.string.home_clock_mode_selected
                } else {
                    R.string.home_clock_mode_option
                },
                homeClockModeLabel(mode),
            )
        }
    }

    private fun homeClockModeLabel(mode: HomeClockMode): String = getString(
        when (mode) {
            HomeClockMode.TIME_ONLY -> R.string.home_clock_time_only
            HomeClockMode.DATE_AND_TIME -> R.string.home_clock_date_and_time
        },
    )

    @Suppress("DEPRECATION")
    private fun showRenameAppDialog(
        component: ComponentName,
        systemLabel: String,
        initialDraft: String? = null,
    ) {
        renameDialog?.dismiss()
        val dialog = Dialog(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            setPadding(dp(20), dp(20), dp(20), dp(16))
            background = whiteRectangle(strokeWidthDp = 2)
        }
        content.addView(
            sectionHeading(getString(R.string.rename_app_title)),
            linearMatchWrapParams(),
        )
        content.addView(verticalSpace(8))
        content.addView(
            plainText(getString(R.string.system_app_name, systemLabel), SMALL_TEXT_SIZE_SP),
            linearMatchWrapParams(),
        )
        content.addView(verticalSpace(8))
        val input = EditText(this).apply {
            setText(initialDraft ?: preferences.appAlias(component).orEmpty())
            hint = systemLabel
            setTextColor(Color.BLACK)
            setHintTextColor(DISABLED_INK_COLOR)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, BODY_TEXT_SIZE_SP)
            setSingleLine(true)
            maxLines = 1
            filters = arrayOf(InputFilter.LengthFilter(AppAliasPolicy.MAX_LENGTH))
            setSelectAllOnFocus(true)
            imeOptions = EditorInfo.IME_ACTION_DONE
            contentDescription = getString(R.string.app_alias_input)
        }
        content.addView(input, linearMatchWrapParams())
        content.addView(verticalSpace(12))

        val saveAlias: () -> Unit = {
            preferences.saveAppAlias(component, input.text?.toString())
            dialog.dismiss()
            renderSelectedApps()
            val savedLabel = preferences.appAlias(component) ?: systemLabel
            selectedContainer?.announceForAccessibility(
                getString(R.string.app_name_saved, savedLabel),
            )
            Unit
        }
        input.setOnEditorActionListener { _, actionId, event ->
            val enterReleased = event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                event.action == KeyEvent.ACTION_UP
            if (actionId == EditorInfo.IME_ACTION_DONE || enterReleased) {
                saveAlias()
                true
            } else {
                false
            }
        }

        content.addView(
            actionButton(getString(R.string.restore_system_name)) {
                preferences.saveAppAlias(component, null)
                dialog.dismiss()
                renderSelectedApps()
                selectedContainer?.announceForAccessibility(
                    getString(R.string.app_name_restored, systemLabel),
                )
            },
            linearMatchWrapParams(),
        )
        content.addView(verticalSpace(8))
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        actions.addView(
            actionButton(getString(android.R.string.cancel)) { dialog.dismiss() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        actions.addView(horizontalSpace(6))
        actions.addView(
            actionButton(getString(R.string.save), saveAlias),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        content.addView(actions, linearMatchWrapParams())

        val dialogScroll = NoFlingScrollView(this).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        dialogScroll.addView(
            content,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        renameDialog = dialog
        renameDialogInput = input
        renameDialogComponent = component
        dialog.setOnDismissListener {
            if (renameDialog === dialog) {
                renameDialog = null
                renameDialogInput = null
                renameDialogComponent = null
            }
        }
        dialog.setContentView(dialogScroll)
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            attributes = attributes.apply { windowAnimations = 0 }
        }
        dialog.show()
        val availableDialogWidth = (
            resources.displayMetrics.widthPixels - dp(RENAME_DIALOG_SIDE_MARGIN_DP * 2)
        ).coerceAtLeast(dp(ACTION_HEIGHT_DP))
        dialog.window?.setLayout(
            minOf(dp(RENAME_DIALOG_MAX_WIDTH_DP), availableDialogWidth),
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        input.requestFocus()
    }

    private fun renderUpdateSection() {
        val container = updateContainer ?: return
        container.removeAllViews()
        container.addView(
            plainText(getString(R.string.current_version, installedVersionName), SMALL_TEXT_SIZE_SP),
            linearMatchWrapParams(),
        )

        val status = when (updateState) {
            UpdateState.IDLE -> null
            UpdateState.CHECKING -> getString(R.string.checking_for_updates)
            UpdateState.UP_TO_DATE -> getString(R.string.up_to_date)
            UpdateState.AVAILABLE -> getString(
                R.string.update_available,
                availableUpdate?.versionName.orEmpty(),
            )
            UpdateState.DOWNLOADING -> getString(R.string.downloading_update)
            UpdateState.READY -> getString(
                R.string.update_ready,
                verifiedUpdate?.release?.versionName.orEmpty(),
            )
            UpdateState.INSTALL_PERMISSION_REQUIRED -> getString(R.string.allow_update_installs)
            UpdateState.CHECK_FAILED -> getString(R.string.update_check_failed)
            UpdateState.DOWNLOAD_FAILED -> getString(R.string.update_download_failed)
            UpdateState.INSTALL_FAILED -> getString(R.string.installer_unavailable)
            UpdateState.FILE_UNAVAILABLE -> getString(R.string.update_file_unavailable)
        }
        status?.let {
            container.addView(verticalSpace(6))
            container.addView(
                plainText(it, SMALL_TEXT_SIZE_SP).apply { setLineSpacing(0f, 1.15f) },
                linearMatchWrapParams(),
            )
        }

        container.addView(verticalSpace(8))
        val action = when (updateState) {
            UpdateState.AVAILABLE,
            UpdateState.DOWNLOAD_FAILED,
            -> getString(R.string.download_update) to ::downloadUpdate

            UpdateState.READY,
            UpdateState.INSTALL_PERMISSION_REQUIRED,
            UpdateState.INSTALL_FAILED,
            -> getString(R.string.install_update) to ::installVerifiedUpdate

            UpdateState.CHECKING -> getString(R.string.checking_for_updates) to {}
            UpdateState.DOWNLOADING -> getString(R.string.downloading_update) to {}
            else -> getString(R.string.check_for_updates) to ::checkForUpdates
        }
        container.addView(
            actionButton(action.first, action.second).apply {
                isEnabled = activeUpdateThread == null &&
                    updateState != UpdateState.CHECKING &&
                    updateState != UpdateState.DOWNLOADING
            },
            linearWrapParams(),
        )
    }

    private fun checkForUpdates() {
        if (activeUpdateThread != null) return
        availableUpdate = null
        verifiedUpdate = null
        val client = newUpdateClientOrShowFailure(UpdateState.CHECK_FAILED) ?: return
        startUpdateTask(
            busyState = UpdateState.CHECKING,
            client = client,
            work = {
                val currentVersion = updateVerifier.currentVersionName()
                if (UpdatePolicy.normalizedVersion(currentVersion) != currentVersion) {
                    throw UpdateException("Installed version name is not semantic")
                }
                currentVersion to client.latestRelease()
            },
        ) { result ->
            result.fold(
                onSuccess = { (currentVersion, release) ->
                    installedVersionName = currentVersion
                    if (UpdatePolicy.isNewer(release.versionName, currentVersion)) {
                        availableUpdate = release
                        updateState = UpdateState.AVAILABLE
                    } else {
                        updateState = UpdateState.UP_TO_DATE
                    }
                },
                onFailure = {
                    updateState = UpdateState.CHECK_FAILED
                },
            )
            renderUpdateSection()
        }
    }

    private fun downloadUpdate() {
        if (activeUpdateThread != null) return
        val release = availableUpdate ?: run {
            updateState = UpdateState.FILE_UNAVAILABLE
            renderUpdateSection()
            return
        }
        verifiedUpdate = null
        val client = newUpdateClientOrShowFailure(UpdateState.DOWNLOAD_FAILED) ?: return
        startUpdateTask(
            busyState = UpdateState.DOWNLOADING,
            client = client,
            work = {
                var downloadedFile: File? = null
                try {
                    downloadedFile = client.download(release, File(cacheDir, UPDATE_CACHE_DIRECTORY))
                    updateVerifier.verify(downloadedFile, release)
                } catch (error: Exception) {
                    downloadedFile?.delete()
                    throw error
                }
            },
        ) { result ->
            result.fold(
                onSuccess = { verified ->
                    verifiedUpdate = verified
                    updateState = UpdateState.READY
                },
                onFailure = {
                    updateState = UpdateState.DOWNLOAD_FAILED
                },
            )
            renderUpdateSection()
        }
    }

    private fun installVerifiedUpdate() {
        val verified = verifiedUpdate ?: run {
            updateState = UpdateState.FILE_UNAVAILABLE
            renderUpdateSection()
            return
        }
        try {
            updateVerifier.verify(verified.file, verified.release)
        } catch (_: UpdateException) {
            verified.file.delete()
            verifiedUpdate = null
            updateState = UpdateState.FILE_UNAVAILABLE
            renderUpdateSection()
            return
        }

        if (!updateInstaller.canRequestInstall()) {
            waitingForInstallPermission = true
            updateState = UpdateState.INSTALL_PERMISSION_REQUIRED
            renderUpdateSection()
            try {
                startActivityWithoutAnimation(updateInstaller.permissionIntent())
            } catch (_: ActivityNotFoundException) {
                waitingForInstallPermission = false
                updateState = UpdateState.INSTALL_FAILED
                renderUpdateSection()
            } catch (_: SecurityException) {
                waitingForInstallPermission = false
                updateState = UpdateState.INSTALL_FAILED
                renderUpdateSection()
            }
            return
        }

        try {
            startActivityWithoutAnimation(updateInstaller.installIntent(verified.file))
        } catch (_: ActivityNotFoundException) {
            updateState = UpdateState.INSTALL_FAILED
            renderUpdateSection()
        } catch (_: SecurityException) {
            updateState = UpdateState.INSTALL_FAILED
            renderUpdateSection()
        } catch (_: UpdateException) {
            updateState = UpdateState.INSTALL_FAILED
            renderUpdateSection()
        }
    }

    private fun startActivityWithoutAnimation(intent: Intent) {
        val options = ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle()
        startActivity(intent, options)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun newUpdateClientOrShowFailure(failureState: UpdateState): UpdateClient? = try {
        updateClientFactory()
    } catch (_: Exception) {
        updateState = failureState
        renderUpdateSection()
        null
    }

    private fun <T> startUpdateTask(
        busyState: UpdateState,
        client: UpdateClient,
        work: () -> T,
        onComplete: (Result<T>) -> Unit,
    ) {
        cancelActiveUpdateTask(restoreIdleState = false)
        updateState = busyState
        renderUpdateSection()
        val generation = ++updateGeneration
        activeUpdateClient = client
        val worker = Thread(
            {
                val result = try {
                    Result.success(work())
                } catch (error: Exception) {
                    Result.failure(error)
                }
                val completedWorker = Thread.currentThread()
                runOnUiThread {
                    if (activeUpdateThread === completedWorker) {
                        activeUpdateThread = null
                    }
                    if (
                        generation != updateGeneration ||
                        isFinishing ||
                        isDestroyed ||
                        currentScreen != Screen.MANAGEMENT
                    ) {
                        if (!isFinishing && !isDestroyed && currentScreen == Screen.MANAGEMENT) {
                            renderUpdateSection()
                        }
                        return@runOnUiThread
                    }
                    activeUpdateClient = null
                    onComplete(result)
                }
            },
            UPDATE_THREAD_NAME,
        )
        activeUpdateThread = worker
        worker.start()
    }

    private fun cancelActiveUpdateTask(restoreIdleState: Boolean) {
        val client = activeUpdateClient
        val worker = activeUpdateThread
        if (client == null && worker == null) return
        updateGeneration += 1
        client?.cancel()
        worker?.interrupt()
        activeUpdateClient = null
        if (restoreIdleState) {
            updateState = when (updateState) {
                UpdateState.CHECKING -> UpdateState.IDLE
                UpdateState.DOWNLOADING -> {
                    if (availableUpdate == null) UpdateState.IDLE else UpdateState.AVAILABLE
                }
                else -> updateState
            }
            renderUpdateSection()
        }
    }

    private fun resetUpdateSession() {
        cancelActiveUpdateTask(restoreIdleState = false)
        waitingForInstallPermission = false
        availableUpdate = null
        verifiedUpdate = null
        updateState = UpdateState.IDLE
    }

    private fun homeAppButton(
        app: LaunchableApp,
        preset: DisplayPreset,
        textSizeSp: Int,
    ): View {
        val displayLabel = preferences.appAlias(app.componentName) ?: app.label
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), 0)
            minimumHeight = dp(preset.homeRowHeightDp)
            isClickable = true
            isFocusable = true
            background = focusOnlyBackground()
            contentDescription = getString(R.string.open_app, displayLabel)
            accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(
                    host: View,
                    info: AccessibilityNodeInfo,
                ) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = Button::class.java.name
                }
            }
            setOnClickListener {
                if (appsRepository.launch(app.componentName)) {
                    @Suppress("DEPRECATION")
                    overridePendingTransition(0, 0)
                } else {
                    showHomeLaunchError(displayLabel)
                }
            }
        }

        val icon = iconCache.icon(app.componentName)
        if (icon != null) {
            val iconSizePx = dp(homeIconSizeDp(preset, textSizeSp))
            row.addView(
                ImageView(this).apply {
                    setImageDrawable(icon)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                LinearLayout.LayoutParams(iconSizePx, iconSizePx).apply {
                    marginEnd = dp(HOME_ICON_LABEL_SPACING_DP)
                },
            )
        }
        row.addView(
            plainText(displayLabel, textSizeSp.toFloat()).apply {
                gravity = Gravity.CENTER_VERTICAL
                typeface = weightedTypeface(preset.fontWeight)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        return row
    }

    /** Icon edge length that tracks the home text size but never inflates the row. */
    private fun homeIconSizeDp(preset: DisplayPreset, textSizeSp: Int): Int {
        val fromTextSize = (textSizeSp * HOME_ICON_TEXT_SCALE).toInt()
        val rowCap = preset.homeRowHeightDp - HOME_ICON_ROW_PADDING_DP
        return fromTextSize.coerceIn(HOME_ICON_MIN_DP, HOME_ICON_MAX_DP)
            .coerceAtMost(rowCap.coerceAtLeast(HOME_ICON_MIN_DP))
    }

    private fun showHomeLaunchError(appLabel: String) {
        val root = homeRoot ?: return
        if (homeErrorView != null) return

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(8), dp(8))
            background = whiteRectangle(strokeWidthDp = 1)
        }
        panel.addView(
            plainText(getString(R.string.unable_to_open_app, appLabel), BODY_TEXT_SIZE_SP),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(12)
            },
        )
        panel.addView(
            actionButton(getString(android.R.string.ok)) { showHome() },
            linearWrapParams(),
        )
        homeErrorView = panel
        root.addView(
            panel,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.START or Gravity.BOTTOM,
            ).apply {
                marginStart = dp(HOME_SIDE_MARGIN_DP)
                marginEnd = dp(HOME_SIDE_MARGIN_DP)
                bottomMargin = dp(
                    HOME_MANAGEMENT_BUTTON_SIZE_DP + HOME_MANAGEMENT_BUTTON_MARGIN_DP + 8,
                )
            },
        )
        panel.isFocusable = true
        panel.requestFocus()
        panel.announceForAccessibility(getString(R.string.unable_to_open_app, appLabel))
    }

    private fun createBaseScreen(): FrameLayout {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
            clipChildren = false
        }
        batteryView = null
        datePrefixView = null
        timeView = null
        wifiView = null
        homeStatusView = null
        if (currentBattery.isEmpty()) readCurrentBattery()?.let(::updateBattery)

        if (currentScreen == Screen.HOME) {
            val status = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                setPadding(
                    dp(STATUS_HORIZONTAL_PADDING_DP),
                    0,
                    dp(STATUS_HORIZONTAL_PADDING_DP),
                    0,
                )
                minimumHeight = dp(MIN_STATUS_TOUCH_HEIGHT_DP)
                layoutDirection = View.LAYOUT_DIRECTION_LTR
            }
            datePrefixView = plainText("", TIME_TEXT_SIZE_SP).apply {
                gravity = Gravity.END
                typeface = interSemiBold
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            timeView = plainText("", TIME_TEXT_SIZE_SP).apply {
                typeface = interSemiBold
                maxLines = 1
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            wifiView = ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            batteryView = plainText("", TIME_TEXT_SIZE_SP).apply {
                typeface = interSemiBold
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            status.addView(
                datePrefixView,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            status.addView(timeView, linearWrapParams())
            status.addView(
                wifiView,
                LinearLayout.LayoutParams(dp(TIME_ICON_SIZE_DP), dp(TIME_ICON_SIZE_DP)).apply {
                    marginStart = dp(STATUS_ITEM_SPACING_DP)
                },
            )
            status.addView(
                batteryView,
                linearWrapParams().apply {
                    marginStart = dp(STATUS_ITEM_SPACING_DP)
                },
            )
            homeStatusView = status
            root.addView(
                status,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP,
                ).apply {
                    topMargin = dp(STATUS_TOP_MARGIN_DP)
                    marginStart = dp(STATUS_END_MARGIN_DP)
                    marginEnd = dp(STATUS_END_MARGIN_DP)
                },
            )
        } else {
            batteryView = plainText("", TIME_TEXT_SIZE_SP).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
                typeface = interSemiBold
                setPadding(dp(8), 0, dp(8), 0)
                minHeight = dp(MIN_STATUS_TOUCH_HEIGHT_DP)
            }
            root.addView(
                batteryView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.END,
                ).apply {
                    topMargin = dp(STATUS_TOP_MARGIN_DP)
                    marginEnd = dp(STATUS_END_MARGIN_DP)
                },
            )
        }
        updateClock()
        updateWifiState()
        updateStatusViews()
        return root
    }

    @Suppress("DEPRECATION")
    private fun readCurrentBattery(): Intent? =
        registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    private fun renderDefaultLauncherSection() {
        val container = defaultLauncherContainer ?: return
        container.removeAllViews()
        if (isDefaultLauncher()) {
            container.addView(
                plainText(getString(R.string.default_launcher_already_set), SMALL_TEXT_SIZE_SP),
                linearMatchWrapParams(),
            )
            return
        }

        container.addView(
            plainText(getString(R.string.default_launcher_explanation), SMALL_TEXT_SIZE_SP).apply {
                setLineSpacing(0f, 1.15f)
            },
            linearMatchWrapParams(),
        )
        container.addView(verticalSpace(8))
        container.addView(
            actionButton(getString(R.string.choose_default_launcher), ::requestDefaultLauncher),
            linearWrapParams(),
        )
        if (defaultLauncherErrorVisible) {
            container.addView(verticalSpace(8))
            container.addView(
                plainText(getString(R.string.default_role_unavailable), SMALL_TEXT_SIZE_SP),
                linearMatchWrapParams(),
            )
        }
    }

    private fun isDefaultLauncher(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_HOME) == true) {
                return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            }
        }

        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        val resolved = packageManager.resolveActivity(homeIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == packageName
    }

    private fun requestDefaultLauncher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_HOME) == true) {
                if (roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                    renderDefaultLauncherSection()
                    return
                }
                try {
                    val request = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                    val options = ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle()
                    @Suppress("DEPRECATION")
                    startActivityForResult(request, REQUEST_HOME_ROLE, options)
                    @Suppress("DEPRECATION")
                    overridePendingTransition(0, 0)
                    return
                } catch (_: ActivityNotFoundException) {
                    // A few vendor builds expose the role but only configure Home in Settings.
                } catch (_: SecurityException) {
                    // Fall through to the vendor's Home settings screen.
                }
            }
        }

        openLegacyHomeSettings()
    }

    private fun openLegacyHomeSettings() {
        val intent = Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        val options = ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle()
        try {
            startActivity(intent, options)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        } catch (_: ActivityNotFoundException) {
            defaultLauncherErrorVisible = true
            renderDefaultLauncherSection()
        } catch (_: SecurityException) {
            defaultLauncherErrorVisible = true
            renderDefaultLauncherSection()
        }
    }

    private fun registerStatusReceiver() {
        if (statusReceiverRegistered) return
        updateClock()
        updateWifiState()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        }
        val stickyBattery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(statusReceiver, filter)
        }
        statusReceiverRegistered = true
        if (stickyBattery?.action == Intent.ACTION_BATTERY_CHANGED) updateBattery(stickyBattery)
    }

    private fun unregisterStatusReceiver() {
        if (!statusReceiverRegistered) return
        unregisterReceiver(statusReceiver)
        statusReceiverRegistered = false
    }

    private fun updateClock() {
        val now = Date()
        val formattedTime = HomeClockTimeFormatter.format(now)
        val formattedDate = HomeClockDateFormatter.format(now)
        val timeChanged = formattedTime != currentTime
        val dateChanged = formattedDate != currentDate
        if (!timeChanged && !dateChanged) return
        currentTime = formattedTime
        currentDate = formattedDate
        updateStatusViews()
    }

    private fun updateBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val charging: Boolean
        val batteryText = if (level < 0 || scale <= 0) {
            charging = false
            getString(R.string.battery_unknown)
        } else {
            val percentage = ((level * 100f) / scale).toInt().coerceIn(0, 100)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING
            getString(R.string.battery_percentage, percentage)
        }
        if (batteryText != currentBattery || charging != currentCharging) {
            currentBattery = batteryText
            currentCharging = charging
            updateStatusViews()
        }
    }

    private fun updateWifiState() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val connectivity =
            applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        @Suppress("DEPRECATION")
        val connected =
            connectivity.getNetworkInfo(ConnectivityManager.TYPE_WIFI)?.isConnected == true
        val next = WifiIndicatorPolicy.resolve(wifiManager.isWifiEnabled, connected)
        if (next == currentWifi) return
        currentWifi = next
        updateStatusViews()
    }

    private fun updateStatusViews() {
        val battery = currentBattery.ifEmpty { getString(R.string.battery_unknown) }
        val batterySpoken = if (currentCharging) {
            getString(R.string.battery_charging_note, battery)
        } else {
            battery
        }
        batteryView?.apply {
            if (text.toString() != battery) text = battery
            renderChargingIcon()
            if (currentScreen != Screen.HOME) {
                contentDescription = getString(R.string.battery_summary, batterySpoken)
            }
        }

        val showDate = currentScreen == Screen.HOME &&
            preferences.homeClockMode() == HomeClockMode.DATE_AND_TIME
        datePrefixView?.apply {
            visibility = if (showDate) View.VISIBLE else View.GONE
            val prefix = getString(R.string.clock_date_prefix_format, currentDate)
            if (text.toString() != prefix) text = prefix
        }
        timeView?.let { view ->
            if (view.text.toString() != currentTime) view.text = currentTime
        }
        wifiView?.apply {
            visibility = if (currentWifi == WifiIndicator.HIDDEN) View.GONE else View.VISIBLE
            setImageResource(R.drawable.ic_wifi)
            imageTintList = ColorStateList.valueOf(
                if (currentWifi == WifiIndicator.ACTIVE) Color.BLACK else WIFI_DIM_INK_COLOR,
            )
        }
        val wifiSummary = getString(
            when (currentWifi) {
                WifiIndicator.ACTIVE -> R.string.wifi_summary_connected
                WifiIndicator.DIM -> R.string.wifi_summary_idle
                WifiIndicator.HIDDEN -> R.string.wifi_summary_off
            },
        )
        homeStatusView?.contentDescription = if (showDate) {
            getString(
                R.string.home_status_date_summary,
                currentTime,
                batterySpoken,
                wifiSummary,
                currentDate,
            )
        } else {
            getString(R.string.home_status_summary, currentTime, batterySpoken, wifiSummary)
        }
    }

    /**
     * Shows a small bolt after the percentage only while the battery is really
     * charging. Only touches the compound drawable when the state flips so
     * e-ink refreshes stay minimal.
     */
    private fun TextView.renderChargingIcon() {
        val hasIcon = compoundDrawablesRelative[2] != null
        if (hasIcon == currentCharging) return
        compoundDrawablePadding = dp(CHARGING_ICON_PADDING_DP)
        val icon = if (currentCharging) {
            context.getDrawable(R.drawable.ic_battery_charging)?.mutate()?.apply {
                val size = dp(CHARGING_ICON_SIZE_DP)
                setBounds(0, 0, size, size)
            }
        } else {
            null
        }
        setCompoundDrawablesRelative(null, null, icon, null)
    }

    @Suppress("DEPRECATION")
    private fun configureWindow() {
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Color.WHITE
        } else {
            Color.BLACK
        }
        window.attributes = window.attributes.apply { windowAnimations = 0 }
    }

    @Suppress("DEPRECATION")
    private fun hideSystemStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.apply {
                hide(WindowInsets.Type.statusBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                )
            }
        } else {
            val systemUiFlags =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            window.decorView.systemUiVisibility = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                systemUiFlags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            } else {
                systemUiFlags
            }
        }
    }

    private fun plainText(text: CharSequence, sizeSp: Float): TextView = TextView(this).apply {
        this.text = text
        setTextColor(Color.BLACK)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        includeFontPadding = false
        setBackgroundColor(Color.TRANSPARENT)
    }

    private fun sectionHeading(text: CharSequence): TextView =
        plainText(text, SECTION_TEXT_SIZE_SP).apply {
            setTypeface(typeface, Typeface.BOLD)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                isAccessibilityHeading = true
            }
        }

    private fun actionButton(text: CharSequence, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        setTextColor(buttonTextColors())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, BUTTON_TEXT_SIZE_SP)
        isAllCaps = false
        includeFontPadding = false
        gravity = Gravity.CENTER
        minHeight = dp(ACTION_HEIGHT_DP)
        minimumWidth = dp(ACTION_HEIGHT_DP)
        setPadding(dp(10), 0, dp(10), 0)
        background = outlinedButtonBackground()
        stateListAnimator = null
        setOnClickListener { onClick() }
    }

    private fun buttonTextColors(): ColorStateList = ColorStateList(
        arrayOf(
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(android.R.attr.state_selected),
            StateSet.WILD_CARD,
        ),
        intArrayOf(DISABLED_INK_COLOR, Color.WHITE, Color.BLACK),
    )

    private fun outlinedButtonBackground(): StateListDrawable = StateListDrawable().apply {
        addState(
            intArrayOf(-android.R.attr.state_enabled),
            whiteRectangle(strokeWidthDp = 1, strokeColor = DISABLED_INK_COLOR),
        )
        addState(intArrayOf(android.R.attr.state_selected), filledRectangle(Color.BLACK))
        addState(intArrayOf(android.R.attr.state_pressed), whiteRectangle(strokeWidthDp = 3))
        addState(intArrayOf(android.R.attr.state_focused), whiteRectangle(strokeWidthDp = 3))
        addState(StateSet.WILD_CARD, whiteRectangle(strokeWidthDp = 1))
    }

    private fun focusOnlyBackground(): StateListDrawable = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), whiteRectangle(strokeWidthDp = 3))
        addState(intArrayOf(android.R.attr.state_focused), whiteRectangle(strokeWidthDp = 3))
        addState(StateSet.WILD_CARD, ColorDrawable(Color.WHITE))
    }

    private fun whiteRectangle(
        strokeWidthDp: Int,
        strokeColor: Int = Color.BLACK,
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(Color.WHITE)
        setStroke(dp(strokeWidthDp), strokeColor)
        cornerRadius = 0f
    }

    private fun filledRectangle(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = 0f
    }

    private fun assetTypeface(path: String): Typeface = try {
        Typeface.createFromAsset(assets, path)
    } catch (_: RuntimeException) {
        Typeface.SANS_SERIF
    }

    private fun weightedTypeface(weight: Int): Typeface =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(Typeface.SANS_SERIF, weight, false)
        } else {
            Typeface.create(
                Typeface.SANS_SERIF,
                if (weight >= 600) Typeface.BOLD else Typeface.NORMAL,
            )
        }

    private fun verticalSpace(heightDp: Int): View = View(this).apply {
        setBackgroundColor(Color.WHITE)
        layoutParams = LinearLayout.LayoutParams(1, dp(heightDp))
    }

    private fun horizontalSpace(widthDp: Int): View = View(this).apply {
        setBackgroundColor(Color.WHITE)
        layoutParams = LinearLayout.LayoutParams(dp(widthDp), 1)
    }

    private fun linearWrapParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    private fun linearMatchWrapParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    private fun compactButtonParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun clearManagementReferences() {
        renameDialog?.dismiss()
        renameDialog = null
        renameDialogInput = null
        renameDialogComponent = null
        selectedContainer = null
        selectedCountView = null
        availableContainer = null
        pageIndicatorView = null
        previousPageButton = null
        nextPageButton = null
        defaultLauncherContainer = null
        firstRunHintView = null
        homeTextSizeValueView = null
        homeGridContainer = null
        displayPresetButtons.clear()
        homeClockModeButtons.clear()
        updateContainer = null
        cachedApps = emptyList()
    }

    private fun clearHomeReferences() {
        homeRoot = null
        homeErrorView = null
        homeManagementButton = null
        homeAppsContainer = null
        homePagerSpacingView = null
        homePageIndicatorView = null
        previousHomePageButton = null
        nextHomePageButton = null
        homePageCount = 1
        homeApps = emptyList()
    }

    private enum class Screen {
        HOME,
        MANAGEMENT,
    }

    private enum class UpdateState {
        IDLE,
        CHECKING,
        UP_TO_DATE,
        AVAILABLE,
        DOWNLOADING,
        READY,
        INSTALL_PERMISSION_REQUIRED,
        CHECK_FAILED,
        DOWNLOAD_FAILED,
        INSTALL_FAILED,
        FILE_UNAVAILABLE,
    }

    private companion object {
        const val REQUEST_HOME_ROLE = 1001
        const val AVAILABLE_PAGE_SIZE = 6
        const val UPDATE_CACHE_DIRECTORY = "updates"
        const val UPDATE_THREAD_NAME = "EInkLauncherUpdate"
        const val STATE_SCREEN = "state_screen"
        const val STATE_HOME_PAGE = "state_home_page"
        const val STATE_AVAILABLE_PAGE = "state_available_page"
        const val STATE_RENAME_COMPONENT = "state_rename_component"
        const val STATE_RENAME_DRAFT = "state_rename_draft"

        const val HOME_SIDE_MARGIN_DP = 40
        const val HOME_MAX_COLUMN_WIDTH_DP = 420
        const val HOME_ROW_SPACING_DP = 4
        const val HOME_COLUMN_SPACING_DP = 8
        const val HOME_GRID_VALUE_WIDTH_DP = 40
        const val HOME_CONTENT_TOP_RESERVE_DP = 64
        const val HOME_CONTENT_BOTTOM_RESERVE_DP = 72
        const val HOME_PAGER_TOP_SPACING_DP = 8
        const val HOME_MANAGEMENT_BUTTON_SIZE_DP = 48
        const val HOME_MANAGEMENT_BUTTON_MARGIN_DP = 20
        const val HOME_ICON_LABEL_SPACING_DP = 12
        const val HOME_ICON_TEXT_SCALE = 1.3f
        const val HOME_ICON_ROW_PADDING_DP = 16
        const val HOME_ICON_MIN_DP = 20
        const val HOME_ICON_MAX_DP = 40
        const val MANAGEMENT_SIDE_MARGIN_DP = 24
        const val MANAGEMENT_TOP_MARGIN_DP = 56
        const val MANAGEMENT_CONTENT_TOP_MARGIN_DP = 112
        const val MANAGEMENT_ROW_HEIGHT_DP = 48
        const val MIN_STATUS_TOUCH_HEIGHT_DP = 48
        const val STATUS_TOP_MARGIN_DP = 4
        const val STATUS_END_MARGIN_DP = 20
        const val STATUS_HORIZONTAL_PADDING_DP = 8
        const val TIME_ICON_SIZE_DP = 22
        const val CHARGING_ICON_SIZE_DP = 14
        const val CHARGING_ICON_PADDING_DP = 3
        const val STATUS_ITEM_SPACING_DP = 10
        const val ACTION_HEIGHT_DP = 48
        const val RENAME_DIALOG_MAX_WIDTH_DP = 520
        const val RENAME_DIALOG_SIDE_MARGIN_DP = 24
        const val DISABLED_INK_COLOR = -10_066_330
        const val WIFI_DIM_INK_COLOR = -7_829_368
        const val HOME_TEXT_LINE_HEIGHT_FACTOR = 1.3f
        const val INTER_SEMI_BOLD_ASSET_PATH = "fonts/Inter-SemiBold.ttf"

        const val HOME_EMPTY_TEXT_SIZE_SP = 23f
        const val TITLE_TEXT_SIZE_SP = 24f
        const val SECTION_TEXT_SIZE_SP = 18f
        const val BODY_TEXT_SIZE_SP = 16f
        const val TIME_TEXT_SIZE_SP = 22f
        const val SMALL_TEXT_SIZE_SP = 14f
        const val BUTTON_TEXT_SIZE_SP = 14f
    }
}

/** Keeps management usable on short screens without adding inertial motion. */
private class NoFlingScrollView(context: Context) : ScrollView(context) {
    override fun fling(velocityY: Int) = Unit
}
