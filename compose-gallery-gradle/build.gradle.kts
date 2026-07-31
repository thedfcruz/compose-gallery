plugins {
    `kotlin-dsl`
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