plugins {
    id("snastro.kotlin-jvm")
    alias(libs.plugins.sqldelight)
}

sqldelight {
    databases {
        create("SnastroDatabase") {
            packageName.set("snastro.persistenza")
        }
    }
}

dependencies {
    implementation(libs.sqlite.jdbc)
}
