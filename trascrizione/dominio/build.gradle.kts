plugins {
    id("snastro.kotlin-jvm")
}

dependencies {
    api(project(":kernel"))
    testImplementation(testFixtures(project(":kernel")))
    // unIncontroDi: the fixture Trascritto's default Incontro (ADR 0033 §4.1).
    testFixturesImplementation(testFixtures(project(":kernel")))
}
