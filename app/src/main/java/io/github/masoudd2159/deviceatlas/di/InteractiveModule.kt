package io.github.masoudd2159.deviceatlas.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.masoudd2159.deviceatlas.data.interactive.*
import io.github.masoudd2159.deviceatlas.data.nativecore.RustSensorAnalyzer
import io.github.masoudd2159.deviceatlas.foundation.interactive.*

@Module
@InstallIn(SingletonComponent::class)
abstract class InteractiveModule {
    @Binds abstract fun sensors(impl: AndroidInteractiveSensors): InteractiveSensors
    @Binds abstract fun haptics(impl: AndroidInteractiveHaptics): InteractiveHaptics
    @Binds abstract fun analyzer(impl: RustSensorAnalyzer): SensorAnalyzer
}
