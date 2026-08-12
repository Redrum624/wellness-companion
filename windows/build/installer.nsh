; Wellness Companion installer hooks.
;
; One installer.nsh serves both build flavors produced by build_installer.bat:
;   - OFFLINE: the model + vc_redist.x64.exe are embedded under resources\.
;   - LEAN:    nothing is embedded; the model is downloaded here at install time.
;
; Model resolution order (first hit wins):
;   1. Already in %LOCALAPPDATA%\wellness-companion\model\   -> nothing to do.
;   2. Embedded under $INSTDIR\resources\model\ (offline) -> move to AppData.
;   3. Next to the installer $EXEDIR\model\ (manual)     -> copy to AppData.
;   4. Not found (lean)                                  -> download from HF.

!define MODEL_DIR_REL  "model\Qwen3-4B-Instruct-2507-GGUF"
!define MODEL_REL      "model\Qwen3-4B-Instruct-2507-GGUF\Qwen3-4B-Instruct-2507-Q4_K_M.gguf"
!define MODEL_FILENAME "Qwen3-4B-Instruct-2507-Q4_K_M.gguf"
!define MODEL_URL      "https://huggingface.co/lmstudio-community/Qwen3-4B-Instruct-2507-GGUF/resolve/main/Qwen3-4B-Instruct-2507-Q4_K_M.gguf"
!define MODEL_BYTES    "2497280448"

!macro customInstall
  ; --- Install Visual C++ Redistributable (required for the AI engine) ---
  DetailPrint "Checking Visual C++ Redistributable..."
  IfFileExists "$SYSDIR\vcruntime140.dll" VcRedistDone VcRedistNeeded

  VcRedistNeeded:
    ; Offline embeds it under resources\; lean ships it next to the installer.
    IfFileExists "$INSTDIR\resources\vc_redist.x64.exe" VcRedistInstallRes 0
    IfFileExists "$EXEDIR\vc_redist.x64.exe" VcRedistInstallExe VcRedistSkip

    VcRedistInstallRes:
      DetailPrint "Installing Visual C++ Redistributable (bundled)..."
      nsExec::ExecToLog '"$INSTDIR\resources\vc_redist.x64.exe" /install /quiet /norestart'
      Goto VcRedistDone

    VcRedistInstallExe:
      DetailPrint "Installing Visual C++ Redistributable..."
      nsExec::ExecToLog '"$EXEDIR\vc_redist.x64.exe" /install /quiet /norestart'
      Goto VcRedistDone

    VcRedistSkip:
      DetailPrint "VC++ Redistributable not found, skipping (the app will prompt if needed)."

  VcRedistDone:

  ; --- Provision the AI model into AppData (survives app upgrades) ---
  DetailPrint "Setting up AI model..."

  ; Step 1: Already in AppData? Done.
  IfFileExists "$LOCALAPPDATA\wellness-companion\${MODEL_REL}" ModelOK ModelNotInAppData

  ModelNotInAppData:
  CreateDirectory "$LOCALAPPDATA\wellness-companion\${MODEL_DIR_REL}"

  ; Step 2: Embedded under resources\ (offline build) -> move it to AppData.
  IfFileExists "$INSTDIR\resources\${MODEL_REL}" ModelMigrate ModelNoMigrate

  ModelMigrate:
    DetailPrint "Installing bundled AI model..."
    nsExec::ExecToLog 'robocopy "$INSTDIR\resources\${MODEL_DIR_REL}" "$LOCALAPPDATA\wellness-companion\${MODEL_DIR_REL}" ${MODEL_FILENAME} /MOV /R:3 /W:5 /NP /NDL /NJH /NJS'
    IfFileExists "$LOCALAPPDATA\wellness-companion\${MODEL_REL}" ModelOK ModelCopyFailed

  ModelNoMigrate:

  ; Step 3: Placed next to the installer (manual) -> copy it to AppData.
  IfFileExists "$EXEDIR\${MODEL_REL}" ModelSourceFound ModelDownload

  ModelSourceFound:
    DetailPrint "Copying AI model to AppData (this may take a minute)..."
    nsExec::ExecToLog 'robocopy "$EXEDIR\${MODEL_DIR_REL}" "$LOCALAPPDATA\wellness-companion\${MODEL_DIR_REL}" ${MODEL_FILENAME} /R:3 /W:5 /NP /NDL /NJH /NJS'
    IfFileExists "$LOCALAPPDATA\wellness-companion\${MODEL_REL}" ModelOK ModelCopyFailed

  ; Step 4: Lean build, no local model -> download it from Hugging Face.
  ModelDownload:
    DetailPrint "Downloading AI model (~2.5 GB) from Hugging Face."
    DetailPrint "This can take several minutes - the window may look frozen, please wait..."
    nsExec::ExecToLog `"$SYSDIR\curl.exe" -L --fail --retry 3 --retry-delay 5 --no-progress-meter -o "$LOCALAPPDATA\wellness-companion\${MODEL_REL}" "${MODEL_URL}"`
    Pop $0
    StrCmp $0 "0" ModelVerify ModelDownloadFailed

  ModelVerify:
    DetailPrint "Verifying downloaded model..."
    nsExec::ExecToLog `powershell -NoProfile -ExecutionPolicy Bypass -Command "if ((Get-Item -LiteralPath '$LOCALAPPDATA\wellness-companion\${MODEL_REL}').Length -eq ${MODEL_BYTES}) { exit 0 } else { exit 1 }"`
    Pop $0
    StrCmp $0 "0" ModelOK ModelDownloadCorrupt

  ModelDownloadCorrupt:
    Delete "$LOCALAPPDATA\wellness-companion\${MODEL_REL}"
    MessageBox MB_OK|MB_ICONEXCLAMATION "The AI model download was incomplete or corrupted.$\r$\n$\r$\nCheck your internet connection and re-run the installer, or manually place the model folder at:$\r$\n$LOCALAPPDATA\wellness-companion\model\"
    Goto ModelDone

  ModelDownloadFailed:
    MessageBox MB_OK|MB_ICONEXCLAMATION "Could not download the AI model (no internet connection?).$\r$\n$\r$\nYou can finish setup now; the app will work once the model is available.$\r$\n$\r$\nManually place the model folder at:$\r$\n$LOCALAPPDATA\wellness-companion\model\$\r$\n$\r$\nOr re-run this installer with an internet connection."
    Goto ModelDone

  ModelCopyFailed:
    MessageBox MB_OK|MB_ICONEXCLAMATION "Failed to copy the AI model (disk may be full).$\r$\n$\r$\nPlease manually copy the model\ folder to:$\r$\n$LOCALAPPDATA\wellness-companion\model\"
    Goto ModelDone

  ModelOK:
    DetailPrint "AI model ready."

  ModelDone:
!macroend

!macro customUnInstall
  ; Remove the model from both locations
  RMDir /r "$INSTDIR\resources\model"
  RMDir /r "$LOCALAPPDATA\wellness-companion\model"
!macroend
