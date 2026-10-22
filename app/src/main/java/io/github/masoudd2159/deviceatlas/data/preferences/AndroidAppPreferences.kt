package io.github.masoudd2159.deviceatlas.data.preferences

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.masoudd2159.deviceatlas.di.PreferenceDispatcher
import io.github.masoudd2159.deviceatlas.foundation.AppPreferences
import io.github.masoudd2159.deviceatlas.foundation.ThemePreference
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** No disk access in construction or on Main; only a non-sensitive theme key. */
class AndroidAppPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
    @PreferenceDispatcher private val dispatcher: CoroutineDispatcher,
) : AppPreferences {
    override suspend fun readTheme(): ThemePreference = withContext(dispatcher) {
        ThemePreference.fromStorage(preferences().getString("theme", null))
    }

    override suspend fun writeTheme(theme: ThemePreference): Boolean = withContext(dispatcher) {
        // Report actual durability, not an optimistic UI update after apply().
        preferences().edit().putString("theme", theme.storageKey).commit()
    }

    private fun preferences() = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
}
