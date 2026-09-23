import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

// CR-9: explicit public API on :kernel and every *:applicazione module.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

configure<KotlinProjectExtension> {
    explicitApi()
}
