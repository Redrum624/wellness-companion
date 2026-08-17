/**
 * db:getSetting / db:setSetting IPC guard (S-01).
 *
 * The `settings` table also stores sync credentials under `sync.*` keys
 * (DEVICE_KEYS_SETTING, PENDING_PAIRINGS_SETTING, PAIR_BACKOFF_SETTING) — raw
 * device pairing secrets and pairing state. A compromised renderer must not
 * be able to read or write those through the general-purpose db:getSetting /
 * db:setSetting channel. This asserts both handlers reject every `sync.`
 * prefixed key and still let ordinary keys (e.g. bad-habit tracker settings)
 * reach the database layer.
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

const GUARD_MESSAGE = 'Access to sync settings is not permitted via this channel'

type Handler = (event: unknown, ...args: unknown[]) => unknown

function registeredHandler(channel: string): Handler {
  const call = (ipcMain.handle as jest.Mock).mock.calls.find(([ch]) => ch === channel)
  if (!call) throw new Error(`handler for ${channel} was never registered`)
  return call[1] as Handler
}

beforeAll(() => {
  DB.registerDatabaseHandlers()
})

describe('db:getSetting / db:setSetting sync.* guard', () => {
  const syncKeys = [DB.DEVICE_KEYS_SETTING, DB.PENDING_PAIRINGS_SETTING, DB.PAIR_BACKOFF_SETTING, 'sync.anything']

  test('db:getSetting refuses every sync.* key', () => {
    const handler = registeredHandler('db:getSetting')
    for (const key of syncKeys) {
      expect(() => handler(null, key)).toThrow(GUARD_MESSAGE)
    }
  })

  test('db:setSetting refuses every sync.* key', () => {
    const handler = registeredHandler('db:setSetting')
    for (const key of syncKeys) {
      expect(() => handler(null, key, 'value')).toThrow(GUARD_MESSAGE)
    }
  })

  test('db:getSetting still lets an ordinary key reach the database layer', () => {
    const handler = registeredHandler('db:getSetting')
    // A non-sync key clears the guard and falls through to db.prepare(...).
    // The native binding is stubbed out in this unit test, so that throws a
    // *different* error (db is unset) than the permission guard -- proving
    // the guard did not fire for this key.
    expect(() => handler(null, 'badhabits:2026-08-17:alcohol:level')).not.toThrow(GUARD_MESSAGE)
  })

  test('db:setSetting still lets an ordinary key reach the database layer', () => {
    const handler = registeredHandler('db:setSetting')
    expect(() => handler(null, 'badhabits:2026-08-17:alcohol:level', '2')).not.toThrow(GUARD_MESSAGE)
  })
})
