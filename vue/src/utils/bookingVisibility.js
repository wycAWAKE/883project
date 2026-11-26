// M3: Persist cleared booking IDs per account without deleting server history.
export function bookingVisibilityKey(username) {
  const owner = typeof username === 'string' ? username.trim() : ''
  return owner ? `smartcampus:m3:hidden-bookings:${encodeURIComponent(owner)}` : null
}

export function readHiddenBookingIds(storage, key) {
  if (!key) return new Set()
  try {
    const parsed = JSON.parse(storage.getItem(key) || '[]')
    return new Set(Array.isArray(parsed) ? parsed.map(String) : [])
  } catch {
    return new Set()
  }
}

export function hideBookingIds(storage, key, bookings, existingIds = new Set()) {
  const hiddenIds = new Set(existingIds)
  for (const booking of bookings) {
    if (booking?.bookingId != null) hiddenIds.add(String(booking.bookingId))
  }
  if (key) {
    try { storage.setItem(key, JSON.stringify([...hiddenIds])) } catch { /* Keep the current page usable if storage is unavailable. */ }
  }
  return hiddenIds
}

export function filterVisibleBookings(bookings, hiddenIds) {
  return bookings.filter(booking => !hiddenIds.has(String(booking.bookingId)))
}
