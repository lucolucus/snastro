plugins {
    id("org.jetbrains.kotlin.jvm")
    id("java-test-fixtures")
    id("io.gitlab.arturbosch.detekt")
}

configureKotlinJvm()
configureDetekt()
configureTesting()
