# Скрипт установки JDK 25 для Windows
$ErrorActionPreference = "Stop"

Write-Host "=== Установка JDK 25 для сборки DisciplineApp ===" -ForegroundColor Cyan

# Корневая директория Eclipse Adoptium
$JDK_ROOT = "C:\Program Files\Eclipse Adoptium"

# Eclipse Temurin JDK 25 x64
$DOWNLOAD_URL = "https://api.adoptium.net/v3/binary/latest/25/ga/windows/x64/jdk/hotspot/normal/eclipse"

$TEMP_ZIP = "$env:TEMP\jdk25.zip"
$TEMP_EXTRACT = "$env:TEMP\jdk25-extract"

# ============================================================
# Поиск уже установленного JDK 25
# ============================================================

$existingJdk = Get-ChildItem $JDK_ROOT -Directory -ErrorAction SilentlyContinue |
        Where-Object {
            Test-Path "$($_.FullName)\bin\java.exe" -and
            Test-Path "$($_.FullName)\bin\javac.exe" -and
            Test-Path "$($_.FullName)\bin\jpackage.exe"
        } |
        Select-Object -First 1

if ($null -ne $existingJdk) {

    $JDK25_PATH = $existingJdk.FullName

    Write-Host "JDK 25 уже установлен:" -ForegroundColor Green
    Write-Host $JDK25_PATH

} else {

    Write-Host "JDK 25 не найден." -ForegroundColor Yellow
    Write-Host "Скачивание Eclipse Temurin JDK 25..." -ForegroundColor Yellow

    Invoke-WebRequest `
        -Uri $DOWNLOAD_URL `
        -OutFile $TEMP_ZIP `
        -UseBasicParsing

    Write-Host "Распаковка JDK 25..." -ForegroundColor Yellow

    if (Test-Path $TEMP_EXTRACT) {
        Remove-Item $TEMP_EXTRACT -Recurse -Force
    }

    New-Item `
        -ItemType Directory `
        -Path $TEMP_EXTRACT `
        -Force | Out-Null

    Expand-Archive `
        -Path $TEMP_ZIP `
        -DestinationPath $TEMP_EXTRACT `
        -Force

    # Находим распакованную директорию JDK
    $extractedDir = Get-ChildItem `
        $TEMP_EXTRACT `
        -Directory |
            Select-Object -First 1

    if ($null -eq $extractedDir) {
        throw "Не удалось найти распакованную директорию JDK 25."
    }

    # Создание директории Eclipse Adoptium
    if (!(Test-Path $JDK_ROOT)) {
        New-Item `
            -ItemType Directory `
            -Path $JDK_ROOT `
            -Force | Out-Null
    }

    # Конечный путь сохраняем с оригинальным именем JDK
    $JDK25_PATH = Join-Path $JDK_ROOT $extractedDir.Name

    if (Test-Path $JDK25_PATH) {
        Remove-Item $JDK25_PATH -Recurse -Force
    }

    Write-Host "Установка в:" -ForegroundColor Yellow
    Write-Host $JDK25_PATH

    Copy-Item `
        $extractedDir.FullName `
        -Destination $JDK25_PATH `
        -Recurse `
        -Force

    # Очистка
    Remove-Item $TEMP_ZIP -Force
    Remove-Item $TEMP_EXTRACT -Recurse -Force

    Write-Host "JDK 25 установлен успешно!" -ForegroundColor Green
}

# ============================================================
# Проверка Java
# ============================================================

Write-Host "`nПроверка Java..." -ForegroundColor Cyan

& "$JDK25_PATH\bin\java.exe" -version

# ============================================================
# Проверка javac
# ============================================================

Write-Host "`nПроверка javac..." -ForegroundColor Cyan

& "$JDK25_PATH\bin\javac.exe" -version

# ============================================================
# Проверка jpackage
# ============================================================

Write-Host "`nПроверка jpackage..." -ForegroundColor Cyan

& "$JDK25_PATH\bin\jpackage.exe" --version

# ============================================================
# Готово
# ============================================================

Write-Host "`n=== JDK 25 готов! ===" -ForegroundColor Green

Write-Host "`nИспользуемый JDK:" -ForegroundColor Yellow
Write-Host $JDK25_PATH

Write-Host "`nДля сборки DisciplineApp:" -ForegroundColor Yellow
Write-Host "  cd C:\Users\seryo\DisciplineApp"
Write-Host "  .\gradlew.bat clean build"
