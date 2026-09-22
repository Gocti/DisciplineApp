plugins {
    java
    application
}

group = "discipline"
version = providers.gradleProperty("appVersion")
    .orElse("1.0.0")
    .get()

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
        vendor.set(JvmVendorSpec.ADOPTIUM)
    }
}

repositories {
    mavenCentral()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf(
        "-Xlint:deprecation",
        "-Xlint:unchecked"
    ))
}

// Dependency versions
val flatlafVersion = "3.7.2"
val jacksonVersion = "3.2.2"
val jetbrainsAnnotationsVersion = "26.1.0"

dependencies {
    implementation("com.formdev:flatlaf:$flatlafVersion")
    implementation("tools.jackson.core:jackson-databind:$jacksonVersion")
    implementation("org.jetbrains:annotations:$jetbrainsAnnotationsVersion")
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("app.MainApp")
    applicationDefaultJvmArgs = listOf(
        "--enable-native-access=ALL-UNNAMED"
    )
}

/** Получаем путь к jpackage из toolchain или JAVA_HOME (platform-independent) */
fun getJpackagePath(): String {
    val envJavaHome = System.getenv("JAVA_HOME")
    if (envJavaHome != null) {
        return "$envJavaHome/bin/jpackage"
    }
    // Используем путь из Java toolchain
    val toolchain = javaToolchains.launcherFor(java.toolchain).get()
    val javaHome = toolchain.metadata.installationPath.asFile.absolutePath
    return "$javaHome/bin/jpackage"
}

/** Общий конфиг для jpackage-задач */
fun Exec.configureJpackage(type: String) {
    group = "distribution"
    description = "Creates $type installer using jpackage"

    val jpackagePath = getJpackagePath()

    commandLine(
        jpackagePath,
        "--type", type,
        "--name", "DisciplineApp",
        "--version", version.toString(),
        "--input", "${layout.buildDirectory.get().asFile}/libs",
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
        "--output", "${layout.buildDirectory.get().asFile}/installers"
    )

    isIgnoreExitValue = false
    standardOutput = System.out
    errorOutput = System.err

    dependsOn("fatJar")
}

tasks.register<Exec>("createMsi") {
    group = "distribution"
    description = "Creates MSI installer using jpackage"
    configureJpackage("msi")
}

tasks.register<Exec>("createExe") {
    group = "distribution"
    description = "Creates EXE installer using jpackage"
    configureJpackage("exe")
}

tasks.register<Jar>("fatJar") {
    group = "distribution"
    description = "Creates a fat JAR with all dependencies included"
    archiveBaseName.set("DisciplineApp")
    archiveVersion.set(version.toString())
    archiveClassifier.set("all")

    manifest {
        attributes["Main-Class"] = "app.MainApp"
        attributes["Implementation-Version"] = version.toString()
    }

    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.exists() }.map {
            if (it.isDirectory) it else zipTree(it)
        }
    })

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.test {
    useJUnitPlatform()
}
