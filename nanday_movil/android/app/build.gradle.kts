import java.io.FileInputStream
import java.util.Properties

// Clave de firma propia (android/key.properties). Si el archivo no existe, el APK de release
// se firma con la clave de depuración, así igual se puede compilar e instalar.
val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("key.properties")
val tieneClavePropia = keystorePropertiesFile.exists()
if (tieneClavePropia) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

plugins {
    id("com.android.application")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "com.example.nanday_movil"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Lo pide flutter_local_notifications (alertas de stock mínimo)
        isCoreLibraryDesugaringEnabled = true
    }

    defaultConfig {
        // Identificador único de la app en Android (no debe empezar con com.example)
        applicationId = "py.com.nanday.siaa"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        // Uses the version code from pubspec.yaml. When using split APKs, 1000 * ABI_VERSION
        // is added automatically by Flutter. (https://developer.android.com/studio/build/configure-apk-splits#configure-APK-versions)
        // You can force using the value of versionCode by specifying the `-P force-version-code-ignoring-abi=true`
        // flag during build.
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    signingConfigs {
        if (tieneClavePropia) {
            create("release") {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (tieneClavePropia) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}

dependencies {
    // Lo pide flutter_local_notifications (alertas de stock mínimo)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
}
