/**
 * A hostile "desktop" on the LAN. It speaks a well-formed v4 `hello` so the
 * phone will talk to it, then forges the pre-channel plaintext error the
 * protocol-F3 / ops-F1 finding is about. Nothing here can authenticate — the
 * point is that an UNAUTHENTICATED frame must never destroy phone trust state.
 *
 * usage: node fakedesktop.js <mode> [port]
 *   unknown_device   forge {type:'error',code:'unknown_device'} after hs1
 *   pairing_changed  forge {type:'error',code:'pairing_changed'} after hs1
 *   v3               answer with a v3 {type:'auth_required'} and nothing else
 *
 * Requires `ws`, resolved from windows/node_modules — run `pnpm install` in
 * windows/ first if it isn't there. See README.md in this directory.
 */
const path = require('node:path')
const { WebSocketServer } = require(path.join(__dirname, '..', '..', 'windows', 'node_modules', 'ws'))
const crypto = require('node:crypto')

const MODE = process.argv[2] || 'unknown_device'
const PORT = Number(process.argv[3] || 9848)
const t0 = Date.now()
const log = (...a) => console.log(`[+${String(Date.now() - t0).padStart(6)}ms]`, ...a)

const wss = new WebSocketServer({ host: '0.0.0.0', port: PORT })
log(`fake desktop mode=${MODE} listening on ${PORT}`)

wss.on('connection', (ws) => {
  log('=== phone connected ===')
  let framesFromPhone = 0
  let binaryFromPhone = 0

  if (MODE === 'v3') {
    const s = JSON.stringify({ type: 'auth_required' })
    log(`-> TEXT ${s}`)
    ws.send(s)
  } else {
    const s = JSON.stringify({
      type: 'hello',
      version: 4,
      minVersion: 4,
      crypto: ['x1'],
      nonce_s: crypto.randomBytes(32).toString('base64')
    })
    log(`-> TEXT ${s}`)
    ws.send(s)
  }

  ws.on('message', (data, isBinary) => {
    framesFromPhone++
    if (isBinary) binaryFromPhone++
    const buf = Buffer.isBuffer(data) ? data : Buffer.from(data)
    log(`<- ${isBinary ? 'BINARY' : 'TEXT  '} ${buf.length}B ${isBinary ? buf.subarray(0, 16).toString('hex') : buf.toString('utf8').slice(0, 320)}`)
    if (!isBinary && MODE !== 'v3') {
      let type = ''
      try { type = JSON.parse(buf.toString('utf8')).type } catch {}
      if (type === 'hs1') {
        // The forged frame: unauthenticated, unsigned, sendable by ANYONE on
        // the LAN. It must not cost the phone its device key.
        const s = JSON.stringify({ type: 'error', code: MODE })
        log(`-> TEXT (FORGED, unauthenticated) ${s}`)
        ws.send(s)
        setTimeout(() => ws.close(1000, MODE), 120)
      }
    }
  })

  ws.on('close', (c, r) => {
    log(`phone closed: code=${c} reason=${r}`)
    log(`SUMMARY frames_from_phone=${framesFromPhone} binary_frames_from_phone=${binaryFromPhone}`)
  })
})
