import '@testing-library/jest-dom/vitest'

// Node 25 pre-installs `globalThis.localStorage = {}` (an empty object
// that shadows jsdom's Storage implementation — getItem/setItem/etc.
// are missing). Replace it with a conforming in-memory Storage so tests
// don't each have to polyfill.
if (typeof globalThis.localStorage?.getItem !== 'function') {
  const store = new Map<string, string>()
  const storage: Storage = {
    get length() { return store.size },
    clear: () => { store.clear() },
    getItem: (k: string) => store.get(k) ?? null,
    setItem: (k: string, v: string) => { store.set(k, String(v)) },
    removeItem: (k: string) => { store.delete(k) },
    key: (i: number) => Array.from(store.keys())[i] ?? null,
  }
  Object.defineProperty(globalThis, 'localStorage', {
    value: storage,
    writable: true,
    configurable: true,
  })
}
