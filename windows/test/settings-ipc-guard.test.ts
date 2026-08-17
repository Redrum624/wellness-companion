/**
 * db:getSetting / db:setSetting IPC guard (S-01, inverted to an allowlist for S-07).
 *
 * The `settings` table also stores sync credentials under `sync.*` keys
 * (DEVICE_KEYS_SETTING, PENDING_PAIRINGS_SETTING, PAIR_BACKOFF_SETTING) — raw
 * device pairing secrets and pairing state — and internal bookkeeping like
 * `app.last_run_version`, which gates the pre-update database snapshot. A
 * compromised renderer must not be able to read or write any of that through
 * the general-purpose db:getSetting / db:setSetting channel, nor any other
 * key that isn't one the renderer actually uses. This asserts both handlers
 * allow only the exact key shapes BadHabitsPage.tsx uses, and refuse
 * everything else — sync.* keys, and an arbitrary unknown key alike.
 */
jest.mock('electron', () => ({
  app: { getPath: (): string => '', getVersion: (): string => '0.0.0' },
  ipcMain: { handle: jest.fn(), removeHandler: jest.fn() }
}))
jest.mock('better-sqlite3-multiple-ciphers', () => ({
  __esModule: true,
  default: class {
    constructor() {
      throw new Error('the native SQLite binding must not be constructed in unit tests')
    }
  }
}))

import { ipcMain } from 'electron'

import * as DB from '../src/main/database'

const GUARD_MESSAGE = 'Access to this setting is not permitted via this channel'

type Handler = (event: unknown, ...args: unknown[]) => unknown

function registeredHandler(channel: string): Handler {
  const call = (ipcMain.handle as jest.Mock).mock.calls.find(([ch]) => ch === channel)
  if (!call) throw new Error(`handler for ${channel} was never registered`)
  return call[1] as Handler
}

beforeAll(() => {
  DB.registerDatabaseHandlers()
})

describe('db:getSetting / db:setSetting allowlist guard', () => {
  const legitimateKeys = [
    'badhabits:2026-08-17:alcohol:level',
    'badhabits:2026-08-17:weed:level'
  ]

  const refusedKeys = [
    DB.DEVICE_KEYS_SETTING,
    DB.PENDING_PAIRINGS_SETTING,
    DB.PAIR_BACKOFF_SETTING,
    'sync.anything',
    'app.last_run_version',
    'badhabits:2026-08-17:tobacco:level', // not a level-tracked substance
    'badhabits:2026-08-17:alcohol:count', // not the `level` field
    'badhabits::alcohol:level', // empty date segment -- the allowlist requires at least one char
    'badhabits:2026-08-17:alcohol:level:extra',
    'anything-else'
  ]

  test('db:getSetting allows every legitimate key', () => {
    const handler = registeredHandler('db:getSetting')
    for (const key of legitimateKeys) {
      // The native binding is stubbed out in this unit test, so a key that
      // clears the guard falls through to a *different* error (db is unset)
      // than the permission guard -- proving the guard did not fire for it.
      expect(() => handler(null, key)).not.toThrow(GUARD_MESSAGE)
    }
  })

  test('db:setSetting allows every legitimate key', () => {
    const handler = registeredHandler('db:setSetting')
    for (const key of legitimateKeys) {
      expect(() => handler(null, key, 'value')).not.toThrow(GUARD_MESSAGE)
    }
  })

  test('db:getSetting refuses every sync.* key', () => {
    const handler = registeredHandler('db:getSetting')
    for (const key of [DB.DEVICE_KEYS_SETTING, DB.PENDING_PAIRINGS_SETTING, DB.PAIR_BACKOFF_SETTING, 'sync.anything']) {
      expect(() => handler(null, key)).toThrow(GUARD_MESSAGE)
    }
  })

  test('db:setSetting refuses every sync.* key', () => {
    const handler = registeredHandler('db:setSetting')
    for (const key of [DB.DEVICE_KEYS_SETTING, DB.PENDING_PAIRINGS_SETTING, DB.PAIR_BACKOFF_SETTING, 'sync.anything']) {
      expect(() => handler(null, key, 'value')).toThrow(GUARD_MESSAGE)
    }
  })

  test('db:getSetting refuses an arbitrary unknown key (fails closed, not just sync.*)', () => {
    const handler = registeredHandler('db:getSetting')
    for (const key of refusedKeys) {
      expect(() => handler(null, key)).toThrow(GUARD_MESSAGE)
    }
  })

  test('db:setSetting refuses an arbitrary unknown key (fails closed, not just sync.*)', () => {
    const handler = registeredHandler('db:setSetting')
    for (const key of refusedKeys) {
      expect(() => handler(null, key, 'value')).toThrow(GUARD_MESSAGE)
    }
  })
})
