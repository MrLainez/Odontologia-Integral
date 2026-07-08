plugins {
    kotlin("jvm") version "2.2.21"
    application
}

group = "com.odontologia"
version = "1.0-SNAPSHOT"

dependencies {
    implementation("io.javalin:javalin-bundle:5.6.3")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.1.0")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    sourceSets {
        main {
            kotlin.srcDir("src")
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

application {
    mainClass.set("AppKt")
}

distributions {
    main {
        contents {
            from("public") {
                into("public")
            }
            from("database") {
                into("database")
            }
            from("config.example.properties")
            from("config.production.example.properties")
            from("README.md")
            from("DEFAULT_CREDENTIALS.md")
        }
    }
}
