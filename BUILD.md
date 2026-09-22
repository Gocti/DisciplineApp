# Инструкция по сборке DisciplineApp

## Проблема с jpackage

`jpackage` в нашей системе не создаёт файлы из-за отсутствия WiX Toolset и других зависимостей. 
**Рабочее решение:** ZIP-архив с приложением.

---

## Способ 1: ZIP-архив (работает)

### Быстрая сборка

```batch
cd "C:\Users\seryo\OneDrive\Документы\GitHub\DisciplineApp"
build-zip.bat
```

**Результат:** `build\DisciplineApp-1.0.0.zip` (~3.7 MB)

### Содержимое архива:
- `DisciplineApp-1.0.0-all.jar` — приложение
- `run.bat` — скрипт запуска
- `README.md` — документация
- `LICENSE` — лицензия

### Запуск приложения:
1. Распакуйте ZIP в любую папку
2. Запустите `run.bat`
3. Требуется JDK 21+ или JRE 21+

---

## Способ 2: MSI/EXE через jpackage (требует настройки)

### Требуется:
1. **JDK 21** — установлен в `C:\Program Files\Java\jdk-21` ✓
2. **WiX Toolset v4/v5** — для создания MSI

### Установка WiX Toolset:
```powershell
# Скачайте с https://wixtoolset.org/docs/intro/
# Или используйте winget:
winget install --id WiXToolset.WiXv4
```

### Сборка после установки WiX:
```batch
cd "C:\Users\seryo\OneDrive\Документы\GitHub\DisciplineApp"
set JAVA_HOME=C:\Program Files\Java\jdk-21
"C:\Program Files\Java\jdk-21\bin\java.exe" -jar gradle/wrapper/gradle-wrapper.jar createMsi --no-daemon
```

**Результат:** `build\installers\DisciplineApp-1.0.0.msi`

---

## Способ 3: Inno Setup (альтернатива MSI)

### Установка Inno Setup:
```powershell
winget install --id JRSoftware.InnoSetup
```

Или вручную: https://jrsoftware.org/isdl.php

### Сборка:
```batch
cd "C:\Users\seryo\OneDrive\Документы\GitHub\DisciplineApp"
build-innosetup.bat
```

**Результат:** `installer\Output\DisciplineApp-Setup-1.0.0.exe`

---

## Структура файлов сборки

```
DisciplineApp/
├── build-zip.bat              # Создание ZIP-архива (РАБОТАЕТ)
├── build-with-jdk21.bat       # Сборка MSI/EXE через jpackage
├── build-innosetup.bat        # Сборка через Inno Setup
├── run.bat                    # Скрипт запуска приложения
├── install-jdk21.ps1          # Установка JDK 21
├── install-innosetup.ps1      # Установка Inno Setup
├── installer/
│   └── discipline-app.iss     # Скрипт Inno Setup
└── build/
    ├── DisciplineApp-1.0.0.zip    # ZIP-архив (готов)
    ├── libs/                      # JAR файлы
    └── distribution/              # Файлы для архива
```

---

## Требования

| Компонент | Статус | Назначение |
|-----------|--------|------------|
| JDK 21 | ✓ Установлен | Сборка приложения |
| WiX Toolset | ❌ Не установлен | Создание MSI |
| Inno Setup | ❌ Не установлен | Создание EXE установщика |

---

## Устранение проблем

### jpackage не создаёт файлы
Требуется WiX Toolset для MSI или используйте ZIP-архив.

### Ошибка "Java not found" при запуске
Установите JDK 21: https://adoptium.net/
Или установите переменную среды JAVA_HOME.

### Ошибка Compress-Archive
Используется .NET класс для создания ZIP, требуется PowerShell 5+.
