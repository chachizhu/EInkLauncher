package com.sousoulab.einklauncher

import android.app.Activity
import android.app.ActivityOptions
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.text.format.DateFormat
import android.util.StateSet
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Date

class MainActivity : Activity() {
    private lateinit var preferences: LauncherPreferences
    private lateinit var appsRepository: LaunchableAppsRepository

    private var currentScreen = Screen.HOME
    private var firstRunManagement = false
    private var refreshScreenWhenResumed = false
    private var availablePage = 0
    private var cachedApps: List<LaunchableApp> = emptyList()

    private var statusView: TextView? = null
    private var selectedContainer: LinearLayout? = null
    private var selectedCountView: TextView? = null
    private var availableContainer: LinearLayout? = null
    private var pageIndicatorView: TextView? = null
    private var previousPageButton: Button? = null
    private var nextPageButton: Button? = null
    private var defaultLauncherContainer: LinearLayout? = null
    private var homeRoot: FrameLayout? = null
    private var homeErrorView: View? = null
    private var defaultLauncherErrorVisible = false

    private var currentTime = ""
    private var currentBattery = ""
    private var statusReceiverRegistered = false

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_BATTERY_CHANGED -> updateBattery(intent)
                Intent.ACTION_TIME_TICK,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                -> updateTime()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureWindow()
        preferences = LauncherPreferences(this)
        appsRepository = LaunchableAppsRepository(this)

        if (preferences.isFirstRun()) {
            showManagement(isFirstRun = true)
        } else {
            showHome()
        }
    }

    override fun onStart() {
        super.onStart()
        registerStatusReceiver()
    }

    override fun onResume() {
        super.onResume()
        hideSystemStatusBar()
        if (refreshScreenWhenResumed) {
            refreshScreenWhenResumed = false
            if (currentScreen == Screen.HOME) {
                showHome()
            } else {
                cachedApps = appsRepository.loadApps()
                renderDefaultLauncherSection()
                renderSelectedApps()
                renderAvailableApps()
            }
        }
    }

    override fun onStop() {
        refreshScreenWhenResumed = true
        unregisterStatusReceiver()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        refreshScreenWhenResumed = false
        if (preferences.isFirstRun()) {
            showManagement(isFirstRun = true)
        } else {
            showHome()
        }
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
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            showManagement(isFirstRun = false)
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
        currentScreen = Screen.HOME
        firstRunManagement = false
        clearManagementReferences()

        val root = createBaseScreen()
        homeRoot = root
        homeErrorView = null
        root.setOnLongClickListener {
            showManagement(isFirstRun = false)
            true
        }

        val selectedApps = appsRepository.loadApps(preferences.selectedComponents())
        val appList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            clipToPadding = false
        }

        if (selectedApps.isEmpty()) {
            appList.addView(
                plainText(getString(R.string.no_apps_title), HOME_TEXT_SIZE_SP).apply {
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                },
                linearWrapParams(),
            )
            appList.addView(verticalSpace(8))
            appList.addView(
                actionButton(getString(R.string.add_apps)) {
                    showManagement(isFirstRun = false)
                },
                linearWrapParams(),
            )
        } else {
            selectedApps.forEach { app ->
                appList.addView(homeAppButton(app), linearMatchWrapParams())
                appList.addView(verticalSpace(4))
            }
        }

        root.addView(
            appList,
            FrameLayout.LayoutParams(
                minOf(
                    dp(HOME_MAX_WIDTH_DP),
                    resources.displayMetrics.widthPixels - dp(HOME_SIDE_MARGIN_DP * 2),
                ).coerceAtLeast(0),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.START or Gravity.CENTER_VERTICAL,
            ).apply {
                marginStart = dp(HOME_SIDE_MARGIN_DP)
                marginEnd = dp(HOME_SIDE_MARGIN_DP)
            },
        )
        setContentView(root)
        hideSystemStatusBar()
    }

    private fun showManagement(isFirstRun: Boolean) {
        if (currentScreen == Screen.MANAGEMENT && !isFirstRun) return

        currentScreen = Screen.MANAGEMENT
        firstRunManagement = isFirstRun
        homeRoot = null
        homeErrorView = null
        defaultLauncherErrorVisible = false
        availablePage = 0
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
            plainText(getString(R.string.manage_apps_title), TITLE_TEXT_SIZE_SP).apply {
                setTypeface(typeface, Typeface.BOLD)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        header.addView(
            actionButton(getString(R.string.done), ::finishManagement),
            linearWrapParams(),
        )
        content.addView(header, linearMatchWrapParams())

        if (isFirstRun) {
            content.addView(verticalSpace(12))
            content.addView(
                plainText(getString(R.string.first_run_hint), BODY_TEXT_SIZE_SP).apply {
                    setLineSpacing(0f, 1.15f)
                },
                linearMatchWrapParams(),
            )
        }

        content.addView(verticalSpace(16))
        defaultLauncherContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
        }
        content.addView(defaultLauncherContainer, linearMatchWrapParams())

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

        root.addView(
            scrollView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ).apply {
                topMargin = dp(MANAGEMENT_TOP_MARGIN_DP)
            },
        )

        setContentView(root)
        renderDefaultLauncherSection()
        renderSelectedApps()
        renderAvailableApps()
        hideSystemStatusBar()
    }

    private fun finishManagement() {
        val shouldRequestHomeRole = firstRunManagement && !isDefaultLauncher()
        if (firstRunManagement) preferences.markOnboardingComplete()
        showHome()
        if (shouldRequestHomeRole) requestDefaultLauncher()
    }

    private fun renderSelectedApps() {
        val container = selectedContainer ?: return
        val selected = preferences.selectedComponents()
        val appsByComponent = cachedApps.associateBy { it.componentName }
        selectedCountView?.text = getString(R.string.selected_count, selected.size)
        container.removeAllViews()

        selected.forEachIndexed { index, component ->
            val app = appsByComponent[component]
            val label = app?.label ?: component.packageName
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
                },
                linearMatchWrapParams(),
            )
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
                contentDescription = getString(R.string.move_app_up, label)
            }
            val downButton = actionButton(getString(R.string.move_down)) {
                updateSelection { SelectionPolicy.moveDown(it, component) }
            }.apply {
                isEnabled = index < selected.lastIndex
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

    private fun renderAvailableApps() {
        val container = availableContainer ?: return
        val selected = preferences.selectedComponents()
        val selectedSet = selected.toHashSet()
        val available = cachedApps.filterNot { it.componentName in selectedSet }
        val page = SelectionPolicy.page(available, availablePage, AVAILABLE_PAGE_SIZE)
        availablePage = page.pageIndex
        container.removeAllViews()

        page.items.forEachIndexed { index, app ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(MANAGEMENT_ROW_HEIGHT_DP)
            }
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
                    isEnabled = selected.size < SelectionPolicy.MAX_SELECTED_APPS
                },
                compactButtonParams(),
            )
            container.addView(row, linearMatchWrapParams())
            if (index < page.items.lastIndex) container.addView(verticalSpace(4))
        }

        pageIndicatorView?.text = getString(
            R.string.page_indicator,
            page.pageIndex + 1,
            page.pageCount,
        )
        previousPageButton?.isEnabled = page.hasPrevious
        nextPageButton?.isEnabled = page.hasNext
    }

    private fun addSelectedApp(component: ComponentName) {
        val selected = preferences.selectedComponents()
        if (selected.size >= SelectionPolicy.MAX_SELECTED_APPS) return
        updateSelection { SelectionPolicy.add(it, component) }
    }

    private fun updateSelection(transform: (List<ComponentName>) -> List<ComponentName>) {
        val oldSelection = preferences.selectedComponents()
        val newSelection = transform(oldSelection)
        if (newSelection == oldSelection) return
        preferences.saveSelectedComponents(newSelection)
        renderSelectedApps()
        renderAvailableApps()
    }

    private fun homeAppButton(app: LaunchableApp): TextView =
        plainText(app.label, HOME_TEXT_SIZE_SP).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), 0)
            minHeight = dp(HOME_ROW_HEIGHT_DP)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            isClickable = true
            isFocusable = true
            background = focusOnlyBackground()
            setOnClickListener {
                if (appsRepository.launch(app.componentName)) {
                    @Suppress("DEPRECATION")
                    overridePendingTransition(0, 0)
                } else {
                    showHomeLaunchError()
                }
            }
        }

    private fun showHomeLaunchError() {
        val root = homeRoot ?: return
        if (homeErrorView != null) return

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(8), dp(8))
            background = whiteRectangle(strokeWidthDp = 1)
        }
        panel.addView(
            plainText(getString(R.string.unable_to_open_app), BODY_TEXT_SIZE_SP),
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
                bottomMargin = dp(24)
            },
        )
    }

    private fun createBaseScreen(): FrameLayout {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
            clipChildren = false
        }
        statusView = plainText("", STATUS_TEXT_SIZE_SP).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            typeface = Typeface.MONOSPACE
            setPadding(dp(8), 0, dp(8), 0)
            isFocusable = true
            setOnLongClickListener {
                if (currentScreen != Screen.MANAGEMENT) {
                    showManagement(isFirstRun = false)
                }
                true
            }
        }
        root.addView(
            statusView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(STATUS_HEIGHT_DP),
                Gravity.TOP or Gravity.END,
            ).apply {
                topMargin = dp(STATUS_TOP_MARGIN_DP)
                marginEnd = dp(STATUS_END_MARGIN_DP)
            },
        )
        updateTime()
        updateStatusView()
        return root
    }

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
        updateTime()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
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

    private fun updateTime() {
        val formatted = DateFormat.getTimeFormat(this).format(Date())
        if (formatted != currentTime) {
            currentTime = formatted
            updateStatusView()
        }
    }

    private fun updateBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val batteryText = if (level < 0 || scale <= 0) {
            getString(R.string.battery_unknown)
        } else {
            val percentage = ((level * 100f) / scale).toInt().coerceIn(0, 100)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            getString(
                if (charging) R.string.battery_charging else R.string.battery_percentage,
                percentage,
            )
        }
        if (batteryText != currentBattery) {
            currentBattery = batteryText
            updateStatusView()
        }
    }

    private fun updateStatusView() {
        val view = statusView ?: return
        val battery = currentBattery.ifEmpty { getString(R.string.battery_unknown) }
        val status = getString(R.string.time_battery_format, currentTime, battery)
        if (view.text.toString() != status) view.text = status
        view.contentDescription = getString(R.string.status_summary, currentTime, battery)
    }

    @Suppress("DEPRECATION")
    private fun configureWindow() {
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
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
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
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
        }

    private fun actionButton(text: CharSequence, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        setTextColor(Color.BLACK)
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

    private fun outlinedButtonBackground(): StateListDrawable = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), whiteRectangle(strokeWidthDp = 2))
        addState(intArrayOf(android.R.attr.state_focused), whiteRectangle(strokeWidthDp = 2))
        addState(StateSet.WILD_CARD, whiteRectangle(strokeWidthDp = 1))
    }

    private fun focusOnlyBackground(): StateListDrawable = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), whiteRectangle(strokeWidthDp = 2))
        addState(intArrayOf(android.R.attr.state_focused), whiteRectangle(strokeWidthDp = 2))
        addState(StateSet.WILD_CARD, ColorDrawable(Color.WHITE))
    }

    private fun whiteRectangle(strokeWidthDp: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(Color.WHITE)
        setStroke(dp(strokeWidthDp), Color.BLACK)
        cornerRadius = 0f
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
        selectedContainer = null
        selectedCountView = null
        availableContainer = null
        pageIndicatorView = null
        previousPageButton = null
        nextPageButton = null
        defaultLauncherContainer = null
        homeRoot = null
        homeErrorView = null
        cachedApps = emptyList()
    }

    private enum class Screen {
        HOME,
        MANAGEMENT,
    }

    private companion object {
        const val REQUEST_HOME_ROLE = 1001
        const val AVAILABLE_PAGE_SIZE = 6

        const val HOME_SIDE_MARGIN_DP = 40
        const val HOME_MAX_WIDTH_DP = 420
        const val HOME_ROW_HEIGHT_DP = 52
        const val MANAGEMENT_SIDE_MARGIN_DP = 24
        const val MANAGEMENT_TOP_MARGIN_DP = 56
        const val MANAGEMENT_ROW_HEIGHT_DP = 48
        const val STATUS_HEIGHT_DP = 48
        const val STATUS_TOP_MARGIN_DP = 4
        const val STATUS_END_MARGIN_DP = 20
        const val ACTION_HEIGHT_DP = 48

        const val HOME_TEXT_SIZE_SP = 23f
        const val TITLE_TEXT_SIZE_SP = 24f
        const val SECTION_TEXT_SIZE_SP = 18f
        const val BODY_TEXT_SIZE_SP = 16f
        const val STATUS_TEXT_SIZE_SP = 15f
        const val SMALL_TEXT_SIZE_SP = 14f
        const val BUTTON_TEXT_SIZE_SP = 14f
    }
}

/** Keeps management usable on short screens without adding inertial motion. */
private class NoFlingScrollView(context: Context) : ScrollView(context) {
    override fun fling(velocityY: Int) = Unit
}
