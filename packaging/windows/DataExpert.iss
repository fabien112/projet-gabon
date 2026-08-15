#define AppName "DataExpert"
#define AppVersion "0.1.0"
#ifndef AppImageDir
  #define AppImageDir "..\..\dist-installer\app-image\DataExpert"
#endif

[Setup]
AppId={{8F3C2A11-4B6E-4D9A-9C21-DATAEXPERT0001}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher=DataExpert
DefaultDirName={localappdata}\Programs\{#AppName}
DefaultGroupName={#AppName}
OutputDir=..\..\dist-installer
OutputBaseFilename=DataExpert-Setup
Compression=lzma2
SolidCompression=yes
PrivilegesRequired=lowest
WizardStyle=modern
DisableProgramGroupPage=yes
UninstallDisplayName={#AppName}
SetupLogging=yes

[Languages]
Name: "french"; MessagesFile: "compiler:Languages\French.isl"

[Files]
Source: "{#AppImageDir}\*"; DestDir: "{app}"; Flags: recursesubdirs ignoreversion

[Icons]
Name: "{group}\{#AppName}"; Filename: "{app}\Lancer-DataExpert.bat"; WorkingDir: "{app}"
Name: "{group}\Desinstaller {#AppName}"; Filename: "{uninstallexe}"
Name: "{userdesktop}\{#AppName}"; Filename: "{app}\Lancer-DataExpert.bat"; WorkingDir: "{app}"; Tasks: desktopicon

[Tasks]
Name: "desktopicon"; Description: "Creer un raccourci sur le Bureau"; GroupDescription: "Raccourcis :"

[Run]
Filename: "{app}\Lancer-DataExpert.bat"; Flags: nowait postinstall runhidden

[UninstallDelete]
Type: filesandordirs; Name: "{app}\logs"
Type: filesandordirs; Name: "{app}\runtime"
Type: filesandordirs; Name: "{app}\app"
