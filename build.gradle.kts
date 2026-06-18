plugins {
    kotlin("jvm") version "2.2.21"
    application
}

group = "com.odontologia"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.javalin:javalin-bundle:7.2.2")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.1.0")
}

kotlin {
    jvmToolchain(17)
    sourceSets {
        main {
            kotlin.srcDir("src")
        }
    }
}

application {
    mainClass.set("AppKt")
}
