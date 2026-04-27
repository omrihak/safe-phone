import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("app.cash.paparazzi") version "1.3.5"
}

fun org.gradle.api.Project.blockLandingUrlForBuildType(debug: Boolean): String {
    if (debug) {
        val debugOnly = (findProperty("safephone.blockLandingUrl.debug") as String?)?.trim().orEmpty()
        if (debugOnly.isNotEmpty()) return debugOnly
    }
    return (findProperty("safephone.blockLandingUrl") as String?)?.trim().orEmpty()
        .ifEmpty { "https://safephone-focus-landing.pages.dev/" }
}

fun String.toBuildConfigStringLiteral(): String =
    '"' + replace("\\", "\\\\").replace("\"", "\\\"") + '"'

/** Owner/repo for GitHub "new issue" links (vibe coding). Explicit gradle props override parsing internalUpdateBaseUrl. */
fun org.gradle.api.Project.githubIssuesOwnerRepo(): Pair<String, String> {
    val explicitOwner = (findProperty("safephone.githubIssueOwner") as String?)?.trim().orEmpty()
    val explicitRepo = (findProperty("safephone.githubIssueRepo") as String?)?.trim().orEmpty()
    if (explicitOwner.isNotEmpty() && explicitRepo.isNotEmpty()) {
        return explicitOwner to explicitRepo
    }
    val base = (findProperty("safephone.internalUpdateBaseUrl") as String?)?.trim().orEmpty()
    val m = Regex("""github\.com/([^/]+)/([^/?.#]+)""").find(base)
    if (m != null) {
        val owner = m.groupValues[1]
        val repo = m.groupValues[2].removeSuffix(".git")
        return owner to repo
    }
    return "" to ""
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

/**
 * Local-only secrets (PATs, default gist ids, etc.). Mirrors the pattern used for keystore.properties
 * so secrets stay in [local.properties] (gitignored) and never enter source control. Gradle's
 * `findProperty` (which reads `gradle.properties` and `-P` flags) is consulted as a fallback so CI
 * can still inject these values via `-Psafephone.cloudSyncDefaultGitHubToken=...`.
 */
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

fun org.gradle.api.Project.localOrProperty(name: String): String {
    val fromLocal = localProperties.getProperty(name)?.trim().orEmpty()
    if (fromLocal.isNotEmpty()) return fromLocal
    return (findProperty(name) as String?)?.trim().orEmpty()
}

/**
 * Resolves a build-time secret in this priority order:
 * 1. Environment variable [envName] (preferred for CI — secrets stay out of the gradle command line),
 * 2. `local.properties` value of [propertyName],
 * 3. Gradle project property [propertyName] (so `-P` overrides still work for ad-hoc builds).
 *
 * Returns an empty string when none of the sources are set, so the BuildConfig literal is `""`.
 */
fun org.gradle.api.Project.envOrLocalOrProperty(envName: String, propertyName: String): String {
    val fromEnv = System.getenv(envName)?.trim().orEmpty()
    if (fromEnv.isNotEmpty()) return fromEnv
    return localOrProperty(propertyName)
}

val appVersionCode = (findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 1
val appVersionName = (findProperty("appVersionName") as String?)?.trim().orEmpty().ifEmpty { "0.1.0" }

android {
    namespace = "com.safephone"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.safephone.focus"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments += mapOf("clearPackageData" to "true")
        val (ghIssuesOwner, ghIssuesRepo) = githubIssuesOwnerRepo()
        buildConfigField("String", "GITHUB_ISSUES_OWNER", ghIssuesOwner.toBuildConfigStringLiteral())
        buildConfigField("String", "GITHUB_ISSUES_REPO", ghIssuesRepo.toBuildConfigStringLiteral())

        val cloudSyncToken = envOrLocalOrProperty(
            envName = "SAFEPHONE_CLOUD_SYNC_DEFAULT_GITHUB_TOKEN",
            propertyName = "safephone.cloudSyncDefaultGitHubToken",
        )
        val cloudSyncGistId = envOrLocalOrProperty(
            envName = "SAFEPHONE_CLOUD_SYNC_DEFAULT_GIST_ID",
            propertyName = "safephone.cloudSyncDefaultGistId",
        )
        buildConfigField(
            "String",
            "CLOUD_SYNC_DEFAULT_GITHUB_TOKEN",
            cloudSyncToken.toBuildConfigStringLiteral(),
        )
        buildConfigField(
            "String",
            "CLOUD_SYNC_DEFAULT_GIST_ID",
            cloudSyncGistId.toBuildConfigStringLiteral(),
        )
    }
    testOptions {
        @Suppress("DEPRECATION")
        execution = "ANDROID_TEST_ORCHESTRATOR"
        unitTests.isIncludeAndroidResources = true
    }

    flavorDimensions += "channel"
    productFlavors {
        create("standard") {
            dimension = "channel"
            isDefault = true
            buildConfigField("boolean", "ENABLE_INTERNAL_AUTO_UPDATE", "false")
            buildConfigField("String", "INTERNAL_UPDATE_BASE_URL", "\"\"")
            buildConfigField("String", "INTERNAL_UPDATE_TRACK_REF", "\"\"")
        }
        create("internal") {
            dimension = "channel"
            val base = (findProperty("safephone.internalUpdateBaseUrl") as String?)?.trim().orEmpty()
            val track = (findProperty("safephone.internalUpdateTrackRef") as String?)?.trim().orEmpty()
            buildConfigField("boolean", "ENABLE_INTERNAL_AUTO_UPDATE", "true")
            buildConfigField("String", "INTERNAL_UPDATE_BASE_URL", base.toBuildConfigStringLiteral())
            buildConfigField("String", "INTERNAL_UPDATE_TRACK_REF", track.toBuildConfigStringLiteral())
        }
    }

    signingConfigs {
        create("upload") {
            val ksFromEnv = System.getenv("KEYSTORE_FILE")?.trim().orEmpty()
            val storeFilePath = when {
                ksFromEnv.isNotEmpty() -> ksFromEnv
                else -> keystoreProperties.getProperty("storeFile")?.trim().orEmpty()
            }
            val storePwd = System.getenv("KEYSTORE_PASSWORD")?.trim().orEmpty()
                .ifEmpty { keystoreProperties.getProperty("storePassword")?.trim().orEmpty() }
            val alias = System.getenv("KEY_ALIAS")?.trim().orEmpty()
                .ifEmpty { keystoreProperties.getProperty("keyAlias")?.trim().orEmpty() }
            val keyPwd = System.getenv("KEY_PASSWORD")?.trim().orEmpty()
                .ifEmpty { keystoreProperties.getProperty("keyPassword")?.trim().orEmpty() }
            if (storeFilePath.isNotEmpty() && storePwd.isNotEmpty() && alias.isNotEmpty() && keyPwd.isNotEmpty()) {
                val f = if (File(storeFilePath).isAbsolute) {
                    file(storeFilePath)
                } else {
                    rootProject.file(storeFilePath)
                }
                if (f.isFile) {
                    storeFile = f
                    storePassword = storePwd
                    keyAlias = alias
                    keyPassword = keyPwd
                }
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField(
                "String",
                "BLOCK_LANDING_URL",
                project.blockLandingUrlForBuildType(debug = true).toBuildConfigStringLiteral(),
            )
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            buildConfigField(
                "String",
                "BLOCK_LANDING_URL",
                project.blockLandingUrlForBuildType(debug = false).toBuildConfigStringLiteral(),
            )
            val upload = signingConfigs.getByName("upload")
            signingConfig = if (upload.storeFile != null && upload.storeFile!!.exists()) {
                upload
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Robolectric + Paparazzi can hit ZipFileSystemAlreadyExists when JVM forks run test classes in parallel.
tasks.withType<Test>().configureEach {
    maxParallelForks = 1
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    val room = "2.6.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("com.squareup.moshi:moshi:1.15.1")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")

    implementation("androidx.work:work-runtime-ktx:2.9.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestUtil("androidx.test:orchestrator:1.5.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
