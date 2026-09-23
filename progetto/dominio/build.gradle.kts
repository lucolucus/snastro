plugins {
    id("snastro.kotlin-jvm")
}

dependencies {
    api(project(":kernel"))

    testImplementation(testFixtures(project(":kernel")))
}
