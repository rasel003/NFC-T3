import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.android.build.gradle.api.ApplicationVariant
import com.android.build.gradle.api.BaseVariantOutput
import com.android.build.gradle.internal.api.BaseVariantOutputImpl

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.sajjad.nfct3"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sajjad.nfct3"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
        dataBinding = true
        viewBinding = true
    }

    applicationVariants.all(ApplicationVariantAction())
}
class ApplicationVariantAction : Action<ApplicationVariant> {
    override fun execute(variant: ApplicationVariant) {
        val fileName = createFileName(variant)
        variant.outputs.all(VariantOutputAction(fileName))
    }

    private fun createFileName(variant: ApplicationVariant): String {
        return "C19 T3" +
                "_${variant.name}" +
//                "_verCode${Android.versionCode}" +
                "_${getDateTimeFormat()}.apk"
    }

    private fun getDateTimeFormat(): String {
        val simpleDateFormat = SimpleDateFormat("dd-MMM-yy-hh-mm-a", Locale.US)
        return simpleDateFormat.format(Date())
    }

    class VariantOutputAction(
        private val fileName: String
    ) : Action<BaseVariantOutput> {
        override fun execute(output: BaseVariantOutput) {
            if (output is BaseVariantOutputImpl) {
                output.outputFileName = fileName
            }
        }
    }
}
dependencies {

    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.6.1")


    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}