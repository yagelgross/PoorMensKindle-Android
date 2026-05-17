plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
buildscript {
    dependencies {
        classpath("com.mikepenz.aboutlibraries.plugin:aboutlibraries-plugin:11.2.3")
    }
}
