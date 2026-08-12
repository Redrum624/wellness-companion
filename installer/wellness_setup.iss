; ===========================================================================
;  Wellness Companion - single-exe Windows installer (Inno Setup 6)
; ===========================================================================
;  Wraps the already-built Electron app (windows\dist\win-unpacked\), bundles
;  the Android debug APK for optional USB sideload, and provisions the AI model
;  post-install via setup_model.ps1.
;
;  Build:  installer\build_installer.bat            (lean - model downloaded)
;          installer\build_installer.bat offline    (model embedded, ~2.8 GB)
;  Output: installer\output\Wellness Companion Setup <version>.exe
;
;  What ships INSIDE the exe: the unpacked Electron app, the debug APK, the
;  VC++ redistributable, setup_model.ps1 and install_phone_app.bat.
;  What is fetched at install time (lean only): the 2.5 GB Qwen3 GGUF model.
;
;  AppVersion is passed in by build_installer.bat (/DAppVersion=x.y.z), read
;  from windows\package.json so there is exactly ONE version source of truth.
; ===========================================================================

#define AppName       "Wellness Companion"
#define AppPublisher  "Redrum624"
#define AppExeName    "Wellness Companion.exe"
#define AppDataSlug   "wellness-companion"

; Fallback so the script still compiles if ISCC is invoked by hand without /D.
#ifndef AppVersion
  #define AppVersion "0.0.0"
#endif

; Repo root is the parent of this script's folder (installer\).
#define RepoRoot AddBackslash(SourcePath) + ".."

[Setup]
AppId={{7C4E9A16-2B58-4D77-9E3F-5EA1C0DE0001}
AppName={#AppName}
AppVersion={#AppVersion}
AppVerName={#AppName} {#AppVersion}
AppPublisher={#AppPublisher}
DefaultDirName={autopf}\{#AppName}
DefaultGroupName={#AppName}
DisableProgramGroupPage=yes
; Admin: needed to install the VC++ redistributable machine-wide.
PrivilegesRequired=admin
OutputDir={#SourcePath}\output
OutputBaseFilename={#AppName} Setup {#AppVersion}
SetupIconFile={#SourcePath}\icon\wellness.ico
UninstallDisplayIcon={app}\{#AppExeName}
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; \
    GroupDescription: "{cm:AdditionalIcons}"; Flags: checkedonce

; Unchecked by default: needs a phone plugged in with USB-debugging on, so it
; would fail noisily for the majority who install the desktop app on its own.
Name: "installandroid"; \
    Description: "Install the Wellness Companion app on my phone now (phone must be connected by USB with USB-debugging enabled)"; \
    GroupDescription: "Android app:"; Flags: unchecked

; Only meaningful for the lean installer; the offline build already carries the
; model, and ShouldSkipPage/Check below hide the choice when it cannot apply.
Name: "downloadmodel"; \
    Description: "Download the AI model now (~2.5 GB, needs an internet connection)"; \
    GroupDescription: "AI model:"; Flags: checkedonce

[Files]
; --- The Electron app: contents of dist\win-unpacked\ land directly in {app} ---
Source: "{#RepoRoot}\windows\dist\win-unpacked\*"; DestDir: "{app}"; \
    Flags: recursesubdirs createallsubdirs ignoreversion

; --- Install-time helpers ---
Source: "{#SourcePath}\setup_model.ps1";       DestDir: "{app}"; Flags: ignoreversion
Source: "{#SourcePath}\install_phone_app.bat"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#SourcePath}\icon\wellness.ico";     DestDir: "{app}"; Flags: ignoreversion

; --- Android debug APK -> where install_phone_app.bat looks for it ---
Source: "{#RepoRoot}\android\app\build\outputs\apk\debug\app-debug.apk"; \
    DestDir: "{app}\android"; DestName: "WellnessCompanion.apk"; Flags: ignoreversion

; --- VC++ redistributable: always embedded so the setup is a true single file ---
;     Extracted to {tmp} and removed afterwards; only run when vcruntime140.dll
;     is missing (see the Check function below).
Source: "{#RepoRoot}\vc_redist.x64.exe"; DestDir: "{tmp}"; \
    Flags: deleteafterinstall; Check: VCRedistNeeded

; --- OPTIONAL: bundle the model for a fully-offline installer (~2.8 GB) ---
;     Compiled in ONLY with /DINCLUDE_MODEL (build_installer.bat "offline").
;     setup_model.ps1 then MOVES it out of {app} into the per-user data root,
;     so it survives uninstall/upgrade and never sits in Program Files.
#ifdef INCLUDE_MODEL
Source: "{#RepoRoot}\model\*"; DestDir: "{app}\model"; \
    Flags: recursesubdirs createallsubdirs ignoreversion
#endif

[Icons]
Name: "{group}\{#AppName}"; Filename: "{app}\{#AppExeName}"; \
    IconFilename: "{app}\wellness.ico"; WorkingDir: "{app}"
Name: "{group}\Install the phone app"; Filename: "{app}\install_phone_app.bat"; \
    IconFilename: "{app}\wellness.ico"; WorkingDir: "{app}"
Name: "{group}\{cm:UninstallProgram,{#AppName}}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\{#AppExeName}"; \
    IconFilename: "{app}\wellness.ico"; WorkingDir: "{app}"; Tasks: desktopicon

[Run]
; 1) VC++ redistributable - required by better-sqlite3 and the llama.cpp binding.
Filename: "{tmp}\vc_redist.x64.exe"; Parameters: "/install /quiet /norestart"; \
    StatusMsg: "Installing the Visual C++ runtime..."; \
    Flags: waituntilterminated; Check: VCRedistNeeded

; 2) Provision the AI model. Runs hidden; the wizard shows StatusMsg and the
;    full detail log is written to {app}\setup.log. -AllowDownload is passed
;    only when the user left the "download now" task checked, so an offline
;    machine can finish setup and supply the model later.
Filename: "powershell.exe"; \
    Parameters: "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File ""{app}\setup_model.ps1"" -InstallRoot ""{app}"" -SetupDir ""{src}"" -AllowDownload"; \
    WorkingDir: "{app}"; \
    StatusMsg: "Setting up the AI model (a 2.5 GB download can take several minutes; see {app}\setup.log)..."; \
    Flags: waituntilterminated runhidden; Tasks: downloadmodel

; Same step without -AllowDownload: still migrates an embedded/adjacent model
; into place, but never starts a download.
Filename: "powershell.exe"; \
    Parameters: "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File ""{app}\setup_model.ps1"" -InstallRoot ""{app}"" -SetupDir ""{src}"""; \
    WorkingDir: "{app}"; \
    StatusMsg: "Setting up the AI model..."; \
    Flags: waituntilterminated runhidden; Tasks: not downloadmodel

; 3) Optional: sideload the Android app onto a connected phone.
Filename: "{app}\install_phone_app.bat"; WorkingDir: "{app}"; \
    Description: "Install the Wellness Companion app on your phone"; \
    Flags: postinstall shellexec skipifsilent; Tasks: installandroid

; 4) Offer to launch. runascurrentuser so the GUI does not run elevated.
Filename: "{app}\{#AppExeName}"; \
    Description: "Launch {#AppName} now"; WorkingDir: "{app}"; \
    Flags: postinstall nowait skipifsilent runascurrentuser

[UninstallRun]
; Close a running instance before files are deleted - otherwise the Electron
; exe and the native better-sqlite3 / llama DLLs stay locked, the sync server
; keeps port 9847 bound, and uninstall leaves a half-removed folder behind.
Filename: "{cmd}"; Parameters: "/c taskkill /F /IM ""{#AppExeName}"" /T >nul 2>&1"; \
    Flags: runhidden; RunOnceId: "WellnessStopApp"

[UninstallDelete]
; Remove only install-time-generated content. The user's data is deliberately
; LEFT IN PLACE and must be deleted by hand to purge everything:
;   %APPDATA%\wellness-companion\wellness.db       - all tracked entries
;   %LOCALAPPDATA%\wellness-companion\model\       - the 2.5 GB AI model
;   %LOCALAPPDATA%\wellness-companion\tools\       - downloaded platform-tools
; (The old NSIS installer deleted the model on uninstall, forcing a full
; re-download on every reinstall. That behavior is intentionally not carried
; over.)
Type: filesandordirs; Name: "{app}\model"
Type: filesandordirs; Name: "{app}\android"
Type: files;          Name: "{app}\setup.log"

[Code]
{ ---------------------------------------------------------------------------
  The VC++ runtime backs better-sqlite3 and node-llama-cpp. Skip both the
  extraction and the run when it is already present, which is the common case
  on an up-to-date Windows 11 machine - that saves unpacking 25 MB for nothing.
  --------------------------------------------------------------------------- }
function VCRedistNeeded: Boolean;
begin
  Result := not FileExists(ExpandConstant('{sys}\vcruntime140.dll'));
end;
