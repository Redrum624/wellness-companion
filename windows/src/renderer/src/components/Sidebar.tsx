import { useState, useEffect } from 'react'
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
      onStatusChange: (callback: (info: { status: string; detail?: string }) => void) => () => void
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
  // A pairing is minted on demand and shown once — it is a one-time 128-bit
  // secret, not a standing code, so there is nothing to display until asked.
  const [pairing, setPairing] = useState<{ keyId: string; code: string } | null>(null)
  const [devices, setDevices] = useState<PairedDevice[]>([])

  const refreshDevices = (): void => {
    window.sync.listDevices().then(setDevices)
  }

  useEffect(() => {
    // `alive` guards against the promise resolving after unmount.
    let alive = true
    Promise.all([window.sync.getLocalIp(), window.sync.getPort()]).then(([ip, port]) => {
      if (!alive) return
      setSyncInfo(`${ip}:${port}`)
    })
    window.sync.listDevices().then((list) => {
      if (alive) setDevices(list)
    })

    const unsub = window.sync.onStatusChange((info) => {
      if (!alive) return
      setSyncDetail(info.detail || info.status)
      // A completed handshake updates lastSeen (and can bind a new device).
      if (info.status === 'connected') refreshDevices()
    })
    return () => {
      alive = false
      unsub()
    }
  }, [])

  const pairDevice = (): void => {
    window.sync.createPairing().then(({ keyId, code }) => setPairing({ keyId, code }))
  }

  const removeDevice = (deviceId: string): void => {
    window.sync.removeDevice(deviceId).then(refreshDevices)
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
            <div
              title="Enter this code on your phone once; it becomes that phone's key"
              style={{
                fontSize: 12, fontWeight: 700, color: '#3D3262',
                background: 'rgba(255,255,255,0.35)', borderRadius: 8,
                padding: '6px 8px', userSelect: 'text', cursor: 'text',
                fontFamily: 'monospace', letterSpacing: '0.5px',
                textAlign: 'center', wordBreak: 'break-all'
              }}
            >
              {pairing.code}
            </div>
            <div style={{ fontSize: 9, color: '#3D326250', marginTop: 2, wordBreak: 'break-all' }}>
              key {pairing.keyId}
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
        {syncDetail && (
          <div style={{ fontSize: 9, color: '#3D326250', marginTop: 3 }}>{syncDetail}</div>
        )}
      </div>
    </nav>
  )
}
