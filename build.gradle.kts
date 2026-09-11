plugins {
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0" apply false
    alias(libs.plugins.android).apply(false)
    alias(libs.plugins.detekt).apply(false)
}
