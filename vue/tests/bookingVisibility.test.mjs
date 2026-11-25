import test from 'node:test'
import assert from 'node:assert/strict'
import { bookingVisibilityKey, filterVisibleBookings, hideBookingIds, readHiddenBookingIds } from '../src/utils/bookingVisibility.js'

function storage() {
  const values = new Map()
  return {
    getItem: key => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
  }
}

test('cleared booking history stays hidden after reload while a new booking remains visible', () => {
  const cache = storage()
  const key = bookingVisibilityKey('Student 1')
  const history = [{ bookingId: 12 }, { bookingId: 11 }]
  const hidden = hideBookingIds(cache, key, history)

  assert.deepEqual([...readHiddenBookingIds(cache, key)], ['12', '11'])
  assert.deepEqual(filterVisibleBookings([...history, { bookingId: 13 }], hidden), [{ bookingId: 13 }])
})

test('hidden booking IDs are isolated by account and malformed storage is ignored', () => {
  assert.notEqual(bookingVisibilityKey('student1'), bookingVisibilityKey('student2'))
  const brokenStorage = { getItem: () => '{bad json', setItem: () => { throw new Error('storage unavailable') } }
  const key = bookingVisibilityKey('student1')
  assert.deepEqual([...readHiddenBookingIds(brokenStorage, key)], [])
  assert.deepEqual([...hideBookingIds(brokenStorage, key, [{ bookingId: 1 }])], ['1'])
})
