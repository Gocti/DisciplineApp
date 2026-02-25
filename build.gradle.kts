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

tasks.register<Exec>("createMsi") {
    group = "distribution"
    description = "Creates MSI installer using jpackage"
    
    val javaHome = System.getProperty("java.home")
    
    commandLine(
        "${javaHome}\\bin\\jpackage",
        "--type", "msi",
        "--name", "DisciplineApp",
        "--version", version.toString(),
        "--input", "${buildDir}/libs",
        "--main-jar", "DisciplineApp-${version}-all.jar",
        "--main-class", "app.MainApp",
        "--win-menu",
        "--win-menu-group", "DisciplineApp",
        "--win-shortcut",
        "--win-dir-chooser",
        "--win-per-user-install",
        "--app-version", version.toString(),
        "--vendor", "DisciplineApp",
        "--description", "Application for blocking programs and task planning",
        "--output", "${buildDir}/installers"
    )
    
    dependsOn("fatJar")
}

tasks.register<Exec>("createExe") {
    group = "distribution"
    description = "Creates EXE installer using jpackage"
    
    val javaHome = System.getProperty("java.home")
    
    commandLine(
        "${javaHome}\\bin\\jpackage",
        "--type", "exe",
        "--name", "DisciplineApp",
        "--version", version.toString(),
        "--input", "${buildDir}/libs",
        "--main-jar", "DisciplineApp-${version}-all.jar",
        "--main-class", "app.MainApp",
        "--win-menu",
        "--win-menu-group", "DisciplineApp",
        "--win-shortcut",
        "--win-dir-chooser",
        "--win-per-user-install",
        "--app-version", version.toString(),
        "--vendor", "DisciplineApp",
        "--description", "Application for blocking programs and task planning",
        "--output", "${buildDir}/installers"
    )
    
    dependsOn("fatJar")
}

tasks.register<Jar>("fatJar") {
    archiveBaseName.set("DisciplineApp")
    archiveVersion.set(version.toString())
    archiveClassifier.set("all")

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






