import org.jetbrains.kotlin.gradle.dsl.JvmTarget
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") // Kotlin 2.0+ requirement
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.07.00"))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.07.00"))
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material3:material3")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.mozilla:rhino:1.8.0")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("io.github.Rosemoe.sora-editor:editor:0.23.6")
    implementation("io.github.Rosemoe.sora-editor:language-textmate:0.23.6")

    testImplementation("junit:junit:4.13.2")
}

android {
    namespace = "com.sk8erdudex.texticle"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }
    compileSdk = 36
    defaultConfig {
        applicationId = "com.sk8erdudex.texticle"
        versionCode = 1
        versionName = "0.1.0"
        targetSdk = 36
        minSdk = 26
    }
}
