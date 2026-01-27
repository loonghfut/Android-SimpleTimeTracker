import com.example.util.simpletimetracker.Base
import com.example.util.simpletimetracker.applyAndroidLibrary

plugins {
    alias(libs.plugins.gradleLibrary)
    alias(libs.plugins.kotlin)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

applyAndroidLibrary()

android {
    namespace = "${Base.namespace}.feature_main"
}

dependencies {
    implementation(project(":core"))
    implementation(project(":feature_running_records"))
    implementation(project(":feature_records"))
    implementation(project(":feature_statistics"))
    implementation(project(":feature_settings"))
    implementation(project(":feature_goals"))
    implementation(libs.androidx.swipeRefreshLayout)
    implementation(libs.google.dagger)
    ksp(libs.kapt.dagger)
}
