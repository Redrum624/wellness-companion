import { contextBridge, ipcRenderer } from 'electron'

const dbApi = {
  getEntriesByDate: (date: string) => ipcRenderer.invoke('db:getEntriesByDate', date),
  getEntriesByDateAndCategory: (date: string, category: string) => ipcRenderer.invoke('db:getEntriesByDateAndCategory', date, category),
  getEntriesByDateRange: (startDate: string, endDate: string, category: string) => ipcRenderer.invoke('db:getEntriesByDateRange', startDate, endDate, category),
  getLoggedDates: (category: string) => ipcRenderer.invoke('db:getLoggedDates', category),
  getAllDatesWithCounts: (startDate: string, endDate: string) => ipcRenderer.invoke('db:getAllDatesWithCounts', startDate, endDate),
  insertEntry: (category: string, date: string, data: string) => ipcRenderer.invoke('db:insertEntry', category, date, data),
  updateEntry: (id: string, data: string) => ipcRenderer.invoke('db:updateEntry', id, data),
  deleteEntry: (id: string) => ipcRenderer.invoke('db:deleteEntry', id),
  getChoreTemplates: () => ipcRenderer.invoke('db:getChoreTemplates'),
  addChoreTemplate: (name: string, category: string | null, recurrence: string | null) => ipcRenderer.invoke('db:addChoreTemplate', name, category, recurrence),
  deleteChoreTemplate: (id: string) => ipcRenderer.invoke('db:deleteChoreTemplate', id),
  getHobbies: () => ipcRenderer.invoke('db:getHobbies'),
  addHobby: (name: string, color: string) => ipcRenderer.invoke('db:addHobby', name, color),
  deleteHobby: (id: string) => ipcRenderer.invoke('db:deleteHobby', id),
  getPeople: () => ipcRenderer.invoke('db:getPeople'),
  addPerson: (name: string) => ipcRenderer.invoke('db:addPerson', name),
  deletePerson: (id: string) => ipcRenderer.invoke('db:deletePerson', id),
  getSetting: (key: string) => ipcRenderer.invoke('db:getSetting', key),
  setSetting: (key: string, value: string) => ipcRenderer.invoke('db:setSetting', key, value)
}

const llmApi = {
  onToken: (callback: (token: string) => void) => {
    const handler = (_event: any, token: string) => callback(token)
    ipcRenderer.on('llm:token', handler)
    return () => ipcRenderer.removeListener('llm:token', handler)
  },
  chat: (message: string) => ipcRenderer.invoke('llm:chat', message),
  getStatus: () => ipcRenderer.invoke('llm:getStatus'),
  getError: () => ipcRenderer.invoke('llm:getError'),
  onStatusChange: (callback: (status: string) => void) => {
    const handler = (_event: any, status: string) => callback(status)
    ipcRenderer.on('llm:status', handler)
    return () => ipcRenderer.removeListener('llm:status', handler)
  }
}

export interface PairedDevice {
  deviceId: string
  keyId: string
  label: string
  lastSeen: number
}

const syncApi = {
  getStatus: () => ipcRenderer.invoke('sync:getStatus'),
  getPort: () => ipcRenderer.invoke('sync:getPort'),
  getLocalIp: () => ipcRenderer.invoke('sync:getLocalIp'),
  // Mints a one-time 128-bit pairing secret, rendered as a 33-character code
  // (grouped 5-5-5-5-5-5-3). The phone types it once; it becomes that phone's
  // long-term device key.
  createPairing: (): Promise<{ keyId: string; code: string; expiresAt: number }> =>
    ipcRenderer.invoke('sync:createPairing'),
  listDevices: (): Promise<PairedDevice[]> => ipcRenderer.invoke('sync:listDevices'),
  // Revokes exactly one phone; the others keep syncing.
  removeDevice: (deviceId: string): Promise<boolean> =>
    ipcRenderer.invoke('sync:removeDevice', deviceId),
  // Last resort: forget every paired device.
  regeneratePairingToken: (): Promise<void> =>
    ipcRenderer.invoke('sync:regeneratePairingToken'),
  onStatusChange: (
    callback: (info: { status: string; detail?: string; code?: string }) => void
  ) => {
    const handler = (_event: any, info: any) => callback(info)
    ipcRenderer.on('sync:status', handler)
    return () => ipcRenderer.removeListener('sync:status', handler)
  }
}

contextBridge.exposeInMainWorld('db', dbApi)
contextBridge.exposeInMainWorld('llm', llmApi)
contextBridge.exposeInMainWorld('sync', syncApi)
