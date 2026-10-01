import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { pathToFileURL } from 'node:url'
import { test } from 'node:test'
import ts from 'typescript'
import axios from 'axios'

const require = createRequire(import.meta.url)
const values = new Map()
globalThis.window = new EventTarget()
window.localStorage = {
  getItem: key => values.get(key) ?? null,
  setItem: (key, value) => values.set(key, value),
  removeItem: key => values.delete(key),
}
function moduleUrl(source) {
  const compiled = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 },
  }).outputText
  return `data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`
}
const authUrl = moduleUrl(readFileSync(new URL('../src/lib/auth.ts', import.meta.url), 'utf8'))
const auth = await import(authUrl)
const apiSource = readFileSync(new URL('../src/lib/api.ts', import.meta.url), 'utf8')
  .replace("'./auth'", JSON.stringify(authUrl))
  .replace("'axios'", JSON.stringify(pathToFileURL(require.resolve('axios')).href))
  .replace('import.meta.env', '({})')
const { api } = await import(moduleUrl(apiSource))
const session = token => ({ token, userId: token, email: `${token}@example.test`, fullName: token, role: 'USER' })

test('malformed stored auth is removed', () => {
  window.localStorage.setItem(auth.AUTH_STORAGE_KEY, '{broken')
  assert.equal(auth.getAuthSession(), null)
  assert.equal(window.localStorage.getItem(auth.AUTH_STORAGE_KEY), null)
})

test('late 401 for A does not sign out newly logged-in B', async () => {
  auth.saveAuthSession(session('A'))
  let expired = 0
  const listener = () => expired++
  window.addEventListener(auth.AUTH_EXPIRED_EVENT, listener)
  await assert.rejects(api.get('/auth/me', { adapter: async config => {
    assert.equal(config.headers.Authorization, 'Bearer A')
    auth.saveAuthSession(session('B'))
    throw new axios.AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, null,
      { status: 401, data: {}, headers: {}, config })
  } }))
  assert.equal(auth.getAuthToken(), 'B')
  assert.equal(expired, 0)
  window.removeEventListener(auth.AUTH_EXPIRED_EVENT, listener)
})

test('401 for current session removes auth', async () => {
  auth.saveAuthSession(session('A'))
  await assert.rejects(api.get('/auth/me', { adapter: async config => {
    throw new axios.AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, null,
      { status: 401, data: {}, headers: {}, config })
  } }))
  assert.equal(auth.getAuthSession(), null)
})

test('late successful connect URL from A cannot redirect B into A flow', async () => {
  auth.saveAuthSession(session('A'))
  await assert.rejects(api.get('/social/meta/connect-url', { adapter: async config => {
    auth.saveAuthSession(session('B'))
    return { status: 200, data: { success: true, data: { url: 'https://example.test/oauth' } }, headers: {}, config }
  } }))
  assert.equal(auth.getAuthToken(), 'B')
})

test('restore request keeps its captured token even if storage changes before dispatch', async () => {
  auth.saveAuthSession(session('B'))
  await assert.rejects(api.get('/auth/me', {
    headers: { Authorization: 'Bearer A' },
    adapter: async config => {
      assert.equal(config.headers.Authorization, 'Bearer A')
      return { status: 200, data: { success: true, data: session('A') }, headers: {}, config }
    },
  }))
  assert.equal(auth.getAuthToken(), 'B')
})
