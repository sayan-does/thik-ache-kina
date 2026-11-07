; Inno Setup Script for Agent Remote Windows Desktop Application
; Generates standalone AgentRemote-Setup.exe installer

#define MyAppName "Agent Remote"
#define MyAppVersion "2.1.0"
#define MyAppPublisher "Sayan Does"
#define MyAppURL "https://github.com/sayan-does/thik-ache-kina"
#define MyAppExeName "AgentRemote.exe"

[Setup]
AppId={{5A8E19B2-3D7C-4C91-A4D2-F86BE825E129}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}
DefaultDirName={autopf}\AgentRemote
DisableProgramGroupPage=yes
DefaultGroupName={#MyAppName}
OutputDir=..\dist
OutputBaseFilename=AgentRemote-Setup-v{#MyAppVersion}
SetupIconFile=..\agent-bridge\desktop\resources\icon.ico
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=lowest
UninstallDisplayIcon={app}\{#MyAppExeName}

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked
Name: "startupicon"; Description: "Launch Agent Remote automatically on Windows login"; GroupDescription: "Windows Integration"; Flags: unchecked

[Files]
Source: "..\agent-bridge\dist\AgentRemote\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\desktop\resources\icon.ico"
Name: "{group}\{cm:UninstallProgram,{#MyAppName}}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon
Name: "{userstartup}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: startupicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent
