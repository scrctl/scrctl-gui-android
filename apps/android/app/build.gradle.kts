import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release signing is optional locally: CI and fresh checkouts fall back to debug signing.
val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.isFile) {
    FileInputStream(keystorePropertiesFile).use(keystoreProperties::load)
}
val releaseKeystore = keystoreProperties.getProperty("storeFile")
    ?.let(rootProject::file)
    ?.takeIf { it.isFile }

// `git describe --tags` yields the tag alone when HEAD is tagged and
// "tag-<count>-g<hash>[-dirty]" afterwards, so release and snapshot builds are
// distinguishable without touching the numeric version used by the updater.
val buildVersion = runCatching {
    providers.exec {
        commandLine("git", "describe", "--tags", "--always", "--dirty")
    }.standardOutput.asText.get().trim()
}.getOrNull().orEmpty().ifBlank { "dev" }

val xdbEnabled = providers.gradleProperty("scrctl.xdb").map(String::toBoolean).getOrElse(false)
val repositoryRoot = rootProject.file("../..")
val rustAbis = if (xdbEnabled) listOf("arm64-v8a") else listOf("arm64-v8a", "armeabi-v7a", "x86_64")
val xdbOutput = layout.projectDirectory.dir("build/xdb")
val rustLibraries = if (xdbEnabled) xdbOutput.dir("jniLibs").asFile
    else layout.projectDirectory.dir("src/main/jniLibs").asFile

android {
    namespace = "com.openscreens.app"
    compileSdk = 36
    if (xdbEnabled) ndkVersion = "29.0.14206865"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        applicationId = "org.scrctl.android"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("openscreens.versionCode")
            .map(String::toInt)
            .getOrElse(1)
        versionName = providers.gradleProperty("openscreens.versionName").getOrElse("0.1.0")
        buildConfigField("String", "BUILD_VERSION", "\"$buildVersion\"")

        ndk { abiFilters += rustAbis }
    }

    sourceSets["main"].apply {
        if (xdbEnabled) {
            java.srcDirs(
                "src/xdb/java",
                xdbOutput.dir("bindings"),
                repositoryRoot.resolve("android-common/java"),
                repositoryRoot.resolve("xdb/android/src/main/java"),
            )
            jniLibs.setSrcDirs(listOf(rustLibraries, repositoryRoot.resolve("app/build/native-libs")))
            assets.srcDir(repositoryRoot.resolve("app/build/native-assets"))
        } else {
            java.srcDir("src/vnc/java")
        }
    }

    buildTypes.configureEach {
        if (xdbEnabled) {
            sourceSets[name].manifest.srcFile("src/xdb/AndroidManifest.xml")
        }
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            if (xdbEnabled) applicationIdSuffix = ".xdb.debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        jniLibs.useLegacyPackaging = true
    }
}

val nativeLibraries = rustAbis.map { rustLibraries.resolve("$it/librfb_ffi.so") } + if (xdbEnabled) {
    listOf("libxdb_client.so", "libxdb_server.so", "libscrctl_jni.so", "libc++_shared.so",
        "libavcodec.so", "libavutil.so", "libswscale.so").map {
        repositoryRoot.resolve("app/build/native-libs/arm64-v8a/$it")
    }
} else emptyList()
tasks.named("preBuild") {
    doFirst {
        val missing = nativeLibraries.filterNot { it.isFile }
        check(missing.isEmpty()) {
            val command = if (xdbEnabled) "./scripts/build-android-core.sh --xdb" else "./scripts/build-android-core.sh"
            "Run $command before running Gradle. Missing native libraries: ${missing.joinToString()}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation("net.java.dev.jna:jna:5.14.0@aar")

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
