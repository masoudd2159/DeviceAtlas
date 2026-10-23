package io.github.masoudd2159.deviceatlas.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.masoudd2159.deviceatlas.data.hardware.AndroidHardwareCollector
import io.github.masoudd2159.deviceatlas.data.hardware.ProcessedHardwareReader
import io.github.masoudd2159.deviceatlas.data.nativecore.RustHardwareProcessor
import io.github.masoudd2159.deviceatlas.foundation.hardware.*
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class HardwareIoDispatcher

@Module
@InstallIn(SingletonComponent::class)
abstract class HardwareModule {
    @Binds abstract fun collector(impl: AndroidHardwareCollector): HardwareCollector
    @Binds abstract fun processor(impl: RustHardwareProcessor): HardwareProcessor
    @Binds abstract fun reader(impl: ProcessedHardwareReader): HardwareReader
    companion object {
        @Provides @HardwareIoDispatcher fun io(): CoroutineDispatcher = Dispatchers.IO
    }
}
