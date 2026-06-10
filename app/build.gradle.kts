import java.util.Properties
import java.time.LocalDateTime
import java.time.ZoneId

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.gradle.play.publisher)
    alias(libs.plugins.google.services)
}

fun readConfig(name: String, defaultValue: String): String {
    val gradleValue = providers.gradleProperty(name).orNull?.trim().orEmpty()
    if (gradleValue.isNotBlank()) {
        return gradleValue
    }
    val envValue = System.getenv(name)?.trim().orEmpty()
    if (envValue.isNotBlank()) {
        return envValue
    }
    return defaultValue
}

fun readIntConfig(name: String, defaultValue: Int): Int {
    val value = readConfig(name, defaultValue.toString())
    return value.toIntOrNull() ?: defaultValue
}

fun defaultVersionCode(): Int {
    val now = LocalDateTime.now(ZoneId.of("Europe/Bratislava"))
    val yearPart = now.year % 100
    return yearPart * 10_000_000 + now.dayOfYear * 10_000 + now.hour * 100 + now.minute
}

fun quoted(value: String): String = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

android {
    namespace = "sk.uss.isac.chat.mobile"
    compileSdk = 35

    defaultConfig {
        minSdk = 28
        targetSdk = 35
        versionCode = readIntConfig("ISAC_MOBILE_VERSION_CODE", defaultVersionCode())
        versionName = readConfig("ISAC_MOBILE_VERSION_NAME", "0.1.10")

        buildConfigField("String", "X_API_TYPE", quoted(readConfig("ISAC_X_API_TYPE", "private")))
        buildConfigField("String", "OIDC_SCOPE", quoted(readConfig("ISAC_OIDC_SCOPE", "openid profile email")))
        buildConfigField("boolean", "ALLOW_MANUAL_BEARER_BOOTSTRAP", "false")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    flavorDimensions += "environment"

    productFlavors {
        create("useitacDev") {
            dimension = "environment"
            applicationId = "sk.uss.isac.chat.mobile"
            versionNameSuffix = "-useitac-dev"
            manifestPlaceholders["appAuthScheme"] = "isacchat"
            resValue("string", "app_name", "UseIT Chat")
            buildConfigField("String", "ENVIRONMENT_LABEL", quoted("Developer"))
            buildConfigField("String", "HOSTED_PRESET_LABEL", quoted("UseIT Developer"))
            buildConfigField("String", "BRAND_NAME", quoted("UseIT"))
            buildConfigField("String", "APP_DISPLAY_TITLE", quoted("UseIT Chat"))
            buildConfigField("String", "APP_SUPPORTING_LABEL", quoted("Mobilný klient pre chat funkcionalitu produktu UseIT"))
            buildConfigField("String", "APP_FOOTER_LABEL", quoted("UseIT Chat"))
            buildConfigField("String", "CHAT_BASE_URL", quoted(readConfig("USEITAC_DEV_CHAT_BASE_URL", "https://useitac.onesoft.sk/chat-backend/")))
            buildConfigField("String", "CHAT_WS_URL", quoted(readConfig("USEITAC_DEV_CHAT_WS_URL", "wss://useitac.onesoft.sk/chat-backend/ws/chat")))
            buildConfigField("String", "PROFILE_API_URL", quoted(readConfig("USEITAC_DEV_PROFILE_API_URL", "https://useitac.onesoft.sk/backend")))
            buildConfigField("String", "OIDC_AUTH_URL", quoted(readConfig("USEITAC_DEV_OIDC_AUTH_URL", "https://useitac.onesoft.sk/auth/realms/ISAC-Test/protocol/openid-connect/auth")))
            buildConfigField("String", "OIDC_TOKEN_URL", quoted(readConfig("USEITAC_DEV_OIDC_TOKEN_URL", "https://useitac.onesoft.sk/auth/realms/ISAC-Test/protocol/openid-connect/token")))
            buildConfigField("String", "OIDC_END_SESSION_URL", quoted(readConfig("USEITAC_DEV_OIDC_END_SESSION_URL", "https://useitac.onesoft.sk/auth/realms/ISAC-Test/protocol/openid-connect/logout")))
            buildConfigField("String", "OIDC_CLIENT_ID", quoted(readConfig("USEITAC_DEV_OIDC_CLIENT_ID", "isac-chat-mobile")))
            buildConfigField("String", "OIDC_REDIRECT_URI", quoted(readConfig("USEITAC_DEV_OIDC_REDIRECT_URI", "isacchat://auth/callback")))
            buildConfigField("String", "APP_AUTH_SCHEME", quoted("isacchat"))
        }

        create("usskTest") {
            dimension = "environment"
            applicationId = "sk.uss.isac.chat.mobile.ussk.test"
            versionNameSuffix = "-ussk-test"
            manifestPlaceholders["appAuthScheme"] = "isacchat-ussk-test"
            resValue("string", "app_name", "ISAC Chat USSK Test")
            buildConfigField("String", "ENVIRONMENT_LABEL", quoted("USSK Test"))
            buildConfigField("String", "HOSTED_PRESET_LABEL", quoted("USSK Test"))
            buildConfigField("String", "BRAND_NAME", quoted("USS Steel Kosice"))
            buildConfigField("String", "APP_DISPLAY_TITLE", quoted("ISAC Chat"))
            buildConfigField("String", "APP_SUPPORTING_LABEL", quoted("Firemný mobilný klient pre chat, schvaľovania a skupiny"))
            buildConfigField("String", "APP_FOOTER_LABEL", quoted("ISAC Chat pre USS Steel Kosice"))
            buildConfigField("String", "CHAT_BASE_URL", quoted(readConfig("USSK_TEST_CHAT_BASE_URL", "https://isac-tst.kbs.sk.uss.com/chat-backend/")))
            buildConfigField("String", "CHAT_WS_URL", quoted(readConfig("USSK_TEST_CHAT_WS_URL", "wss://isac-tst.kbs.sk.uss.com/chat-backend/ws/chat")))
            buildConfigField("String", "PROFILE_API_URL", quoted(readConfig("USSK_TEST_PROFILE_API_URL", "https://isac-tst.kbs.sk.uss.com/backend")))
            buildConfigField("String", "OIDC_AUTH_URL", quoted(readConfig("USSK_TEST_OIDC_AUTH_URL", "https://isac-tst.kbs.sk.uss.com/auth/realms/ISAC-Test/protocol/openid-connect/auth")))
            buildConfigField("String", "OIDC_TOKEN_URL", quoted(readConfig("USSK_TEST_OIDC_TOKEN_URL", "https://isac-tst.kbs.sk.uss.com/auth/realms/ISAC-Test/protocol/openid-connect/token")))
            buildConfigField("String", "OIDC_END_SESSION_URL", quoted(readConfig("USSK_TEST_OIDC_END_SESSION_URL", "https://isac-tst.kbs.sk.uss.com/auth/realms/ISAC-Test/protocol/openid-connect/logout")))
            buildConfigField("String", "OIDC_CLIENT_ID", quoted(readConfig("USSK_TEST_OIDC_CLIENT_ID", "isac-chat-mobile-ussk-test")))
            buildConfigField("String", "OIDC_REDIRECT_URI", quoted(readConfig("USSK_TEST_OIDC_REDIRECT_URI", "isacchat-ussk-test://auth/callback")))
            buildConfigField("String", "APP_AUTH_SCHEME", quoted("isacchat-ussk-test"))
        }

        create("usskProd") {
            dimension = "environment"
            applicationId = "sk.uss.isac.chat.mobile.ussk"
            versionNameSuffix = "-ussk-prod"
            manifestPlaceholders["appAuthScheme"] = "isacchat-ussk"
            resValue("string", "app_name", "ISAC Chat USSK")
            buildConfigField("String", "ENVIRONMENT_LABEL", quoted("USSK Produkcia"))
            buildConfigField("String", "HOSTED_PRESET_LABEL", quoted("USSK Produkcia"))
            buildConfigField("String", "BRAND_NAME", quoted("USS Steel Kosice"))
            buildConfigField("String", "APP_DISPLAY_TITLE", quoted("ISAC Chat"))
            buildConfigField("String", "APP_SUPPORTING_LABEL", quoted("Firemný mobilný klient pre chat, schvaľovania a skupiny"))
            buildConfigField("String", "APP_FOOTER_LABEL", quoted("ISAC Chat pre USS Steel Kosice"))
            buildConfigField("String", "CHAT_BASE_URL", quoted(readConfig("USSK_PROD_CHAT_BASE_URL", "")))
            buildConfigField("String", "CHAT_WS_URL", quoted(readConfig("USSK_PROD_CHAT_WS_URL", "")))
            buildConfigField("String", "PROFILE_API_URL", quoted(readConfig("USSK_PROD_PROFILE_API_URL", "")))
            buildConfigField("String", "OIDC_AUTH_URL", quoted(readConfig("USSK_PROD_OIDC_AUTH_URL", "")))
            buildConfigField("String", "OIDC_TOKEN_URL", quoted(readConfig("USSK_PROD_OIDC_TOKEN_URL", "")))
            buildConfigField("String", "OIDC_END_SESSION_URL", quoted(readConfig("USSK_PROD_OIDC_END_SESSION_URL", "")))
            buildConfigField("String", "OIDC_CLIENT_ID", quoted(readConfig("USSK_PROD_OIDC_CLIENT_ID", "isac-chat-mobile-ussk")))
            buildConfigField("String", "OIDC_REDIRECT_URI", quoted(readConfig("USSK_PROD_OIDC_REDIRECT_URI", "isacchat-ussk://auth/callback")))
            buildConfigField("String", "APP_AUTH_SCHEME", quoted("isacchat-ussk"))
        }
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "ALLOW_MANUAL_BEARER_BOOTSTRAP", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
            buildConfigField("boolean", "ALLOW_MANUAL_BEARER_BOOTSTRAP", "false")
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        disable += setOf("AndroidGradlePluginVersion", "GradleDependency")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.security.crypto)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    implementation(libs.gson.core)
    implementation(libs.firebase.messaging)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}

play {
    defaultToAppBundles.set(true)
}
