plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    id("org.sonarqube") version "5.1.0.4882"
}

sonar {
    properties {
        property("sonar.projectKey", "GitHardUPCFrontend")
        property("sonar.projectName", "GitHardUPC Frontend")
        property("sonar.host.url", "http://nattech.fib.upc.edu:40380")

        // Rutas específicas de Android
        property("sonar.sources", "app/src/main/java")
        property("sonar.tests", "app/src/test/java, app/src/androidTest/java")
        property("sonar.java.binaries", "app/build/tmp/kotlin-classes/debug")

        // Reporte de JaCoCo
        property("sonar.coverage.jacoco.xmlReportPaths", "app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml")

        property("sonar.gradle.skipCompile", "true")
    }
}