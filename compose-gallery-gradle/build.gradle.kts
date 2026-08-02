plugins {
    `kotlin-dsl`
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}

gradlePlugin {
    plugins {
        create("gallery") {
            id = "com.dfcruz.compose.gallery"
            displayName = "Compose Component Gallery"
            description = "Generates a Gallery of Components"
            implementationClass = "com.dfcruz.compose.gallery.gradle.ComposePreviewGalleryPlugin"
        }
    }
}