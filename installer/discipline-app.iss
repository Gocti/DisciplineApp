; ============================================================
; DisciplineApp - Inno Setup 7
; ============================================================

#define MyAppName "DisciplineApp"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "Discipline"
#define MyAppExeName "DisciplineApp.exe"
#define MyAppDir "..\build\DisciplineApp"

; ============================================================
; Setup
; ============================================================

[Setup]

AppId=9A4C5F3E-1234-4567-8901-ACDE12345678
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}

DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}

OutputDir=Output
OutputBaseFilename=DisciplineApp-Setup-{#MyAppVersion}

Compression=lzma
SolidCompression=yes

WizardStyle=modern

PrivilegesRequired=admin

UninstallDisplayName={#MyAppName}
UninstallDisplayIcon={app}\{#MyAppExeName}

; ============================================================
; Languages
; ============================================================

[Languages]

Name: "english"; MessagesFile: "compiler:Default.isl"
Name: "russian"; MessagesFile: "compiler:Languages\Russian.isl"

; ============================================================
; Tasks
; ============================================================

[Tasks]

Name: "desktopicon"; \
    Description: "{cm:CreateDesktopIcon}"; \
    GroupDescription: "{cm:AdditionalIcons}"; \
    Flags: unchecked

; ============================================================
; Files
; ============================================================

[Files]

Source: "{#MyAppDir}\*"; \
    DestDir: "{app}"; \
    Flags: ignoreversion recursesubdirs createallsubdirs

; ============================================================
; Shortcuts
; ============================================================

[Icons]

Name: "{group}\{#MyAppName}"; \
    Filename: "{app}\{#MyAppExeName}"

Name: "{group}\{cm:UninstallProgram,{#MyAppName}}"; \
    Filename: "{uninstallexe}"

Name: "{autodesktop}\{#MyAppName}"; \
    Filename: "{app}\{#MyAppExeName}"; \
    Tasks: desktopicon

; ============================================================
; Launch application after installation
; ============================================================

[Run]

Filename: "{app}\{#MyAppExeName}"; \
    Description: "Запустить {#MyAppName}"; \
    Flags: nowait postinstall skipifsilent
