declare global {
  interface Window {
    db: {
      getEntriesByDate: (date: string) => Promise<any[]>
      getEntriesByDateAndCategory: (date: string, category: string) => Promise<any[]>
      getEntriesByDateRange: (startDate: string, endDate: string, category: string) => Promise<any[]>
      getLoggedDates: (category: string) => Promise<string[]>
      getAllDatesWithCounts: (startDate: string, endDate: string) => Promise<{ date: string; count: number }[]>
      insertEntry: (category: string, date: string, data: string) => Promise<any>
      updateEntry: (id: string, data: string) => Promise<void>
      deleteEntry: (id: string) => Promise<void>
      getChoreTemplates: () => Promise<any[]>
      addChoreTemplate: (name: string, category: string | null, recurrence: string | null) => Promise<void>
      deleteChoreTemplate: (id: string) => Promise<void>
      getHobbies: () => Promise<any[]>
      addHobby: (name: string, color: string) => Promise<void>
      deleteHobby: (id: string) => Promise<void>
      getPeople: () => Promise<any[]>
      addPerson: (name: string) => Promise<void>
      deletePerson: (id: string) => Promise<void>
      getSetting: (key: string) => Promise<string | null>
      setSetting: (key: string, value: string) => Promise<void>
    }
    llm: {
      onToken: (callback: (token: string) => void) => () => void
      chat: (message: string) => Promise<string>
      getStatus: () => Promise<string>
      getError: () => Promise<string>
      onStatusChange: (callback: (status: string) => void) => () => void
    }
  }
}

export function useDatabase() {
  return window.db
}

export function useLlm() {
  return window.llm
}
