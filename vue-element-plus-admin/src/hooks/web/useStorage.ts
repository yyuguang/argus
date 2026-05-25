import { getSafeStorage, getStorageKeys } from '@/utils/safeStorage'

// 获取传入的值的类型
const getValueType = (value: any) => {
  const type = Object.prototype.toString.call(value)
  return type.slice(8, -1)
}

export const useStorage = (type: 'sessionStorage' | 'localStorage' = 'sessionStorage') => {
  const storage = getSafeStorage(type)

  const setStorage = (key: string, value: any) => {
    const valueType = getValueType(value)
    storage.setItem(key, JSON.stringify({ type: valueType, value }))
  }

  const getStorage = (key: string) => {
    const value = storage.getItem(key)
    if (value) {
      const { value: val } = JSON.parse(value)
      return val
    } else {
      return value
    }
  }

  const removeStorage = (key: string) => {
    storage.removeItem(key)
  }

  const clear = (excludes?: string[], includeDefaultExcludes = true) => {
    // 获取排除项
    const keys = getStorageKeys(storage)
    const defaultExcludes = ['dynamicRouter', 'serverDynamicRouter']
    const excludesArr = includeDefaultExcludes
      ? excludes
        ? [...excludes, ...defaultExcludes]
        : defaultExcludes
      : excludes || []
    const excludesKeys = excludesArr ? keys.filter((key) => !excludesArr.includes(key)) : keys
    // 排除项不清除
    excludesKeys.forEach((key) => {
      storage.removeItem(key)
    })
    // storage.clear()
  }

  return {
    setStorage,
    getStorage,
    removeStorage,
    clear
  }
}
