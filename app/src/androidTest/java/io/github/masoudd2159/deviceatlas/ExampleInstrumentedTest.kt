package io.github.masoudd2159.deviceatlas

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Guards the application identity across build configuration changes. */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("io.github.masoudd2159.deviceatlas", appContext.packageName)
    }

    @Test fun packagedApplicationAllowsOnlyNormalVibrationAndPrivateReceiverPermission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        assertEquals(setOf(android.Manifest.permission.VIBRATE,
            "${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"), info.requestedPermissions.orEmpty().toSet())
        val vibration = context.packageManager.getPermissionInfo(android.Manifest.permission.VIBRATE, 0)
        assertEquals(android.content.pm.PermissionInfo.PROTECTION_NORMAL,
            vibration.protectionLevel and android.content.pm.PermissionInfo.PROTECTION_MASK_BASE)
    }

    @Test fun packagedApplicationDisablesBackup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }
}
