import { app, BrowserWindow, ipcMain, shell, nativeImage } from 'electron'
import { join } from 'path'
import { electronApp, optimizer, is } from '@electron-toolkit/utils'
import { initDatabase, registerDatabaseHandlers, closeDatabase, waitForPendingBackup } from './database'
import { registerLlmHandlers, disposeLlm } from './llm'
import { startSyncServer, stopSyncServer, registerSyncHandlers } from './sync-server'

function createWindow(): void {
  const iconPath = join(__dirname, '../../resources/icon.png')

  const mainWindow = new BrowserWindow({
    width: 1200,
    height: 800,
    minWidth: 900,
    minHeight: 600,
    show: false,
    autoHideMenuBar: true,
    icon: iconPath,
    webPreferences: {
      preload: join(__dirname, '../preload/index.js'),
      // The preload uses only contextBridge + ipcRenderer, which are
      // sandbox-compatible, so the renderer runs fully sandboxed.
      sandbox: true,
      contextIsolation: true,
      nodeIntegration: false
    }
  })

  mainWindow.on('ready-to-show', () => {
    mainWindow.show()
  })

  // Only ever hand http(s) to the OS. Without a scheme check, a url like
  // file:, smb: or ms-msdt: reaches the shell's protocol handlers — a real
  // risk here because entry data arrives over the LAN sync channel and is
  // rendered in this window.
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    let scheme = ''
    try {
      scheme = new URL(url).protocol
    } catch {
      return { action: 'deny' }
    }
    if (scheme === 'https:' || scheme === 'http:') void shell.openExternal(url)
    return { action: 'deny' }
  })

  // Keep the window pinned to the packaged app. Navigating away would carry
  // the exposed db/llm/sync bridges to whatever loaded next.
  mainWindow.webContents.on('will-navigate', (event, url) => {
    const devUrl = process.env['ELECTRON_RENDERER_URL']
    if (devUrl && url.startsWith(devUrl)) return
    if (url.startsWith('file://')) return
    event.preventDefault()
  })

  // No webviews are used; refuse any that markup tries to attach.
  mainWindow.webContents.on('will-attach-webview', (event) => {
    event.preventDefault()
  })

  if (is.dev && process.env['ELECTRON_RENDERER_URL']) {
    mainWindow.loadURL(process.env['ELECTRON_RENDERER_URL'])
  } else {
    mainWindow.loadFile(join(__dirname, '../renderer/index.html'))
  }
}

app.whenReady().then(() => {
  electronApp.setAppUserModelId('com.wellnesscompanion.app')

  app.on('browser-window-created', (_, window) => {
    optimizer.watchWindowShortcuts(window)
  })

  initDatabase()
  registerDatabaseHandlers()
  registerLlmHandlers()
  registerSyncHandlers()
  startSyncServer()

  createWindow()

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow()
  })
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit()
  }
})

// Electron does not await async 'before-quit' listeners, so an async handler
// can be cut off mid-teardown — leaving the llama context undisposed and the
// SQLite WAL never checkpointed. Hold the quit, tear down, then exit.
let isQuitting = false
app.on('before-quit', (event) => {
  if (isQuitting) return
  event.preventDefault()
  isQuitting = true
  void (async () => {
    try {
      await stopSyncServer()
    } catch (err) {
      console.error('sync server shutdown failed:', err)
    }
    try {
      await disposeLlm()
    } catch (err) {
      console.error('LLM disposal failed:', err)
    }
    try {
      await waitForPendingBackup()
    } catch (err) {
      console.error('waiting for pending backup failed:', err)
    }
    try {
      closeDatabase()
    } catch (err) {
      console.error('database close failed:', err)
    }
    app.exit(0)
  })()
})
