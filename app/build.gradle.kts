plugins {
    alias(libs.plugins.agp.app)
    alias(libs.plugins.kotlin)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "eu.hxreborn.remembermysort"
    compileSdk = 36

    defaultConfig {
        applicationId = "eu.hxreborn.remembermysort"
        minSdk = 31
        targetSdk = 36

        versionCode = 100
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            fun secret(name: String): String? =
                providers
                    .gradleProperty(name)
                    .orElse(providers.environmentVariable(name))
                    .orNull

            val storeFilePath = secret("RELEASE_STORE_FILE")
            val storePassword = secret("RELEASE_STORE_PASSWORD")
            val keyAlias = secret("RELEASE_KEY_ALIAS")
            val keyPassword = secret("RELEASE_KEY_PASSWORD")
            val storeType = secret("RELEASE_STORE_TYPE") ?: "PKCS12"

            if (!storeFilePath.isNullOrBlank()) {
                storeFile = file(storeFilePath)
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
                this.storeType = storeType

                enableV1Signing = false
                enableV2Signing = true
            } else {
                logger.warn("RELEASE_STORE_FILE not found. Release signing is disabled.")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig =
                signingConfigs.getByName("release").takeIf { it.storeFile != null }
                    ?: signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildFeatures {
        buildConfig = true
    }

    kotlin {
        jvmToolchain(21)
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
        }
    }

    lint {
        abortOnError = true
        disable.addAll(listOf("OldTargetApi", "PrivateApi", "DiscouragedPrivateApi"))
        ignoreTestSources = true
    }

    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.add("-Xlint:none")
    }
}

ktlint {
    version.set("1.4.1")
    android.set(true)
    ignoreFailures.set(false)
}

dependencies {
    compileOnly(files("$rootDir/libs/api-100.aar"))
    compileOnly(files("$rootDir/libs/interface-100.aar"))
}
