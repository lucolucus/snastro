plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("java-test-fixtures")
    id("io.gitlab.arturbosch.detekt")
}

configureKotlinJvm()
configureDetekt()
configureTesting()
