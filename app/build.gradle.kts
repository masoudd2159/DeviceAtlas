import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    buildToolsVersion = "37.0.0"
    namespace = "io.github.masoudd2159.deviceatlas"
    ndkVersion = "30.0.16248370"

    compileSdk { version = release(37) }

    defaultConfig {
        applicationId = "io.github.masoudd2159.deviceatlas"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86") }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

// The Compose plugin selects the matching Kotlin compiler for AGP's built-in support.
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

// Public variant API wiring means host-only Kotlin tests need no Android native build.
abstract class BuildRust : DefaultTask() {
    @get:Internal
    abstract val sourceDirectory: DirectoryProperty
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection
    @get:InputFile @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val toolchainFile: RegularFileProperty
    @get:Input abstract val releaseBuild: Property<Boolean>
    @get:Input abstract val apiLevel: Property<Int>
    @get:Input abstract val ndkRevision: Property<String>
    @get:Internal abstract val ndkDirectory: DirectoryProperty
    @get:Input abstract val cargoExecutable: Property<String>
    @get:Input abstract val cargoHome: Property<String>
    @get:Input abstract val offlineBuild: Property<Boolean>
    @get:OutputDirectory abstract val jniLibs: DirectoryProperty
    @get:LocalState abstract val cargoTarget: DirectoryProperty
    @get:Inject abstract val exec: ExecOperations

    @TaskAction fun build() {
        val executableName = if (File.separatorChar == '\\') "cargo.exe" else "cargo"
        val installedCargo = File(cargoHome.get(), "bin/$executableName")
        val cargo = cargoExecutable.orNull?.takeIf { it.isNotBlank() }
            ?: installedCargo.takeIf { it.isFile }?.absolutePath
            ?: executableName
        val cargoBin = File(cargoHome.get(), "bin").absolutePath
        val executableBin = File(cargo).parent
        val rustPath = listOfNotNull(executableBin, cargoBin).distinct().joinToString(File.pathSeparator)
        val ndk = ndkDirectory.get().asFile
        check(ndk.resolve("source.properties").isFile) {
            "Install Android NDK ${ndkRevision.get()} in the SDK Manager."
        }
        val version = ByteArrayOutputStream()
        exec.exec {
            val pathKey = environment.keys.firstOrNull { it.equals("PATH", ignoreCase = true) } ?: "PATH"
            environment(pathKey, rustPath + File.pathSeparator + environment[pathKey]?.toString().orEmpty())
            workingDir(sourceDirectory.get().asFile)
            commandLine(cargo, "ndk", "--version")
            standardOutput = version
        }
        check(version.toString().trim() == "cargo-ndk 4.1.2") {
            "Install cargo-ndk 4.1.2: cargo install cargo-ndk --version 4.1.2 --locked"
        }
        exec.exec {
            val pathKey = environment.keys.firstOrNull { it.equals("PATH", ignoreCase = true) } ?: "PATH"
            environment(pathKey, rustPath + File.pathSeparator + environment[pathKey]?.toString().orEmpty())
            workingDir(sourceDirectory.get().asFile)
            environment("ANDROID_NDK_HOME", ndk.absolutePath)
            environment("CARGO_TARGET_DIR", cargoTarget.get().asFile.absolutePath)
            val args = mutableListOf(cargo, "ndk", "--platform", apiLevel.get().toString())
            listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86").forEach { args += listOf("-t", it) }
            args += listOf("-o", jniLibs.get().asFile.absolutePath, "build", "--locked")
            if (offlineBuild.get()) args += "--offline"
            if (releaseBuild.get()) args += "--release"
            commandLine(args)
        }
    }
}

androidComponents {
    val configuredNdkRevision = android.ndkVersion
    onVariants(selector().all()) { variant ->
        val rust = tasks.register<BuildRust>("buildRust${variant.name.replaceFirstChar { it.uppercase() }}") {
            group = "build"
            description = "Build the Rust JNI core for ${variant.name} and all supported ABIs."
            sourceDirectory.set(rootProject.layout.projectDirectory.dir("native"))
            sourceFiles.from(rootProject.fileTree("native") {
                include("Cargo.toml", "Cargo.lock", "src/**")
            })
            toolchainFile.set(rootProject.layout.projectDirectory.file("rust-toolchain.toml"))
            releaseBuild.set(variant.buildType == "release")
            apiLevel.set(26)
            ndkRevision.set(configuredNdkRevision)
            ndkDirectory.set(sdkComponents.sdkDirectory.map { it.dir("ndk/$configuredNdkRevision") })
            cargoExecutable.set(providers.gradleProperty("cargoExecutable").orElse(""))
            cargoHome.set(providers.environmentVariable("CARGO_HOME").orElse(
                providers.systemProperty("user.home").map { "$it/.cargo" }
            ))
            offlineBuild.set(gradle.startParameter.isOffline)
            jniLibs.set(layout.buildDirectory.dir("generated/rust/${variant.name}/jniLibs"))
            cargoTarget.set(layout.buildDirectory.dir("rust-target/${variant.name}"))
        }
        variant.sources.jniLibs?.addGeneratedSourceDirectory(rust, BuildRust::jniLibs)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
