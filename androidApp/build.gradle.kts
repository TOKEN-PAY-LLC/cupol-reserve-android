import org.gradle.api.tasks.compile.JavaCompile
import java.io.File

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

/** The app version: -PappVersion=1.2.3 (the release workflow passes the tag). */
val appVersion = (findProperty("appVersion") as String?)?.removePrefix("v")?.takeIf { it.isNotBlank() } ?: "0.1.0"

/** 1.2.3 -> 10203: grows with every release as Android requires. */
val appVersionCode = appVersion.split('.', '-').take(3).map { it.toIntOrNull() ?: 0 }
    .let { (it + listOf(0, 0, 0)).take(3) }.let { (major, minor, patch) -> major * 10000 + minor * 100 + patch }

/** The core the gomobile library was built from, written by scripts/build-android-core.sh. */
val coreVersion = file("libs/openflux-core.version").takeIf { it.isFile }?.readText()?.trim() ?: "встроенное"

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "io.openflux.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        // Separate application id: installs beside CUPOL VPN.
        applicationId = "space.cupol.reserve"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = appVersionCode
        versionName = appVersion
        buildConfigField("String", "CORE_VERSION", "\"$coreVersion\"")
    }

    buildFeatures {
        buildConfig = true
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = true
        }
    }

    signingConfigs {
        create("release") {
            val keystore = System.getenv("ANDROID_KEYSTORE_FILE")
            if (!keystore.isNullOrBlank()) {
                storeFile = file(keystore)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        // The Go core loads as a plain .so; extracting it keeps startup simple.
        jniLibs.useLegacyPackaging = true
    }
}

dependencies {
    implementation(project(":shared"))
    // The OpenFlux core (gomobile), built by scripts/build-android-core.sh.
    implementation(files("libs/openflux.aar"))
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.components.resources)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core)
    implementation(libs.androidx.webkit)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.zxing.core)
    implementation(libs.zxing.android)
}

// Lint 9.2 misidentifies this Kotlin 2.4 Activity hierarchy. The manifest
// suppresses that one finding; the release still verifies the actual JVM
// superclass chain and public constructor before an APK can be assembled.
val verifyReleaseActivityHierarchy by tasks.registering {
    dependsOn("compileReleaseKotlin", "compileReleaseJavaWithJavac")
    val compiled = layout.buildDirectory.dir("tmp/kotlin-classes/release")
    inputs.dir(compiled)
    val report = layout.buildDirectory.file("reports/release-activity-hierarchy.txt")
    outputs.file(report)
    doLast {
        val javaCompile = tasks.named<JavaCompile>("compileReleaseJavaWithJavac").get()
        val classpath = (javaCompile.classpath.files + compiled.get().asFile)
            .joinToString(File.pathSeparator) { it.absolutePath }
        val tool = File(System.getProperty("java.home"),
            "bin/javap" + if (System.getProperty("os.name").startsWith("Windows")) ".exe" else "")
        fun inspect(name: String): String {
            val process = ProcessBuilder(tool.absolutePath, "-classpath", classpath, name)
                .redirectErrorStream(true).start()
            val text = process.inputStream.bufferedReader().readText()
            check(process.waitFor() == 0) { "Could not inspect $name" }
            return text
        }
        val activity = inspect("io.openflux.android.MainActivity")
        val component = inspect("androidx.activity.ComponentActivity")
        val core = inspect("androidx.core.app.ComponentActivity")
        check(activity.contains("public final class io.openflux.android.MainActivity extends androidx.activity.ComponentActivity"))
        check(activity.contains("public io.openflux.android.MainActivity();"))
        check(component.contains("extends androidx.core.app.ComponentActivity"))
        check(core.contains("extends android.app.Activity"))
        report.get().asFile.apply {
            parentFile.mkdirs()
            writeText("Public no-argument constructor and Activity superclass chain verified.\n")
        }
    }
}
tasks.matching { it.name == "assembleRelease" }.configureEach {
    dependsOn(verifyReleaseActivityHierarchy)
}
