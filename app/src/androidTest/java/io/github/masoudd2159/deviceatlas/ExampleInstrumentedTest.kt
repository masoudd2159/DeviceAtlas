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

    @Test fun packagedApplicationHasNoHardwareOrNetworkPermissions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        assertTrue(info.requestedPermissions.orEmpty().all {
            it == "${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        })
    }

    @Test fun packagedApplicationDisablesBackup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }
}
