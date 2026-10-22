package io.github.masoudd2159.deviceatlas.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.masoudd2159.deviceatlas.data.preferences.AndroidAppPreferences
import io.github.masoudd2159.deviceatlas.foundation.AppPreferences
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PreferenceDispatcher

@Module
@InstallIn(SingletonComponent::class)
abstract class PreferencesModule {
    @Binds abstract fun bindPreferences(impl: AndroidAppPreferences): AppPreferences

    companion object {
        @Provides @PreferenceDispatcher
        fun providePreferenceDispatcher(): CoroutineDispatcher = Dispatchers.IO
    }
}
