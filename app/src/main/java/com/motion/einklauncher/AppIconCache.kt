package com.motion.einklauncher

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Grayscale launcher-icon cache: memory first, then PNGs on disk, and only a
 * full miss generates an icon from the system drawable. Management warms the
 * cache in the background so Home usually binds icons without any generation
 * work; disk entries are keyed by the package's last update time so updated
 * apps regenerate their icons automatically.
 */
internal class AppIconCache(private val context: Context) {
    private val lock = Any()
    private val memory = object : LinkedHashMap<ComponentName, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<ComponentName, Bitmap>?,
        ): Boolean = size > MEMORY_LIMIT
    }
    private val packageManager = context.packageManager
    private val launcherApps =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    /** Returns a fresh grayscale drawable, or null when the app provides no icon. */
    fun icon(component: ComponentName): Drawable? =
        bitmapFor(component)?.let { BitmapDrawable(context.resources, it) }

    fun warmAsync(components: List<ComponentName>) {
        val targets = components.toList()
        val thread = Thread(
            {
                targets.forEach { component ->
                    synchronized(lock) {
                        if (memory[component] == null && readDisk(component) == null) {
                            generate(component)
                        }
                    }
                }
            },
            WARM_THREAD_NAME,
        )
        thread.priority = Thread.MIN_PRIORITY
        thread.start()
    }

    private fun bitmapFor(component: ComponentName): Bitmap? = synchronized(lock) {
        memory[component] ?: readDisk(component) ?: generate(component)
    }

    /** Caller must hold [lock]. */
    private fun readDisk(component: ComponentName): Bitmap? {
        val file = diskFile(component) ?: return null
        if (!file.exists()) return null
        val bitmap = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
        if (bitmap == null) {
            runCatching { file.delete() }
            return null
        }
        memory[component] = bitmap
        return bitmap
    }

    /** Caller must hold [lock]. */
    private fun generate(component: ComponentName): Bitmap? {
        val source = loadSystemIcon(component) ?: return null
        val width = source.intrinsicWidth.takeIf { it > 0 } ?: FALLBACK_ICON_SIZE_PX
        val height = source.intrinsicHeight.takeIf { it > 0 } ?: FALLBACK_ICON_SIZE_PX

        val raw = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(raw).let { canvas ->
            source.setBounds(0, 0, width, height)
            source.draw(canvas)
        }
        val grayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(grayscale).drawBitmap(
            raw,
            0f,
            0f,
            Paint(Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(
                    ColorMatrix().apply { setSaturation(0f) },
                )
            },
        )
        raw.recycle()

        val file = diskFile(component)
        if (file != null) {
            runCatching {
                val temporary = File(file.absolutePath + ".tmp")
                FileOutputStream(temporary).use { stream ->
                    grayscale.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
                if (!temporary.renameTo(file)) temporary.delete()
            }
        }
        memory[component] = grayscale
        return grayscale
    }

    private fun loadSystemIcon(component: ComponentName): Drawable? {
        val fromLauncherApps = runCatching {
            launcherApps.getActivityList(component.packageName, Process.myUserHandle())
                .firstOrNull { it.componentName == component }
                ?.getIcon(0)
        }.getOrNull()
        if (fromLauncherApps != null) return fromLauncherApps

        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getActivityInfo(
                    component,
                    PackageManager.ComponentInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getActivityInfo(component, 0)
            }.loadIcon(packageManager)
        }.getOrNull()
    }

    private fun diskFile(component: ComponentName): File? {
        val directory = File(context.cacheDir, CACHE_DIRECTORY_NAME)
        if (!directory.exists() && !directory.mkdirs()) return null
        val lastUpdateTime = runCatching {
            packageManager.getPackageInfo(component.packageName, 0).lastUpdateTime
        }.getOrDefault(0L)
        val digest = MessageDigest.getInstance("SHA-256").digest(
            "${component.flattenToString()}@$lastUpdateTime".toByteArray(),
        )
        val name = digest.joinToString("") { "%02x".format(it) }.take(32) + ".png"
        return File(directory, name)
    }

    private companion object {
        const val MEMORY_LIMIT = 24
        const val FALLBACK_ICON_SIZE_PX = 96
        const val CACHE_DIRECTORY_NAME = "app-icons"
        const val WARM_THREAD_NAME = "EInkLauncherIconWarm"
    }
}
