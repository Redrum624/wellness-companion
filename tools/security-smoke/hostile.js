/**
 * Hostile / scripted wc-sync peer. Independent implementation (see wcx.js) used
 * for the states that cannot be forced through a real UI: a v3 phone, plaintext
 * data injection, a wrong-secret device, and a well-behaved second device.
 *
 * usage: node hostile.js <mode> [args...]
 *   v3-auth [token]              text {type:'auth',token} the instant we connect
 *   v3-fullsync                  text {type:'full_sync',entries:[POISON]}
 *   v3-auth-then-fullsync        text auth, then text full_sync
 *   v3-push                      text {type:'push',entries:[POISON]}
 *   pair <code> <deviceId> [--badmac] [--push=<file.json>] [--label=X]
 *                                full x1 handshake; --badmac corrupts the
 *                                pairing secret so mac_c fails (present keyId,
 *                                wrong bytes); --push seals that file's JSON.
 *
 * Requires `ws`, resolved from windows/node_modules — run `pnpm install` in
 * windows/ first if it isn't there. See README.md in this directory.
 */
const path = require('node:path')
const { WebSocket } = require(path.join(__dirname, '..', '..', 'windows', 'node_modules', 'ws'))
const fs = require('node:fs')
const crypto = require('node:crypto')
const X = require('./wcx')

const MODE = process.argv[2]
const args = process.argv.slice(3)
const flag = (n) => args.find((a) => a.startsWith(`--${n}=`))?.split('=').slice(1).join('=')
const has = (n) => args.includes(`--${n}`)
const URL = flag('url') || 'ws://127.0.0.1:9847'

const t0 = Date.now()
const log = (...a) => console.log(`[+${String(Date.now() - t0).padStart(5)}ms]`, ...a)

/** Deliberately distinctive so any leak into the real database is unmistakable. */
const POISON_ID = flag('poison') || 'SMOKE-PLAINTEXT-INJECTION-0001'
const poisonEntry = {
  id: POISON_ID,
  category: 'water',
  timestamp: Date.now(),
  date: new Date().toISOString().slice(0, 10),
  data: JSON.stringify({ amount: 1, note: 'plaintext injection must never land' }),
  version: 1,
  modified_at: Date.now()
}

const ws = new WebSocket(URL)
const seen = []
let established = false
let kC2S = null
let kS2C = null
let ctrOut = 0
let ctrIn = 0
let ephemeral = null
let helloBytes = null
let hs1Bytes = null
let psk = null
let sentAfterHs3Text = 0

function sendText(obj) {
  const s = JSON.stringify(obj)
  if (established) sentAfterHs3Text++
  log(`-> TEXT   ${s.length}B ${s.slice(0, 300)}`)
  ws.send(s)
  return Buffer.from(s, 'utf8')
}

function sendSealed(obj) {
  const frame = X.sealRecord(kC2S, ctrOut, X.DIR_C2S, obj)
  ctrOut++
  log(`-> BINARY ${frame.length}B sealed type=${obj.type} counter=${ctrOut - 1}`)
  ws.send(frame, { binary: true })
}

ws.on('open', () => {
  log(`connected to ${URL}`)
  if (MODE === 'v3-auth') sendText({ type: 'auth', token: args[0] || 'DUMMYTOK' }) // Dummy token; the desktop tombstones any legacy auth frame without validating
  if (MODE === 'v3-fullsync') sendText({ type: 'full_sync', since: 0, entries: [poisonEntry] })
  if (MODE === 'v3-push') sendText({ type: 'push', entries: [poisonEntry] })
  if (MODE === 'v3-auth-then-fullsync') {
    sendText({ type: 'auth', token: args[0] || 'DUMMYTOK' }) // Dummy token; the desktop tombstones any legacy auth frame without validating
    sendText({ type: 'full_sync', since: 0, entries: [poisonEntry] })
  }
})

ws.on('message', (data, isBinary) => {
  const buf = Buffer.isBuffer(data) ? data : Buffer.from(data)
  if (isBinary) {
    log(`<- BINARY ${buf.length}B counter=${buf.length >= 9 ? buf.readBigUInt64BE(1) : '?'}`)
    seen.push({ chan: 'binary', len: buf.length })
    let msg
    try {
      msg = X.openRecord(kS2C, ctrIn, X.DIR_S2C, buf)
    } catch (e) {
      log(`!! record REJECTED: ${e.message}`)
      return
    }
    ctrIn++
    log(`   decrypted: type=${msg.type} ${JSON.stringify(msg).slice(0, 240)}`)
    if (msg.type === 'auth_ok') {
      const pushFile = flag('push')
      if (pushFile) sendSealed(JSON.parse(fs.readFileSync(pushFile, 'utf8')))
      else sendSealed({ type: 'full_sync', since: Number.MAX_SAFE_INTEGER, entries: [] })
    } else if (msg.type === 'full_sync_response') {
      log(`   full_sync_response: entries=${(msg.entries || []).length} received=${JSON.stringify(msg.received)}`)
      log('RESULT: SYNC_OK')
      ws.close(1000, 'done')
    }
    return
  }

  const text = buf.toString('utf8')
  log(`<- TEXT   ${buf.length}B ${text.slice(0, 400)}`)
  seen.push({ chan: 'text', len: buf.length, text })
  const msg = JSON.parse(text)

  if (msg.type === 'hello' && MODE === 'pair') {
    const code = args[0]
    const deviceId = args[1]
    const parts = X.decodePairingCode(code)
    let secret = parts.secret
    if (has('badmac')) {
      // Present keyId, WRONG secret bytes: the ops-F2 "bricking" case.
      secret = Buffer.from(secret)
      secret[0] ^= 0xff
      log('   using a DELIBERATELY WRONG secret (first byte flipped)')
    }
    psk = X.pskFromSecret(secret)
    ephemeral = X.generateEphemeral()
    helloBytes = buf
    hs1Bytes = sendText({
      type: 'hs1',
      proto: X.CRYPTO,
      keyId: parts.keyId,
      deviceId,
      pub_c: ephemeral.publicSpkiB64,
      nonce_c: crypto.randomBytes(32).toString('base64'),
      deviceName: flag('label') || 'SmokeTestPeer'
    })
    log(`   keyId=${parts.keyId} pub_c=${ephemeral.publicSpkiB64.slice(0, 40)}…`)
    return
  }

  if (msg.type === 'hs2') {
    const ss = X.ecdhSharedSecret(ephemeral.priv, msg.pub_s)
    const th = X.transcriptHash(helloBytes, hs1Bytes, Buffer.from(msg.pub_s, 'ascii'))
    const keys = X.deriveSession(ss, psk, th)
    const expected = X.macTag(keys.km, 'srv', th)
    const presented = Buffer.from(msg.mac_s, 'base64')
    const ok = expected.length === presented.length && crypto.timingSafeEqual(expected, presented)
    log(`   mac_s verify: ${ok ? 'OK' : 'FAILED'}`)
    log(`   th=${th.toString('hex')}`)
    log(`   k_c2s=${keys.kC2S.toString('hex').slice(0, 16)}… k_s2c=${keys.kS2C.toString('hex').slice(0, 16)}…`)
    if (!ok && !has('badmac')) {
      log('RESULT: MAC_S_VERIFY_FAILED — aborting, nothing sensitive sent')
      return ws.close(1002, 'server authentication failed')
    }
    sendText({ type: 'hs3', mac_c: X.macTag(keys.km, 'cli', th).toString('base64') })
    kC2S = keys.kC2S
    kS2C = keys.kS2C
    established = true
    log('   channel established (hs3 sent) — every later frame must be BINARY')
    return
  }

  if (msg.type === 'error') log(`   server error code=${msg.code} retryAfterMs=${msg.retryAfterMs ?? '-'}`)
})

ws.on('close', (code, reason) => {
  log(`closed: code=${code} reason=${String(reason)}`)
  log(`SUMMARY frames_from_server=${JSON.stringify(seen.map((s) => s.chan))}`)
  log(`SUMMARY text_frames_sent_after_hs3=${sentAfterHs3Text}`)
  log(`RESULT_CLOSE_CODE=${code}`)
  process.exit(0)
})
ws.on('error', (e) => { log('socket error:', e.message); process.exit(1) })
setTimeout(() => { log('TIMEOUT'); process.exit(2) }, 30000)
