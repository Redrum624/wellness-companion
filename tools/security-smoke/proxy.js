/**
 * A logging (and optionally tampering) MITM between the phone and the desktop.
 *
 *   phone -> ws://10.0.2.2:9848  [this proxy]  -> ws://127.0.0.1:9847 [desktop]
 *
 * Every frame is logged with its direction, WebSocket channel (TEXT/BINARY) and
 * size, which is the direct evidence for "no text frame after hs3" (spec §9.2)
 * and for "two sequential syncs use different pub_c" (§9.3).
 *
 * Modes (argv[2]):
 *   log                  pass everything through untouched
 *   tamper-c2s:<n>       flip one ciphertext byte in the n-th phone->PC record
 *   tamper-s2c:<n>       flip one ciphertext byte in the n-th PC->phone record
 *   hello-patch-file     shallow-merge hello_patch.json (next to this file)
 *                        into the desktop's hello before forwarding it
 *                        (active downgrade / negotiation tamper)
 *   hello-drop-crypto    delete `crypto` from hello and set version:3
 *
 * Requires `ws`, resolved from windows/node_modules — run `pnpm install` in
 * windows/ first if it isn't there. See README.md in this directory.
 */
const path = require('node:path')
const fs = require('node:fs')
const { WebSocketServer, WebSocket } = require(path.join(__dirname, '..', '..', 'windows', 'node_modules', 'ws'))

const MODE = process.argv[2] || 'log'
const LISTEN = Number(process.argv[3] || 9848)
const UPSTREAM = process.argv[4] || 'ws://127.0.0.1:9847'

let tamperDir = null
let tamperIndex = -1
if (MODE.startsWith('tamper-c2s:')) { tamperDir = 'c2s'; tamperIndex = Number(MODE.split(':')[1]) }
if (MODE.startsWith('tamper-s2c:')) { tamperDir = 's2c'; tamperIndex = Number(MODE.split(':')[1]) }
// Read the patch from a file: PowerShell strips the quotes out of an inline
// JSON argv, which silently turns the tamper into a no-op.
const helloPatch = MODE === 'hello-patch-file'
  ? JSON.parse(fs.readFileSync(path.join(__dirname, 'hello_patch.json'), 'utf8'))
  : null

const t0 = Date.now()
const log = (...a) => console.log(`[+${String(Date.now() - t0).padStart(6)}ms]`, ...a)

function describe(dir, isBinary, buf) {
  const chan = isBinary ? 'BINARY' : 'TEXT  '
  if (isBinary) {
    const tag = buf[0]
    const ctr = buf.length >= 9 ? buf.readBigUInt64BE(1) : '?'
    // The first 32 bytes are enough to compare two records that carry the SAME
    // plaintext under the SAME counter: identical bytes would mean an identical
    // (key, nonce) pair, i.e. the GCM catastrophe.
    return `${dir} ${chan} ${String(buf.length).padStart(6)}B  record tag=0x${tag.toString(16).padStart(2, '0')} counter=${ctr} head32=${buf.subarray(0, 32).toString('hex')}`
  }
  const s = buf.toString('utf8')
  let type = '?'
  try { type = JSON.parse(s).type } catch { /* not json */ }
  return `${dir} ${chan} ${String(buf.length).padStart(6)}B  type=${type}  ${s.length > 400 ? s.slice(0, 400) + '…' : s}`
}

const wss = new WebSocketServer({ host: '0.0.0.0', port: LISTEN })
log(`proxy mode=${MODE} listening on ${LISTEN}, upstream ${UPSTREAM}`)

let conn = 0
wss.on('connection', (down) => {
  const id = ++conn
  log(`=== connection #${id} from phone ===`)
  const queue = []
  let c2sRecords = 0
  let s2cRecords = 0
  let hs3Seen = false
  let textAfterHs3 = 0

  const up = new WebSocket(UPSTREAM)
  up.on('open', () => { queue.forEach((f) => up.send(f.data, { binary: f.isBinary })); queue.length = 0 })

  down.on('message', (data, isBinary) => {
    let buf = Buffer.isBuffer(data) ? data : Buffer.from(data)
    if (isBinary) {
      c2sRecords++
      if (tamperDir === 'c2s' && c2sRecords === tamperIndex) {
        buf = Buffer.from(buf)
        const at = 9 // first ciphertext byte, right after byte0||counter
        const before = buf[at]
        buf[at] ^= 0x01
        log(`### TAMPER c2s record #${c2sRecords}: ciphertext byte[${at}] 0x${before.toString(16)} -> 0x${buf[at].toString(16)}`)
      }
    } else if (hs3Seen) {
      textAfterHs3++
      log(`### VIOLATION: phone sent a TEXT frame after hs3`)
    }
    log(describe('phone->PC ', isBinary, buf))
    if (!isBinary) { try { if (JSON.parse(buf.toString('utf8')).type === 'hs3') hs3Seen = true } catch {} }
    if (up.readyState === WebSocket.OPEN) up.send(buf, { binary: isBinary })
    else queue.push({ data: buf, isBinary })
  })

  up.on('message', (data, isBinary) => {
    let buf = Buffer.isBuffer(data) ? data : Buffer.from(data)
    if (isBinary) {
      s2cRecords++
      if (tamperDir === 's2c' && s2cRecords === tamperIndex) {
        buf = Buffer.from(buf)
        const at = 9
        const before = buf[at]
        buf[at] ^= 0x01
        log(`### TAMPER s2c record #${s2cRecords}: ciphertext byte[${at}] 0x${before.toString(16)} -> 0x${buf[at].toString(16)}`)
      }
    } else if (hs3Seen) {
      textAfterHs3++
      log(`### VIOLATION: PC sent a TEXT frame after hs3`)
    }
    if (!isBinary && (helloPatch || MODE === 'hello-drop-crypto')) {
      try {
        const o = JSON.parse(buf.toString('utf8'))
        if (o.type === 'hello') {
          if (MODE === 'hello-drop-crypto') { delete o.crypto; o.version = 3; o.minVersion = 3 }
          else Object.assign(o, helloPatch)
          buf = Buffer.from(JSON.stringify(o), 'utf8')
          log(`### TAMPER hello -> ${buf.toString('utf8')}`)
        }
      } catch {}
    }
    log(describe('PC->phone ', isBinary, buf))
    if (down.readyState === WebSocket.OPEN) down.send(buf, { binary: isBinary })
  })

  down.on('close', (c, r) => {
    log(`phone closed: code=${c} reason=${r}`)
    log(`--- #${id} totals: c2s records=${c2sRecords} s2c records=${s2cRecords} textFramesAfterHs3=${textAfterHs3} ---`)
    try { up.close() } catch {}
  })
  up.on('close', (c, r) => {
    log(`PC closed: code=${c} reason=${r}`)
    log(`--- #${id} totals: c2s records=${c2sRecords} s2c records=${s2cRecords} textFramesAfterHs3=${textAfterHs3} ---`)
    // Forward the upstream close code verbatim so the phone sees exactly what
    // the desktop sent; 1005/1006 are "no code received" and cannot be re-sent.
    const passthrough = (c >= 3000 && c <= 4999) || (c >= 1000 && c <= 1013 && c !== 1004 && c !== 1005 && c !== 1006)
    try { down.close(passthrough ? c : 1011, String(r || '')) } catch { try { down.close() } catch {} }
  })
  up.on('error', (e) => log('upstream error:', e.message))
  down.on('error', (e) => log('downstream error:', e.message))
})
