// Root build file. Plugins declared here with `apply false` so each module
// can opt in. google-services and crashlytics are applied conditionally in
// :app so a fresh clone WITHOUT google-services.json still builds.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.crashlytics) apply false
}
