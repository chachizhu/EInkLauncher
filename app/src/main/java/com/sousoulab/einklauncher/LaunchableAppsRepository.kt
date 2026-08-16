package com.sousoulab.einklauncher

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Process
import java.text.Collator

internal data class LaunchableApp(
    val componentName: ComponentName,
    val label: String,
)

internal class LaunchableAppsRepository(private val context: Context) {
    private val packageManager = context.packageManager
    private val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    fun loadApps(): List<LaunchableApp> {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = queryActivities(query).mapNotNull(::toLaunchableApp).distinctBy { it.componentName }

        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        return apps.sortedWith { first, second ->
            val labelOrder = collator.compare(first.label, second.label)
            if (labelOrder != 0) {
                labelOrder
            } else {
                first.componentName.flattenToString()
                    .compareTo(second.componentName.flattenToString())
            }
        }
    }

    /** Resolves only the saved components so returning Home never scans every installed app. */
    fun loadApps(components: List<ComponentName>): List<LaunchableApp> = components
        .distinct()
        .take(SelectionPolicy.MAX_SELECTED_APPS)
        .let { selectedComponents ->
            val activitiesByPackage = mutableMapOf<String, List<LauncherActivityInfo>>()
            selectedComponents.mapNotNull { component ->
                val activities = activitiesByPackage.getOrPut(component.packageName) {
                    runCatching {
                        launcherApps.getActivityList(component.packageName, Process.myUserHandle())
                    }.getOrDefault(emptyList())
                }
                activities
                    .firstOrNull { it.componentName == component }
                    ?.let(::toLaunchableApp)
            }
        }

    private fun queryActivities(query: Intent): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                query,
                PackageManager.ResolveInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(query, 0)
        }

    private fun toLaunchableApp(resolveInfo: ResolveInfo): LaunchableApp? {
        val activityInfo = resolveInfo.activityInfo ?: return null
        if (activityInfo.packageName == context.packageName || !activityInfo.enabled) return null

        val component = ComponentName(activityInfo.packageName, activityInfo.name)
        val label = runCatching { resolveInfo.loadLabel(packageManager).toString().trim() }
            .getOrDefault("")
            .ifEmpty { activityInfo.packageName }
        return LaunchableApp(component, label)
    }

    private fun toLaunchableApp(activityInfo: LauncherActivityInfo): LaunchableApp? {
        val component = activityInfo.componentName
        if (component.packageName == context.packageName) return null
        val label = activityInfo.label.toString().trim().ifEmpty { component.packageName }
        return LaunchableApp(component, label)
    }

    fun launch(componentName: ComponentName): Boolean {
        val intent = Intent.makeMainActivity(componentName).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        val options = ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle()

        return try {
            context.startActivity(intent, options)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
