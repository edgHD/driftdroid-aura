import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
}

// DriftDroid Aura's release key lives outside the repo, described by a properties file (storeFile,
// storePassword, keyAlias, keyPassword) at $DRIFTDROID_SIGNING_PROPERTIES or
// ~/.driftdroid-aura/release-signing.properties. Without one, release builds use the debug key.
val releaseSigning: Properties? =
    (System.getenv("DRIFTDROID_SIGNING_PROPERTIES")?.let { File(it) }
        ?: File(System.getProperty("user.home"), ".driftdroid-aura/release-signing.properties"))
        .takeIf { it.isFile }
        ?.let { source -> Properties().apply { source.inputStream().use { load(it) } } }

android {
    namespace = "com.driftdroid.android"
    compileSdk = 34
    ndkVersion = "27.2.12479018"

    defaultConfig {
        // Aura installs as its own app, next to DriftDroid. Only the ID Android knows it by
        // changes: the Kotlin classes keep the com.driftdroid.android namespace because the
        // game libraries bind their JNI functions by those class names
        // (Java_com_driftdroid_android_MainActivity_...).
        applicationId = "com.driftdroid.aura"
        minSdk = 29
        targetSdk = 34
        // DriftDroid Aura, a fork of DriftDroid 1.4.0. versionCode = upstream code (7) x 100 + this
        // fork's build number, so it stays above upstream and keeps rising across fork releases.
        versionCode = 701
        versionName = "1.4.0-aura.1"

        ndk {
            abiFilters += "arm64-v8a"
        }

        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_shared"
                cppFlags += "-std=c++20"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.28.3"
        }
    }

    sourceSets {
        getByName("main") {
            // cacert.pem: the TLS root bundle network_ssl.cpp needs; same file desktop builds copy
            // next to the executable (runtime/cmake/PublicProducts.cmake).
            assets.srcDir("../../runtime/assets/certs")
            // initial_pipeline_cache.db: shader recipes imported on first launch
            // (aurora-main/lib/gfx/pipeline_cache.cpp).
            assets.srcDir("../../runtime/assets/pipeline")
        }
    }

    androidResources {
        // SQLite reads the pipeline seed with random access through SDL's asset stream.
        noCompress += "db"
    }

    signingConfigs {
        if (releaseSigning != null) {
            create("release") {
                storeFile = file(releaseSigning.getProperty("storeFile"))
                storePassword = releaseSigning.getProperty("storePassword")
                keyAlias = releaseSigning.getProperty("keyAlias")
                keyPassword = releaseSigning.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Published releases are signed with the release key above. Without it, release-type
            // builds fall back to the debug key so they can still be installed and measured
            // directly: Android only enables CheckJNI (and its per-call class/field validation,
            // ~10% of the UI thread in a debug profile) for debuggable builds, so performance
            // testing has to happen here, not on the debug variant.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug {
            isJniDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    // libarchive for mod downloads: GameBanana's Mario Kart Wii mods come as .zip, .7z and RAR5 .rar,
    // and RAR5 has no pure-Java reader. BSD-licensed libarchive behind an Apache-2.0 JNI wrapper.
    implementation("me.zhanghai.android.libarchive:library:1.1.4")

    testImplementation("junit:junit:4.13.2")
    // Android's org.json is a stub in local unit tests; this is the real implementation.
    testImplementation("org.json:json:20240303")
}
