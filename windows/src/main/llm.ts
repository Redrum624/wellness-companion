import { ipcMain, BrowserWindow, app } from 'electron'
import { join, dirname } from 'path'
import { existsSync } from 'fs'

let llmModule: any = null
let llama: any = null
let model: any = null
let context: any = null
let session: any = null
let sequence: any = null
let status: 'idle' | 'loading' | 'ready' | 'error' | 'generating' = 'idle'
let lastError: string = ''
let chatLock: Promise<any> = Promise.resolve()

const MODEL_FILE = 'Qwen3-4B-Instruct-2507-GGUF/Qwen3-4B-Instruct-2507-Q4_K_M.gguf'

function getModelPath(): string {
  // Check multiple candidate locations (AppData first — survives upgrades)
  const candidates = [
    // Primary: %LOCALAPPDATA%\wellness-companion\model\ — where the installer
    // (installer\setup_model.ps1) puts it. Deliberately NOT userData: that
    // resolves to %APPDATA% (Roaming) on Windows, and a 2.5 GB model must not
    // live in a roaming profile.
    join(process.env.LOCALAPPDATA || '', 'wellness-companion', 'model', MODEL_FILE),
    // Legacy: the Roaming userData dir, for installs made before the move.
    join(app.getPath('userData'), 'model', MODEL_FILE),
    // Legacy: resources/model/ (old installs before AppData migration)
    join(process.resourcesPath, 'model', MODEL_FILE),
    // Dev: model/ at project root (sibling of windows/)
    join(app.getAppPath(), '..', 'model', MODEL_FILE),
    // Portable: model/ next to the exe
    join(dirname(app.getPath('exe')), 'model', MODEL_FILE),
  ]

  for (const p of candidates) {
    if (existsSync(p)) {
      console.log('Found model at:', p)
      return p
    }
  }

  // Log all searched paths for debugging
  console.error('Model not found. Searched:')
  candidates.forEach(p => console.error('  ', p))
  return candidates[0]
}

function broadcastStatus(newStatus: typeof status, errorDetail?: string): void {
  status = newStatus
  if (errorDetail) lastError = errorDetail
  BrowserWindow.getAllWindows().forEach(win => {
    win.webContents.send('llm:status', status)
  })
}

async function loadModel(): Promise<void> {
  // Both must be present. Guarding on `model` alone meant that if
  // createContext() below threw, `model` stayed assigned with `context` null —
  // every retry short-circuited here and chat() failed on a null context
  // forever, with the multi-GB allocation still resident.
  if (model && context) return
  broadcastStatus('loading')
  try {
    const modelPath = getModelPath()
    if (!existsSync(modelPath)) {
      const msg = `Model file not found: ${modelPath}`
      console.error('LLM:', msg)
      broadcastStatus('error', msg)
      throw new Error(msg)
    }

    llmModule = await import('node-llama-cpp')
    console.log('LLM: node-llama-cpp imported successfully')

    // Try CUDA first, fall back to CPU
    try {
      llama = await llmModule.getLlama({ gpu: 'cuda' })
      console.log('LLM: using CUDA GPU')
    } catch (gpuErr: any) {
      console.log('LLM: CUDA not available, falling back to CPU. Reason:', gpuErr?.message || gpuErr)
      try {
        llama = await llmModule.getLlama({ gpu: false })
        console.log('LLM: using CPU mode')
      } catch (cpuErr: any) {
        const msg = `Failed to initialize LLM runtime (both CUDA and CPU failed).\nCPU error: ${cpuErr?.message || cpuErr}\n\nThis usually means the Visual C++ Redistributable is not installed.\nDownload it from: https://aka.ms/vs/17/release/vc_redist.x64.exe`
        console.error('LLM:', msg)
        broadcastStatus('error', msg)
        throw new Error(msg)
      }
    }

    console.log('LLM: loading model from', modelPath)
    model = await llama.loadModel({ modelPath })
    context = await model.createContext({ contextSize: 4096 })
    broadcastStatus('ready')
  } catch (err: any) {
    const msg = err?.message || String(err)
    console.error('Failed to load LLM:', msg)
    // Release whatever was allocated before the failure so the next attempt
    // starts clean instead of inheriting a half-initialised runtime.
    await releaseRuntime()
    if (status !== 'error') broadcastStatus('error', msg)
    throw err
  }
}

/**
 * Dispose every native handle we may hold, each independently: a throw in one
 * disposal must not skip the rest (the model is by far the largest allocation
 * and used to be stranded when the context failed to dispose).
 */
async function releaseRuntime(): Promise<void> {
  try { session?.dispose?.() } catch (e) { console.error('session dispose:', e) }
  session = null
  try { sequence?.dispose?.() } catch (e) { console.error('sequence dispose:', e) }
  sequence = null
  try { await context?.dispose?.() } catch (e) { console.error('context dispose:', e) }
  context = null
  try { await model?.dispose?.() } catch (e) { console.error('model dispose:', e) }
  model = null
  try { await llama?.dispose?.() } catch (e) { console.error('llama dispose:', e) }
  llama = null
}

async function chat(prompt: string): Promise<string> {
  // Serialize chat calls — wait for any in-flight request to finish
  const prev = chatLock
  let resolve: () => void
  chatLock = new Promise<void>(r => { resolve = r })

  try {
    await prev
  } catch {
    // Previous call errored — that's fine, we proceed
  }

  try {
    if (!model || !context) {
      await loadModel()
    }
    broadcastStatus('generating')

    // Create a fresh sequence + session each time. The previous pair is
    // released in the finally below rather than here, so nothing is held after
    // the last generation finishes.
    sequence = context.getSequence()
    session = new llmModule.LlamaChatSession({ contextSequence: sequence })
    let fullResponse = ''

    const response = await session.prompt(prompt, {
      onTextChunk(text: string) {
        fullResponse += text
        BrowserWindow.getAllWindows().forEach(win => {
          win.webContents.send('llm:token', text)
        })
      }
    })

    broadcastStatus('ready')
    return response || fullResponse
  } catch (err: any) {
    const msg = err?.message || String(err)
    console.error('LLM chat error:', msg)
    broadcastStatus('ready')
    throw new Error(msg)
  } finally {
    // Release the session and its 4096-token KV cache as soon as the
    // generation ends, on success or failure. These were previously freed only
    // at the start of the NEXT chat, so one session stayed resident for the
    // rest of the app's life once the user stopped asking questions.
    try { session?.dispose?.() } catch (e) { console.error('session dispose:', e) }
    session = null
    try { sequence?.dispose?.() } catch (e) { console.error('sequence dispose:', e) }
    sequence = null
    resolve!()
  }
}

export async function disposeLlm(): Promise<void> {
  await releaseRuntime()
  status = 'idle'
}

export function registerLlmHandlers(): void {
  ipcMain.handle('llm:getStatus', () => status)
  ipcMain.handle('llm:getError', () => lastError)

  ipcMain.handle('llm:chat', async (_e, message: string) => {
    return chat(message)
  })

  ipcMain.handle('llm:loadModel', async () => {
    await loadModel()
  })
}
