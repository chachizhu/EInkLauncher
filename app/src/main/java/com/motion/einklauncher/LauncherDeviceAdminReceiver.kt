package com.motion.einklauncher

import android.app.admin.DeviceAdminReceiver

/**
 * Exists only so Home may call [android.app.admin.DevicePolicyManager.lockNow]. An ordinary app
 * cannot lock the screen any other way: `PowerManager.goToSleep` needs DEVICE_POWER, which is
 * system-only. The sole policy declared in `res/xml/device_admin.xml` is force-lock.
 */
class LauncherDeviceAdminReceiver : DeviceAdminReceiver()