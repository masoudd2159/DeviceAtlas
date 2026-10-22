package io.github.masoudd2159.deviceatlas.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.masoudd2159.deviceatlas.data.nativecore.RustNativeMath
import io.github.masoudd2159.deviceatlas.foundation.NativeMath
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NativeDispatcher

@Module
@InstallIn(SingletonComponent::class)
abstract class NativeCoreModule {
    @Binds
    abstract fun bindNativeMath(impl: RustNativeMath): NativeMath

    companion object {
        @Provides
        @NativeDispatcher
        fun provideNativeDispatcher(): CoroutineDispatcher = Dispatchers.Default
    }
}
