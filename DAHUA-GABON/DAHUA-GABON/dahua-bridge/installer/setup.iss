; Script Inno Setup — Dahua Bridge v1.0
; Télécharger Inno Setup : https://jrsoftware.org/isinfo.php
; Compiler ce fichier pour générer DahuaBridge-Setup-v1.0.exe

#define AppName    "Dahua Bridge"
#define AppVersion "1.0.0"
#define AppPublisher "Votre Société"
#define AppExeName "bridge-server.exe"
#define ServiceName "DahuaBridgeSvc"

[Setup]
AppId={{B3A7C2D1-4E5F-6789-ABCD-EF0123456789}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher={#AppPublisher}
DefaultDirName={autopf}\DahuaBridge
DefaultGroupName={#AppName}
OutputDir=..\dist
OutputBaseFilename=DahuaBridge-Setup-v{#AppVersion}
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=admin
; Icône de l'installeur (optionnel)
; SetupIconFile=icon.ico

[Languages]
Name: "french"; MessagesFile: "compiler:Languages\French.isl"

[Files]
; Exécutable compilé
Source: "..\dist\bridge-server.exe"; DestDir: "{app}"; Flags: ignoreversion

; Frontend (fichiers statiques Vue.js)
Source: "..\frontend\dist\*"; DestDir: "{app}\frontend\dist"; Flags: recursesubdirs ignoreversion

; Configuration (le client peut l'éditer)
Source: "..\backend\.env"; DestDir: "{app}"; DestName: ".env"; Flags: ignoreversion onlyifdoesntexist

; Script de démarrage manuel
Source: "..\dist\start.bat"; DestDir: "{app}"; Flags: ignoreversion

[Dirs]
Name: "{app}\logs"
Name: "C:\SageExports"

[Icons]
Name: "{group}\Dahua Bridge - Interface Web"; Filename: "{app}\open-browser.bat"
Name: "{group}\Désinstaller Dahua Bridge";   Filename: "{uninstallexe}"

[Run]
; Installer le service Windows au moment de l'installation
Filename: "{app}\bridge-server.exe"; Parameters: "install"; StatusMsg: "Installation du service Windows..."; Flags: runhidden waituntilterminated
Filename: "{app}\bridge-server.exe"; Parameters: "start";   StatusMsg: "Démarrage du service..."; Flags: runhidden waituntilterminated

; Ouvrir l'interface web après installation
Filename: "{app}\open-browser.bat"; Description: "Ouvrir l'interface web"; Flags: postinstall skipifsilent

[UninstallRun]
Filename: "{app}\bridge-server.exe"; Parameters: "stop";      Flags: runhidden
Filename: "{app}\bridge-server.exe"; Parameters: "uninstall"; Flags: runhidden

[Code]
// Vérifier que Node.js n'est pas requis (tout est compilé dans l'exe)
procedure InitializeWizard();
begin
  WizardForm.WelcomeLabel2.Caption :=
    'Ce programme va installer Dahua Bridge sur votre serveur.' + #13#10 + #13#10 +
    'Le bridge synchronise automatiquement les pointages DSS Pro' + #13#10 +
    'et génère les exports pour Sage X3.' + #13#10 + #13#10 +
    'Prérequis : PostgreSQL installé et DSS Pro V8.7 accessible.';
end;
