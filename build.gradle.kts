plugins {
    java
    application
}

group = "discipline"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.formdev:flatlaf:3.7")
    implementation("com.formdev:flatlaf-extras:3.7")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.21.0")
}

application {
    mainClass.set("app.MainApp")
}

tasks.register<Jar>("fatJar") {
    archiveBaseName.set("DisciplineApp")
    archiveVersion.set("1.0.0")

    manifest {
        attributes["Main-Class"] = "app.MainApp"
    }

    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.exists() }.map {
            if (it.isDirectory) it else zipTree(it)
        }
    })

    // 🔑 обработка дубликатов
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}






