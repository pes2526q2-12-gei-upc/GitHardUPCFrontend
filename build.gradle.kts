// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    id("org.sonarqube") version "4.4.1.3373"
}

sonar {
    properties {
        property("sonar.projectKey", "GitHardUPCFrontend") // Consistente con el backend
        property("sonar.projectName", "GitHardUPC Frontend")
        property("sonar.host.url", "http://nattech.fib.upc.edu:40380") // Nuestra URL de Virtech

        // Rutas específicas de Android
        property("sonar.sources", "app/src/main/java")
        property("sonar.tests", "app/src/test/java, app/src/androidTest/java")
        property("sonar.java.binaries", "app/build/tmp/kotlin-classes/debug")

        // Le decimos a Sonar dónde encontrará el XML de cobertura generado por JaCoCo
        property("sonar.coverage.jacoco.xmlReportPaths", "app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml")
    }