import type { App } from 'vue'
import { createPinia } from 'pinia'
import { createPersistedState } from 'pinia-plugin-persistedstate'
import { getSafeStorage } from '@/utils/safeStorage'

const store = createPinia()

store.use(
  createPersistedState({
    storage: getSafeStorage('localStorage')
  })
)

export const setupStore = (app: App<Element>) => {
  app.use(store)
}

export { store }
