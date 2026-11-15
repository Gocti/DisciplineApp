# Инструкция по сборке DisciplineApp

## Текущая схема сборки

Для Windows используется следующая схема:

```text
Gradle
   ↓
DisciplineApp-1.0.0-all.jar
   ↓
ZIP / Inno Setup
   ↓
Готовое приложение
```

Основной установщик — **Inno Setup**.

`jpackage` в текущей конфигурации не является обязательным.

---

# 1. Требования

Для сборки приложения используются:

| Компонент               | Статус               | Назначение                                  |
| ----------------------- | -------------------- | ------------------------------------------- |
| JDK 25                  | ✓ Установлен        | Компиляция и сборка приложения              |
| Gradle / Gradle Wrapper | ✓ В проекте         | Сборка JAR                                  |
| Inno Setup              | Требуется для `.exe` | Создание Windows-установщика                |
| jpackage                | Не требуется         | Опциональная упаковка вместе с Java Runtime |

---

# 2. Установка JDK 25

Если JDK 25 ещё не установлен, можно использовать:

```powershell
.\install-jdk25.ps1
```

После установки проверьте:

```batch
java -version
```

и:

```batch
javac -version
```

Ожидается версия Java 25.

Для сборки также можно проверить:

```batch
jpackage --version
```

`jpackage` входит в состав JDK 25, поэтому отдельно устанавливать его не нужно.

---

# 3. Быстрая сборка ZIP

ZIP-вариант не требует создания Windows-установщика.

Запустите:

```batch
cd "C:\Users\seryo\OneDrive\Документы\GitHub\DisciplineApp"
build-zip.bat
```

Результат:

```text
build\DisciplineApp-1.0.0.zip
```

В архив входят:

```text
DisciplineApp-1.0.0-all.jar
run.bat
README.md
LICENSE
```

После распаковки приложение запускается через:

```text
run.bat
```

### Важно

Текущий ZIP-вариант предполагает наличие Java 25 на компьютере пользователя.

То есть пользователь должен иметь установленный:

```text
JRE 25 или JDK 25
```

---

# 4. Windows EXE-установщик

Для создания обычного Windows-установщика используется **Inno Setup**.

Установка:

```powershell
winget install --id JRSoftware.InnoSetup
```

После установки можно проверить наличие компилятора:

```batch
where ISCC
```

Если используется собственный скрипт установки Inno Setup:

```powershell
.\install-innosetup.ps1
```

---

# 5. Сборка EXE-установщика

После установки Inno Setup выполните:

```batch
cd "C:\Users\seryo\OneDrive\Документы\GitHub\DisciplineApp"
build-innosetup.bat
```

Результат:

```text
installer\Output\DisciplineApp-Setup-1.0.0.exe
```

Этот файл является установщиком приложения.

---

# 6. Что делает Inno Setup

Inno Setup отвечает именно за установку приложения в Windows.

Например, установщик может:

* создать папку приложения;
* скопировать JAR и необходимые файлы;
* создать ярлык в меню «Пуск»;
* создать ярлык на рабочем столе;
* добавить запись в список установленных программ;
* предоставить деинсталлятор;
* удалить приложение через стандартный механизм Windows.

При этом Inno Setup сам по себе **не является Java Runtime**.

Если установщик содержит только JAR, на компьютере пользователя должна присутствовать совместимая Java.

---

# 7. Зачем нужен jpackage

`jpackage` — это дополнительный инструмент JDK, который позволяет подготовить Java-приложение к распространению вместе с Java Runtime.

Без `jpackage` текущая схема выглядит так:

```text
DisciplineApp-Setup.exe
        ↓
Устанавливает JAR
        ↓
Windows должна иметь Java 25
        ↓
Запуск приложения
```

С использованием `jpackage` можно сделать:

```text
DisciplineApp
├── DisciplineApp.exe
├── app/
│   └── DisciplineApp.jar
└── runtime/
    └── встроенная Java Runtime
```

Тогда схема становится:

```text
DisciplineApp-Setup.exe
        ↓
Устанавливает приложение
        ↓
Внутри уже есть Java Runtime
        ↓
Java отдельно устанавливать не нужно
```

Это особенно удобно при распространении приложения среди пользователей, у которых Java заранее не установлена.

---

# 8. Нужен ли jpackage сейчас?

Для текущей сборки — **нет**.

Если задача:

```text
собрать приложение
↓
получить ZIP
↓
получить EXE-установщик
```

достаточно:

```text
JDK 25
+
Gradle
+
Inno Setup
```

`jpackage` можно оставить как возможное дальнейшее улучшение.

---

# 9. Почему jpackage раньше выдавал ошибку

При создании Windows-пакетов `jpackage` может использовать дополнительные инструменты Windows.

В частности, для создания некоторых типов установочных пакетов требуется **WiX Toolset**.

Поэтому ситуация:

```text
jpackage
   ↓
не может создать требуемый Windows installer
   ↓
ошибка отсутствия WiX
```

Не означает, что Java-приложение невозможно упаковать.

Это означает только, что конкретному способу упаковки не хватает необходимого инструментария.

Inno Setup в данном случае является отдельным решением для создания `.exe`-установщика.

---

# 10. Рекомендуемая структура проекта

```text
DisciplineApp/
│
├── build-zip.bat
├── build-innosetup.bat
├── run.bat
│
├── install-jdk25.ps1
├── install-innosetup.ps1
│
├── installer/
│   └── discipline-app.iss
│
├── build/
│   ├── libs/
│   ├── distribution/
│   └── DisciplineApp-1.0.0.zip
│
└── ...
```

---

# 11. Основные команды

### Собрать ZIP

```batch
build-zip.bat
```

Результат:

```text
build\DisciplineApp-1.0.0.zip
```

### Собрать EXE

```batch
build-innosetup.bat
```

Результат:

```text
installer\Output\DisciplineApp-Setup-1.0.0.exe
```

### Проверить Java

```batch
java -version
```

### Проверить компилятор Java

```batch
javac -version
```

### Проверить jpackage

```batch
jpackage --version
```

---

# 12. Какой вариант использовать

### Для разработки

```text
Gradle → JAR → запуск из IDE
```

### Для быстрого распространения

```text
Gradle → ZIP
```

Пользователь устанавливает Java 25 и запускает `run.bat`.

### Для обычной Windows-установки

```text
Gradle → JAR → Inno Setup → EXE
```

### Для полностью автономного приложения

В дальнейшем:

```text
Gradle
   ↓
JAR
   ↓
jpackage
   ↓
Application Image + Java Runtime
   ↓
Inno Setup
   ↓
DisciplineApp-Setup.exe
```

В последнем варианте пользователю не потребуется отдельно устанавливать Java.

