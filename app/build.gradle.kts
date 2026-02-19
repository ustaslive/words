import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ustas.words"
    compileSdk = 35

    val versionFile = rootProject.file("version.txt")
    val appVersion = versionFile.readText().trim()
    if (appVersion.isBlank()) {
        error("version.txt is empty")
    }

    val buildTimePattern = "yyyy-MM-dd HH:mm:ss 'UTC'"
    val buildTimeUtc = ZonedDateTime.now(ZoneOffset.UTC)
        .format(DateTimeFormatter.ofPattern(buildTimePattern))

    defaultConfig {
        applicationId = "com.familiarapps.words"
        minSdk = 24
        targetSdk = 35
        versionCode = 8
        versionName = appVersion
        buildConfigField("String", "BUILD_TIME_UTC", "\"$buildTimeUtc\"")
    }

    signingConfigs {
        create("release") {
            keyAlias = System.getenv("ANDROID_KEY_ALIAS") ?: "words-key"
            keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            storePassword = System.getenv("ANDROID_STORE_PASSWORD")

            storeFile = rootProject.file("secrets/familiar-apps.jks")
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.7"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.02.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    testImplementation("junit:junit:4.13.2")

    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.android.material:material:1.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

tasks.withType<Test> {
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
    finalizedBy("logTestReport")
}

tasks.register("logTestReport") {
    doLast {
        val reportPath = file("${layout.buildDirectory.get()}/reports/tests/testDebugUnitTest/index.html")
        println()
        println("--------------------------------------------------")
        println("Test Report: file://$reportPath")
        println("To force re-run: ./gradlew test --rerun-tasks")
        println("--------------------------------------------------")
    }
}
