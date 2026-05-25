export type SupportedStorageType = 'localStorage' | 'sessionStorage'

type SafeStorageLike = Storage

const createMemoryStorage = (): SafeStorageLike => {
  const memory = new Map<string, string>()

  return {
    get length() {
      return memory.size
    },
    clear() {
      memory.clear()
    },
    getItem(key: string) {
      return memory.has(key) ? memory.get(key)! : null
    },
    key(index: number) {
      return Array.from(memory.keys())[index] ?? null
    },
    removeItem(key: string) {
      memory.delete(key)
    },
    setItem(key: string, value: string) {
      memory.set(key, value)
    }
  }
}

const memoryLocalStorage = createMemoryStorage()
const memorySessionStorage = createMemoryStorage()

const resolveMemoryStorage = (type: SupportedStorageType) => {
  return type === 'localStorage' ? memoryLocalStorage : memorySessionStorage
}

const isStorageAvailable = (type: SupportedStorageType) => {
  if (typeof window === 'undefined') {
    return false
  }
  try {
    const storage = window[type]
    const probeKey = `__argus_storage_probe__${type}`
    storage.setItem(probeKey, probeKey)
    storage.removeItem(probeKey)
    return true
  } catch (_error) {
    return false
  }
}

export const getSafeStorage = (type: SupportedStorageType): SafeStorageLike => {
  if (isStorageAvailable(type)) {
    return window[type]
  }
  return resolveMemoryStorage(type)
}

export const ensureStorageGlobals = () => {
  if (typeof window === 'undefined') {
    return
  }

  ;(['localStorage', 'sessionStorage'] as SupportedStorageType[]).forEach((type) => {
    if (window[type]) {
      return
    }

    Object.defineProperty(window, type, {
      configurable: true,
      enumerable: true,
      writable: true,
      value: resolveMemoryStorage(type)
    })
  })
}

export const getStorageKeys = (storage: Pick<Storage, 'key' | 'length'>) => {
  const keys: string[] = []
  for (let index = 0; index < storage.length; index += 1) {
    const key = storage.key(index)
    if (key) {
      keys.push(key)
    }
  }
  return keys
}
