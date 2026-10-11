plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    // Версия AGP должна совпадать с agp в gradle/libs.versions.toml.
    implementation("com.android.tools.build:gradle:8.13.2")
    implementation("org.ow2.asm:asm:9.7.1")
}
