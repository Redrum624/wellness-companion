import { useState, useEffect } from 'react'
import { NavLink } from 'react-router-dom'

declare global {
  interface Window {
    sync: {
      getStatus: () => Promise<string>
      getPort: () => Promise<number>
      getLocalIp: () => Promise<string>
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

export default function Sidebar() {
  const [syncInfo, setSyncInfo] = useState('')
  const [syncDetail, setSyncDetail] = useState('')

  useEffect(() => {
    Promise.all([
      window.sync.getLocalIp(),
      window.sync.getPort()
    ]).then(([ip, port]) => {
      setSyncInfo(`${ip}:${port}`)
    })

    const unsub = window.sync.onStatusChange((info) => {
      setSyncDetail(info.detail || info.status)
    })
    return unsub
  }, [])

  return (
    <nav className="sidebar">
      <div className="sidebar-header" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        {/* Vite copies src/renderer/public/ to the output ROOT, so the runtime
            path is ./favicon.png — "./public/favicon.png" only exists in source
            and rendered as a broken image in the built app. */}
        <img src="./favicon.png" alt="" style={{ width: 22, height: 22 }} />
        Wellness
      </div>
      {navItems.map((item) => (
        <NavLink
          key={item.path}
          to={item.path}
          className={({ isActive }) => `sidebar-item ${isActive ? 'active' : ''}`}
          end={item.path === '/'}
        >
          <span className="sidebar-icon">{item.icon}</span>
          <span className="sidebar-label">{item.label}</span>
        </NavLink>
      ))}

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
        {syncDetail && (
          <div style={{ fontSize: 9, color: '#3D326250', marginTop: 3 }}>{syncDetail}</div>
        )}
      </div>
    </nav>
  )
}
