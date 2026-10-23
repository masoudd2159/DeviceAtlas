package io.github.masoudd2159.deviceatlas.di

import android.os.SystemClock
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.masoudd2159.deviceatlas.data.monitoring.AndroidMonitoringSource
import io.github.masoudd2159.deviceatlas.data.nativecore.RustMonitoringProcessor
import io.github.masoudd2159.deviceatlas.foundation.monitoring.*

@Module
@InstallIn(SingletonComponent::class)
abstract class MonitoringModule {
    @Binds abstract fun source(impl: AndroidMonitoringSource): MonitoringSource
    @Binds abstract fun processor(impl: RustMonitoringProcessor): MonitoringProcessor
    companion object {
        @Provides fun clock(): MonotonicClock = MonotonicClock(SystemClock::elapsedRealtimeNanos)
    }
}
