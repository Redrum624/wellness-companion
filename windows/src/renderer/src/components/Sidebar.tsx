import { useState, useEffect, useRef } from 'react'
import type { CSSProperties } from 'react'
import { NavLink } from 'react-router-dom'
import { getCategoryByKey } from '../lib/categories'
import { categoryColors, accentColors } from '../styles/theme'

interface PairedDevice {
  deviceId: string
  keyId: string
  label: string
  lastSeen: number
}

declare global {
  interface Window {
    sync: {
      getStatus: () => Promise<string>
      getPort: () => Promise<number>
      getLocalIp: () => Promise<string>
      createPairing: () => Promise<{ keyId: string; code: string; expiresAt: number }>
      listDevices: () => Promise<PairedDevice[]>
      removeDevice: (deviceId: string) => Promise<boolean>
      regeneratePairingToken: () => Promise<void>
      onStatusChange: (
        callback: (info: { status: string; detail?: string; code?: string }) => void
      ) => () => void
    }
  }
}

const navItems = [
  { path: '/', label: 'Dashboard', icon: '🏠' },
  { path: '/water', label: 'Water', icon: '💧' },
  { path: '/food', label: 'Food', icon: '🥪' },
  { path: '/bathroom', label: 'Bathroom', icon: '🚽' },
  { path: '/health', label: 'Health', icon: '💚' },
  { path: '/sleep', label: 'Sleep', icon: '🌙' },
  { path: '/emotions', label: 'Emotions', icon: '🌻' },
  { path: '/interactions', label: 'Journal', icon: '💬' },
  { path: '/chores', label: 'Chores', icon: '✅' },
  { path: '/hobbies', label: 'Hobbies', icon: '🎨' },
  { path: '/ideas', label: 'Ideas', icon: '💡' },
  { path: '/cycle', label: 'Cycle', icon: '🩸' },
  { path: '/badhabits', label: 'Bad Habits', icon: '⚠️' },
  { path: '/insights', label: 'Insights', icon: '🧠' }
]

function lastSeenLabel(ts: number): string {
  if (!ts) return 'never'
  const mins = Math.floor((Date.now() - ts) / 60_000)
  if (mins < 1) return 'just now'
  if (mins < 60) return `${mins}m ago`
  const hours = Math.floor(mins / 60)
  if (hours < 24) return `${hours}h ago`
  return `${Math.floor(hours / 24)}d ago`
}

export default function Sidebar() {
  const [syncInfo, setSyncInfo] = useState('')
  const [syncDetail, setSyncDetail] = useState('')
  // A pairing is minted on demand and shown once — it is a one-time code, not a
  // standing one, so there is nothing to display until asked. Only the code is
  // held: it already carries the keyId, and the user must never be asked to
  // transcribe a second string.
  const [pairing, setPairing] = useState<string | null>(null)
  const [devices, setDevices] = useState<PairedDevice[]>([])
  // Tracks the paired-device count outside React state so the `connected`
  // handler below (registered once in the effect with deps []) can compare
  // against the CURRENT count instead of the stale value captured when the
  // closure was created.
  const deviceCountRef = useRef(0)
  // Mirrors the effect's local `alive` flag so callbacks fired from outside
  // the effect (e.g. pairDevice, below) can also skip work after unmount.
  const mountedRef = useRef(true)
  // The pairing code IS the phone's long-term secret once bound, and the
  // sidebar stays mounted all session — so it must not linger on screen
  // past the moment it stops being useful. Cleared on a successful pairing
  // (below) and by this expiry timer (mirrors the server-side PAIRING_TTL_MS
  // the `expiresAt` IPC field already carries), whichever comes first.
  const pairingTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  const clearPairingTimer = (): void => {
    if (pairingTimer.current) {
      clearTimeout(pairingTimer.current)
      pairingTimer.current = null
    }
  }
  // Sticky, unlike syncDetail: a version mismatch stays on screen until either
  // a sync actually succeeds (proof the phone was updated) or the app
  // restarts — a status line that keeps getting overwritten by the next
  // "listening"/"pairing" broadcast would flash past before anyone reads it.
  const [versionMismatch, setVersionMismatch] = useState(false)
  // Two-step so a stray click can't nuke every paired phone at once.
  const [confirmingForgetAll, setConfirmingForgetAll] = useState(false)

  const refreshDevices = (): void => {
    window.sync.listDevices().then((list) => {
      deviceCountRef.current = list.length
      setDevices(list)
    })
  }

  useEffect(() => {
    // `alive` guards against the promise resolving after unmount.
    let alive = true
    Promise.all([window.sync.getLocalIp(), window.sync.getPort()]).then(([ip, port]) => {
      if (!alive) return
      setSyncInfo(`${ip}:${port}`)
    })
    window.sync.listDevices().then((list) => {
      if (!alive) return
      deviceCountRef.current = list.length
      setDevices(list)
    })

    const unsub = window.sync.onStatusChange((info) => {
      if (!alive) return
      setSyncDetail(info.detail || info.status)
      // Machine-readable signal, not a prose match: sync-server.ts sends
      // code: 'update_required' from both 4005 close sites (the v3 tombstone
      // in tombstoneLegacyFrame and the proto-mismatch branch in handleHs1),
      // so a copy edit to `detail` can never silently break this banner.
      if (info.code === 'update_required') {
        setVersionMismatch(true)
      } else if (info.status === 'connected' || info.status === 'synced') {
        setVersionMismatch(false)
      }
      // A completed handshake updates lastSeen (and can bind a new device) —
      // but the server emits `connected` for every completed handshake,
      // including an already-paired phone reconnecting to sync. Clearing the
      // pairing code on that broadcast would wipe a fresh code still on
      // screen before the user got to use it, so only clear it when a NEW
      // device actually bound (the device list grew).
      if (info.status === 'connected') {
        window.sync.listDevices().then((list) => {
          if (!alive) return
          if (list.length > deviceCountRef.current) {
            // Pairing succeeded — the code has done its job. Clear it rather
            // than let it sit on screen for the rest of the session.
            clearPairingTimer()
            setPairing(null)
          }
          deviceCountRef.current = list.length
          setDevices(list)
        })
      }
    })
    return () => {
      alive = false
      mountedRef.current = false
      unsub()
      clearPairingTimer()
    }
  }, [])

  const pairDevice = (): void => {
    window.sync.createPairing().then(({ code, expiresAt }) => {
      if (!mountedRef.current) return
      setPairing(code)
      clearPairingTimer()
      pairingTimer.current = setTimeout(() => setPairing(null), Math.max(0, expiresAt - Date.now()))
    })
  }

  const removeDevice = (deviceId: string): void => {
    window.sync.removeDevice(deviceId).then(refreshDevices)
  }

  const forgetAllDevices = (): void => {
    if (!confirmingForgetAll) {
      setConfirmingForgetAll(true)
      return
    }
    window.sync.regeneratePairingToken().then(() => {
      setConfirmingForgetAll(false)
      refreshDevices()
    })
  }

  return (
    <nav className="sidebar">
      <div className="sidebar-header" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        {/* Vite copies src/renderer/public/ to the output ROOT, so the runtime
            path is ./favicon.png — "./public/favicon.png" only exists in source
            and rendered as a broken image in the built app. */}
        <img src="./favicon.png" alt="" style={{ width: 22, height: 22 }} />
        Wellness
      </div>
      {navItems.map((item) => {
        // Most nav items map 1:1 to a data category and borrow its color for the
        // active accent bar; items with no category (Dashboard, Insights) fall
        // back to the app's existing "active/ready" accent (teal).
        const category = item.path === '/' ? undefined : getCategoryByKey(item.path.slice(1))
        const accent = category ? categoryColors[category.key].text : accentColors.teal
        return (
          <NavLink
            key={item.path}
            to={item.path}
            className={({ isActive }) => `sidebar-item ${isActive ? 'active' : ''}`}
            end={item.path === '/'}
            style={{ '--accent': accent } as CSSProperties}
          >
            <span className="sidebar-icon">{item.icon}</span>
            <span className="sidebar-label">{item.label}</span>
          </NavLink>
        )
      })}

      {/* Sync status at bottom */}
      <div style={{ marginTop: 'auto', padding: '12px 12px 4px' }}>
        {/* Persistent — stays up until a real sync succeeds, unlike syncDetail
            below which gets overwritten by whatever the server broadcasts next. */}
        {versionMismatch && (
          <div
            role="alert"
            style={{
              fontSize: 11, fontWeight: 600, color: '#7A1F1F',
              background: '#F8D7DA', border: '1px solid #E4A5AA',
              borderRadius: 8, padding: '8px 10px', marginBottom: 8,
              lineHeight: 1.4
            }}
          >
            Your phone app is older than this PC app. Open "Install the phone app" from the Start
            Menu to update it.
          </div>
        )}
        <div style={{ fontSize: 10, color: '#3D326260', marginBottom: 2 }}>Phone sync</div>
        <div style={{
          fontSize: 12, fontWeight: 600, color: '#3D3262',
          background: 'rgba(255,255,255,0.35)', borderRadius: 8,
          padding: '6px 10px', userSelect: 'text', cursor: 'text',
          fontFamily: 'monospace', letterSpacing: '-0.3px'
        }}>
          {syncInfo || '...'}
        </div>
        {/* One 128-bit secret per phone, typed once. Nothing syncs without it. */}
        <button
          onClick={pairDevice}
          title="Show a one-time code to enter on a new phone"
          style={{
            width: '100%', marginTop: 8, padding: '6px 10px',
            fontSize: 11, fontWeight: 600, color: '#3D3262',
            background: 'rgba(255,255,255,0.45)', border: 'none',
            borderRadius: 8, cursor: 'pointer'
          }}
        >
          Pair a device
        </button>
        {pairing && (
          <div style={{ marginTop: 6 }}>
            <div style={{ fontSize: 10, color: '#3D326260', marginBottom: 2 }}>
              Type this on the phone (valid 5 min)
            </div>
            {/* Rendered group-by-group so it wraps at the dashes instead of
                mid-group — this is read aloud and typed on a phone. */}
            <div
              title="Enter this code on your phone once; it becomes that phone's key"
              style={{
                display: 'flex', flexWrap: 'wrap', gap: '2px 6px', justifyContent: 'center',
                fontSize: 12, fontWeight: 700, color: '#3D3262',
                background: 'rgba(255,255,255,0.35)', borderRadius: 8,
                padding: '6px 8px', userSelect: 'text', cursor: 'text',
                fontFamily: 'monospace', letterSpacing: '0.5px'
              }}
            >
              {pairing.split('-').map((group, i) => (
                <span key={i}>{group}</span>
              ))}
            </div>
            <div style={{ fontSize: 9, color: '#3D326260', marginTop: 3, lineHeight: 1.4 }}>
              This code is a secret — keep it off-screen from anyone nearby. It disappears once
              your phone pairs.
            </div>
          </div>
        )}

        {/* Per-device revoke: removing one phone leaves the others paired. */}
        <div style={{ fontSize: 10, color: '#3D326260', margin: '8px 0 2px' }}>Paired devices</div>
        {devices.length === 0 ? (
          <div style={{ fontSize: 10, color: '#3D326250' }}>None yet</div>
        ) : (
          devices.map((d) => (
            <div
              key={d.deviceId}
              style={{
                display: 'flex', alignItems: 'center', gap: 6,
                fontSize: 11, color: '#3D3262', padding: '3px 0'
              }}
            >
              <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis' }}>
                {d.label}
                <span style={{ color: '#3D326250' }}> · {lastSeenLabel(d.lastSeen)}</span>
              </span>
              <button
                onClick={() => removeDevice(d.deviceId)}
                title={`Stop syncing with ${d.label}`}
                style={{
                  fontSize: 10, color: '#3D3262', background: 'transparent',
                  border: 'none', cursor: 'pointer', padding: 0
                }}
              >
                Remove
              </button>
            </div>
          ))
        )}
        {/* Last resort: wipes every paired device at once (regeneratePairingToken).
            Per-device Remove above is the normal path; this is for "I think my
            pairing secrets are compromised" — hence the destructive styling and
            the two-click confirmation, so a stray click can't strand every phone. */}
        {devices.length > 0 && (
          <div style={{ marginTop: 6 }}>
            {confirmingForgetAll ? (
              <div>
                {/* Visible, not just a tooltip — a destructive control's
                    consequence must be readable without hovering. */}
                <div style={{ fontSize: 10, color: '#B3261E', marginBottom: 4, lineHeight: 1.4 }}>
                  This cannot be undone. Every phone will need to re-pair before it can sync again.
                </div>
                <div style={{ display: 'flex', gap: 6 }}>
                  <button
                    onClick={forgetAllDevices}
                    title="This cannot be undone — every phone will need to re-pair"
                    style={{
                      flex: 1, padding: '6px 10px', fontSize: 10, fontWeight: 700,
                      color: '#fff', background: '#B3261E', border: 'none',
                      borderRadius: 8, cursor: 'pointer'
                    }}
                  >
                    Confirm: forget all {devices.length} device{devices.length === 1 ? '' : 's'}
                  </button>
                  <button
                    onClick={() => setConfirmingForgetAll(false)}
                    style={{
                      padding: '6px 10px', fontSize: 10, fontWeight: 600,
                      color: '#3D3262', background: 'rgba(255,255,255,0.45)',
                      border: 'none', borderRadius: 8, cursor: 'pointer'
                    }}
                  >
                    Cancel
                  </button>
                </div>
              </div>
            ) : (
              <button
                onClick={forgetAllDevices}
                title="Forget every paired device — each phone must re-pair to sync again"
                style={{
                  width: '100%', padding: '6px 10px', fontSize: 10, fontWeight: 600,
                  color: '#B3261E', background: 'transparent',
                  border: '1px solid #B3261E60', borderRadius: 8, cursor: 'pointer'
                }}
              >
                Forget all devices
              </button>
            )}
          </div>
        )}
        {syncDetail && (
          <div style={{ fontSize: 9, color: '#3D326250', marginTop: 3 }}>{syncDetail}</div>
        )}
      </div>
    </nav>
  )
}
