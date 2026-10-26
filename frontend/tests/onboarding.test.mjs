import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import ts from 'typescript'

const values = new Map()
globalThis.window = new EventTarget()
window.localStorage = {
  getItem: (key) => values.get(key) ?? null,
  setItem: (key, value) => values.set(key, value),
  removeItem: (key) => values.delete(key),
}

function load(path) {
  const source = readFileSync(new URL(path, import.meta.url), 'utf8')
  const compiled = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 },
  }).outputText
  return import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`)
}

const onboarding = await load('../src/lib/onboarding.ts')

const overview = (patch = {}) => ({
  totalPosts: 0, scheduledPosts: 0, publishedPosts: 0, failedPosts: 0, channels: [], recentPosts: [], ...patch,
})
const channel = (active = true) => ({ id: 'c', accountId: 'a', name: 'Page', type: 'Facebook Page', badge: 'Facebook', color: '#000', active })

test('a brand-new account has nothing done and is not set up', () => {
  const state = onboarding.getOnboardingState(overview())
  assert.deepEqual(state, { hasChannel: false, hasPost: false, hasPublishedOrScheduled: false, doneCount: 0, isSetUp: false })
})

test('an inactive channel does not count as connected', () => {
  assert.equal(onboarding.getOnboardingState(overview({ channels: [channel(false)] })).hasChannel, false)
})

test('a channel alone is progress but not set up', () => {
  const state = onboarding.getOnboardingState(overview({ channels: [channel()] }))
  assert.equal(state.doneCount, 1)
  assert.equal(state.isSetUp, false)
})

test('drafts and failed posts do not make the account set up', () => {
  const state = onboarding.getOnboardingState(overview({ channels: [channel()], totalPosts: 2, failedPosts: 2 }))
  assert.equal(state.hasPost, true)
  assert.equal(state.hasPublishedOrScheduled, false)
  assert.equal(state.isSetUp, false)
})

test('a channel plus a scheduled or published post is set up', () => {
  assert.equal(onboarding.getOnboardingState(overview({ channels: [channel()], totalPosts: 1, scheduledPosts: 1 })).isSetUp, true)
  const published = onboarding.getOnboardingState(overview({ channels: [channel()], totalPosts: 1, publishedPosts: 1 }))
  assert.equal(published.isSetUp, true)
  assert.equal(published.doneCount, 3)
})

test('dismissal is remembered and survives unavailable storage', () => {
  assert.equal(onboarding.isOnboardingDismissed(), false)
  onboarding.dismissOnboarding()
  assert.equal(onboarding.isOnboardingDismissed(), true)

  const original = window.localStorage
  window.localStorage = {
    getItem: () => { throw new Error('blocked') },
    setItem: () => { throw new Error('blocked') },
    removeItem: () => { throw new Error('blocked') },
  }
  assert.equal(onboarding.isOnboardingDismissed(), false)
  assert.doesNotThrow(() => onboarding.dismissOnboarding())
  window.localStorage = original
})
