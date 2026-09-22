$ErrorActionPreference = "Stop"

$JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot"
$JPACKAGE = "$JAVA_HOME\bin\jpackage.exe"

$PROJECT_DIR = "C:\Users\seryo\DisciplineApp"
$INPUT_DIR = "$PROJECT_DIR\build\libs"
$OUTPUT_DIR = "$PROJECT_DIR\build\installers"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "DisciplineApp - jpackage build" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "Java Home: $JAVA_HOME"
Write-Host "Input:     $INPUT_DIR"
Write-Host "Output:    $OUTPUT_DIR"
Write-Host ""

# Проверка JDK
if (!(Test-Path "$JAVA_HOME\bin\java.exe")) {
    Write-Error "Java not found: $JAVA_HOME\bin\java.exe"
    exit 1
}

if (!(Test-Path "$JAVA_HOME\bin\javac.exe")) {
    Write-Error "javac not found: $JAVA_HOME\bin\javac.exe"
    exit 1
}

if (!(Test-Path $JPACKAGE)) {
    Write-Error "jpackage not found: $JPACKAGE"
    exit 1
}

Write-Host "Java version:"
& "$JAVA_HOME\bin\java.exe" -version

Write-Host ""
Write-Host "jpackage version:"
& $JPACKAGE --version

Write-Host ""

# Проверка JAR
$JAR = "$INPUT_DIR\DisciplineApp-1.0.0-all.jar"

if (!(Test-Path $JAR)) {
    Write-Error "JAR file not found: $JAR"
    exit 1
}

Write-Host "JAR found:"
Write-Host $JAR
Write-Host ""

# Очистка старого результата
if (Test-Path $OUTPUT_DIR) {
    Write-Host "Removing previous output..."
    Remove-Item $OUTPUT_DIR -Recurse -Force
}

New-Item -ItemType Directory -Path $OUTPUT_DIR | Out-Null

# Аргументы jpackage
$args = @(
    "--type", "exe",
    "--name", "DisciplineApp",
    "--app-version", "1.0.0",
    "--input", $INPUT_DIR,
    "--main-jar", "DisciplineApp-1.0.0-all.jar",
    "--main-class", "app.MainApp",
    "--win-menu",
    "--win-shortcut",
    "--vendor", "DisciplineApp",
    "--description", "Application for blocking programs and task planning",
    "--dest", $OUTPUT_DIR,
    "--verbose"
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Running jpackage..." -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "Arguments:"
Write-Host ($args -join " ")
Write-Host ""

# Запуск jpackage
& $JPACKAGE @args

$EXIT_CODE = $LASTEXITCODE

Write-Host ""
Write-Host "Exit code: $EXIT_CODE"

if ($EXIT_CODE -eq 0) {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "BUILD SUCCESSFUL!" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    Write-Host ""

    Write-Host "Output files:"
    Get-ChildItem $OUTPUT_DIR -Recurse |
            Select-Object FullName
}
else {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Red
    Write-Host "BUILD FAILED!" -ForegroundColor Red
    Write-Host "========================================" -ForegroundColor Red

    exit $EXIT_CODE
}
