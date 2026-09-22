# DisciplineApp

A desktop productivity application for managing tasks and maintaining focus.

DisciplineApp helps you organize your daily tasks and work with a calendar-based schedule. The project is designed to evolve into a cross-platform productivity application.

## Features

* Task management
* Calendar-based task planning
* Task priorities
* Task completion status
* Persistent task storage
* Light, dark, and system themes
* Font and interface scaling
* Accent color customization
* System tray integration
* Automatic update checking
* Windows application packaging with an embedded Java runtime

## Technology

* Java 25
* Gradle
* Swing
* FlatLaf
* Jackson
* JUnit
* Inno Setup

## Project Structure

```text
DisciplineApp/
├── src/
│   ├── main/
│   │   └── java/
│   │       ├── app/
│   │       ├── model/
│   │       ├── repository/
│   │       ├── ui/
│   │       └── updater/
│   └── test/
│       └── java/
├── installer/
│   └── discipline-app.iss
├── build.gradle.kts
├── build-innosetup.bat
├── build-jpackage.ps1
├── build-zip.bat
└── run.bat
```

## Building

The project requires **JDK 25**.

Run the tests:

```bash
./gradlew clean test
```

On Windows:

```bat
gradlew.bat clean test
```

## Running

For development, the application can be launched using the provided script:

```bat
run.bat
```

Alternatively, build the Fat JAR with Gradle and run it using Java 25.

## Building the Windows Installer

The recommended release build is:

```bat
build-innosetup.bat
```

The build process:

```text
Gradle
  ↓
Fat JAR
  ↓
jpackage
  ↓
Windows application image
  ↓
Inno Setup
  ↓
Windows installer
```

The resulting installer is created at:

```text
installer\Output\DisciplineApp-Setup-1.0.0.exe
```

The application package contains its own Java runtime, so the end user does not need to install Java separately.

## Data Storage

Task data is stored in:

```text
%USERPROFILE%\.discipline-app\tasks.json
```

The application loads the stored tasks when it starts and saves changes automatically.

## Development

The project currently uses a Swing-based user interface.

The application is being structured so that the domain and data layers remain independent of the UI. This allows the project to be extended with other user interfaces in the future, including a planned Compose Multiplatform interface.

## License

This project is currently under development.
