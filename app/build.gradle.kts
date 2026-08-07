plugins {
    alias(libs.plugins.agp.app)
}

android {
    namespace = "eu.hxreborn.remembermysort"
    compileSdk = 36

    defaultConfig {
        applicationId = "eu.hxreborn.remembermysort"
        minSdk = 30
        targetSdk = 36
        versionCode = 300
        versionName = "3.0.0"
    }

    signingConfigs {
        create("release") {
            fun secret(name: String): String? =
                providers
                    .gradleProperty(name)
                    .orElse(providers.environmentVariable(name))
                    .orNull

            val storeFilePath = secret("RELEASE_STORE_FILE")
            if (!storeFilePath.isNullOrBlank()) {
                storeFile = file(storeFilePath)
                storePassword = secret("RELEASE_STORE_PASSWORD")
                keyAlias = secret("RELEASE_KEY_ALIAS")
                keyPassword = secret("RELEASE_KEY_PASSWORD")
                storeType = secret("RELEASE_STORE_TYPE") ?: "PKCS12"
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
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
            excludes += "**"
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
        disable.addAll(listOf("PrivateApi", "DiscouragedPrivateApi"))
        ignoreTestSources = true
    }
}

kotlin { jvmToolchain(21) }

val ktlintSrc by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs ktlint on Kotlin source files"
    mainClass.set("com.pinterest.ktlint.Main")
    classpath = configurations.detachedConfiguration(
        dependencies.create("com.pinterest.ktlint:ktlint-cli:1.8.0"),
    )
    args("src/**/*.kt")
}

tasks.named("check").configure {
    dependsOn(ktlintSrc)
}

tasks.register<JavaExec>("ktlintFormat") {
    group = "formatting"
    description = "Fix Kotlin code style"
    mainClass.set("com.pinterest.ktlint.Main")
    classpath = configurations.detachedConfiguration(
        dependencies.create("com.pinterest.ktlint:ktlint-cli:1.8.0"),
    )
    args("-F", "src/**/*.kt")
}

dependencies {
    compileOnly(libs.libxposed.api)
    compileOnly(libs.xposed.legacy.api)
}
