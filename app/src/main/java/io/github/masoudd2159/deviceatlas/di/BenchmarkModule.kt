package io.github.masoudd2159.deviceatlas.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.masoudd2159.deviceatlas.data.benchmark.AndroidBenchmarkSafetySource
import io.github.masoudd2159.deviceatlas.data.nativecore.RustBenchmarkProcessor
import io.github.masoudd2159.deviceatlas.foundation.benchmark.*

@Module
@InstallIn(SingletonComponent::class)
abstract class BenchmarkModule {
    @Binds abstract fun safety(impl: AndroidBenchmarkSafetySource): BenchmarkSafetySource
    @Binds abstract fun processor(impl: RustBenchmarkProcessor): BenchmarkProcessor
}
