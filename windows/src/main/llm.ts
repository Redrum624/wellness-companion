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
    // Primary: AppData/Local (survives app upgrades)
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
  if (model) return
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
    if (status !== 'error') broadcastStatus('error', msg)
    throw err
  }
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

    // Dispose previous session AND sequence to free the slot
    if (session) {
      try { session.dispose?.() } catch {}
      session = null
    }
    if (sequence) {
      try { sequence.dispose?.() } catch {}
      sequence = null
    }

    // Create a fresh sequence + session each time
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
    resolve!()
  }
}

export async function disposeLlm(): Promise<void> {
  try {
    if (session) { session.dispose?.(); session = null }
    if (sequence) { sequence.dispose?.(); sequence = null }
    if (context) { await context.dispose?.(); context = null }
    if (model) { await model.dispose?.(); model = null }
    if (llama) { await llama.dispose?.(); llama = null }
    status = 'idle'
  } catch (err) {
    console.error('Error disposing LLM:', err)
  }
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
