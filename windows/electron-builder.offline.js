// Offline electron-builder configuration.
//
// Reuses the base "build" config from package.json (single source of truth) and
// adds extraResources so the AI model and the VC++ redistributable are embedded
// inside the installer for a fully self-contained, no-download install.
//
// Used by build_installer.bat for the "offline" build:
//   electron-builder --win --config electron-builder.offline.js
//
// Paths in `from` are relative to this project dir (windows\), so ../model and
// ../vc_redist.x64.exe resolve to the project root. They land under
// resources\model\ and resources\vc_redist.x64.exe, which build\installer.nsh
// then moves into place at install time.
const base = require('./package.json').build

module.exports = {
  ...base,
  extraResources: [
    ...(base.extraResources || []),
    { from: '../model', to: 'model' },
    { from: '../vc_redist.x64.exe', to: 'vc_redist.x64.exe' }
  ]
}
