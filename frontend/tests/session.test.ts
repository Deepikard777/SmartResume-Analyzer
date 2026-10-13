import { test, describe, beforeEach } from 'node:test';
import assert from 'node:assert/strict';

/**
 * Unit tests for the client-side session store (the only place the bearer token
 * lives). A minimal in-memory localStorage shim is installed on globalThis, which is
 * the same object the module resolves its bare `localStorage` reference against.
 */

class MemoryStorage {
  private store = new Map<string, string>();
  getItem(key: string): string | null {
    return this.store.has(key) ? (this.store.get(key) as string) : null;
  }
  setItem(key: string, value: string): void {
    this.store.set(key, value);
  }
  removeItem(key: string): void {
    this.store.delete(key);
  }
  clear(): void {
    this.store.clear();
  }
  get size(): number {
    return this.store.size;
  }
}

const storage = new MemoryStorage();
(globalThis as unknown as { localStorage: MemoryStorage }).localStorage = storage;

const session = await import('../src/services/session.ts');

describe('session storage', () => {
  beforeEach(() => {
    storage.clear();
    session.setUnauthorizedHandler(null);
  });

  test('starts with no token and no cached user', () => {
    assert.equal(session.getToken(), null);
    assert.equal(session.getStoredUserJson(), null);
  });

  test('stores and reads back the token', () => {
    session.setToken('header.payload.signature');
    assert.equal(session.getToken(), 'header.payload.signature');
  });

  test('stores and reads back the cached user', () => {
    session.setStoredUserJson('{"id":1,"name":"Asha","email":"asha@example.com"}');
    assert.equal(session.getStoredUserJson(), '{"id":1,"name":"Asha","email":"asha@example.com"}');
  });

  test('never persists a password alongside the session', () => {
    session.setToken('t');
    session.setStoredUserJson('{"id":1,"name":"Asha","email":"asha@example.com"}');
    const raw = `${storage.getItem('smartresume.token')}${storage.getItem('smartresume.currentUser')}`;
    assert.doesNotMatch(raw, /password|passwd|secret/i);
  });

  test('clearSession removes both the token and the cached user', () => {
    session.setToken('t');
    session.setStoredUserJson('{"id":1}');
    session.clearSession();
    assert.equal(session.getToken(), null);
    assert.equal(session.getStoredUserJson(), null);
    assert.equal(storage.size, 0);
  });

  test('notifies the registered handler exactly once per call', () => {
    let calls = 0;
    session.setUnauthorizedHandler(() => {
      calls += 1;
    });
    session.notifyUnauthorized();
    session.notifyUnauthorized();
    assert.equal(calls, 2);
  });

  test('does nothing when no handler is registered', () => {
    assert.doesNotThrow(() => session.notifyUnauthorized());
  });

  test('a handler registered later replaces the previous one', () => {
    const seen: string[] = [];
    session.setUnauthorizedHandler(() => seen.push('first'));
    session.setUnauthorizedHandler(() => seen.push('second'));
    session.notifyUnauthorized();
    assert.deepEqual(seen, ['second']);
  });
});

describe('session resilience', () => {
  test('a storage that throws does not break the application', () => {
    const broken = {
      getItem() {
        throw new Error('quota');
      },
      setItem() {
        throw new Error('quota');
      },
      removeItem() {
        throw new Error('quota');
      },
    };
    (globalThis as unknown as { localStorage: unknown }).localStorage = broken;

    assert.equal(session.getToken(), null);
    assert.equal(session.getStoredUserJson(), null);
    assert.doesNotThrow(() => session.setToken('t'));
    assert.doesNotThrow(() => session.setStoredUserJson('{}'));
    assert.doesNotThrow(() => session.clearSession());

    (globalThis as unknown as { localStorage: MemoryStorage }).localStorage = storage;
  });
});
