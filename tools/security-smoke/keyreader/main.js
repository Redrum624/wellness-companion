// Unwrap wellness.key via Electron safeStorage (same Windows user => DPAPI works),
// then open a COPY of the encrypted database and report counts.
const { app, safeStorage } = require('electron')
const fs = require('fs')
const path = require('path')
const Database = require(path.join(__dirname, '..', '..', '..', 'windows', 'node_modules', 'better-sqlite3-multiple-ciphers'))

// Read-only by default: this tool's README says to point it at a COPY of the
// live DB, but nothing enforced that -- an accidental `%APPDATA%` path plus a
// destructive SQL argument could otherwise mutate a real user's database.
// --allow-write is documented (README) as an extra, trailing argument, but is
// filtered out of argv here -- not read positionally -- so it works wherever
// it appears on the command line without shifting the positional key/db/sql/
// shadow-dir arguments that follow it.
const allowWrite = process.argv.includes('--allow-write')
const positional = process.argv.slice(2).filter((a) => a !== '--allow-write')

const keyPath = positional[0]
const dbPath = positional[1]
const sql = positional[2] || null

if (!allowWrite) console.log('opening read-only (pass --allow-write to allow mutating statements)')

// Chromium's OSCrypt on Windows keeps its DPAPI-wrapped key in <userData>/Local State,
// so the helper must run against a userData dir carrying the SAME Local State as the app.
const shadow = positional[3]
if (shadow) app.setPath('userData', shadow)
app.disableHardwareAcceleration()
app.whenReady().then(() => {
  try {
    const available = safeStorage.isEncryptionAvailable()
    console.log('safeStorage available:', available)
    const key = Buffer.from(safeStorage.decryptString(fs.readFileSync(keyPath)), 'base64')
    console.log('unwrapped key bytes:', key.length)
    const hdr = Buffer.alloc(16)
    const fd = fs.openSync(dbPath, 'r'); fs.readSync(fd, hdr, 0, 16, 0); fs.closeSync(fd)
    console.log('db header hex:', hdr.toString('hex'))
    console.log('db header ascii:', JSON.stringify(hdr.toString('latin1')))
    console.log('plaintext SQLite header:', hdr.toString('latin1').startsWith('SQLite format 3\0'))
    // Prove it is NOT readable without the key
    try {
      const bare = new Database(dbPath, { readonly: true })
      bare.prepare('SELECT count(*) FROM entries').get()
      console.log('UNKEYED READ: SUCCEEDED  <-- NOT ciphertext')
      bare.close()
    } catch (e) { console.log('unkeyed read rejected:', e.message.split('\n')[0]) }

    const db = new Database(dbPath, { readonly: !allowWrite })
    db.pragma("cipher='sqlcipher'"); db.pragma('legacy=4')
    db.key(key) // the library's own keying call, exactly as openDatabaseHandle() does
    for (const t of ['entries','hobbies','people','chore_templates','settings']) {
      try { console.log(`count(${t}) =`, db.prepare(`SELECT COUNT(*) c FROM ${t}`).get().c) }
      catch (e) { console.log(`count(${t}) ERR`, e.message) }
    }
    if (sql) {
      const stmt = db.prepare(sql)
      if (stmt.reader) console.log('QUERY:', JSON.stringify(stmt.all()))
      else console.log('EXEC:', JSON.stringify(stmt.run()))
    }
    db.close()
  } catch (err) {
    console.log('ERROR:', err && err.message)
  }
  app.quit()
})
