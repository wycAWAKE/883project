<!-- M3: Room booking page implementation. Search for "M3:" to find this feature's additions. -->
<template>
  <section class="booking-page">
    <RouterLink to="/home" class="back-link">← Back to home</RouterLink>
    <header class="heading">
      <div><p class="eyebrow">Facilities / M3</p><h1>Room Booking</h1></div>
      <span class="timezone">Local time · {{ localTime }}</span>
    </header>
    <p v-if="error" class="notice error" role="alert">{{ error }}</p>
    <div class="booking-grid">
      <section class="availability">
        <h2>Find a room</h2>
        <div class="filters">
          <label>Room
            <select v-model="roomId" @change="loadAvailability">
              <option v-for="room in rooms" :key="room.roomId" :value="String(room.roomId)">{{ room.roomCode }} · {{ room.roomName }}</option>
            </select>
          </label>
          <label>Date
            <input v-model="date" type="date" :min="today" :max="maxDate" @change="loadAvailability">
          </label>
        </div>
        <div v-if="loading" class="state">Loading availability…</div>
        <div v-else-if="!rooms.length" class="state">No rooms are available.</div>
        <div v-else class="slot-list">
          <div :class="['slots', { expanded: showAllSlots }]" aria-label="Fixed time slots">
            <div v-for="slot in visibleSlots" :key="slot" class="slot-row">
            <div><strong>{{ slot }}</strong><span>{{ slotStatus(slot) }}</span></div>
            <button :disabled="isUnavailable(slot) || submitting" @click="book(slot)" :aria-label="`Book ${slot}`">Book</button>
            </div>
          </div>
          <button v-if="slots.length > initialSlotCount" class="expand-toggle" :aria-expanded="showAllSlots" @click="showAllSlots = !showAllSlots">
            {{ showAllSlots ? 'Show fewer time slots' : `Show all ${slots.length} time slots` }}
          </button>
        </div>
        <p v-if="feedback" class="notice success" role="status">{{ feedback }}</p>
      </section>
      <section class="my-bookings">
        <div class="section-title"><h2>My bookings</h2><button class="clear-bookings" title="Clear booking list" aria-label="Clear booking list" :disabled="mineLoading" @click="clearBookingList"><Delete /></button></div>
        <div v-if="!myBookings.length" class="state">You have no bookings yet.</div>
        <div v-else class="booking-list-wrap">
          <ul :class="['booking-list', { expanded: showAllBookings }]">
          <li v-for="booking in visibleBookings" :key="booking.bookingId">
            <div><strong>{{ booking.roomName }}</strong><span>{{ formatDate(booking.date) }} · {{ booking.timeSlot }}</span></div>
            <div class="booking-actions"><span :class="['status', booking.status.toLowerCase()]">{{ booking.status === 'BOOKED' ? 'Booked' : 'Cancelled' }}</span>
              <button v-if="booking.status === 'BOOKED'" class="cancel" :disabled="submitting" @click="cancel(booking.bookingId)">Cancel</button></div>
          </li>
          </ul>
          <button v-if="myBookings.length > initialBookingCount" class="expand-toggle" :aria-expanded="showAllBookings" @click="showAllBookings = !showAllBookings">
            {{ showAllBookings ? 'Show fewer bookings' : `Show all ${myBookings.length} bookings` }}
          </button>
        </div>
      </section>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { Delete } from '@element-plus/icons-vue'
import request from '../../utils/request.js'
import { bookingVisibilityKey, filterVisibleBookings, hideBookingIds, readHiddenBookingIds } from '../../utils/bookingVisibility.js'
defineProps({ module: { type: Object, required: true } })
// M3: Keep long lists collapsed by default; expanded lists scroll inside their panels.
const initialSlotCount = 4, initialBookingCount = 5
const rooms = ref([]), roomId = ref(''), date = ref(''), slots = ref([]), bookedSlots = ref([]), unavailableSlots = ref([]), myBookings = ref([])
const loading = ref(false), mineLoading = ref(false), submitting = ref(false), error = ref(''), feedback = ref('')
const showAllSlots = ref(false), showAllBookings = ref(false)
// M3: Display the computer's local clock and release the timer when leaving this page.
const localTime = ref(formatLocalTime())
let localTimeTimer, bookingRequestVersion = 0
const safeLocalStorage = (() => {
  const fallback = { getItem: () => null, setItem: () => {} }
  if (typeof window === 'undefined') return fallback
  try { return window.localStorage } catch { return fallback }
})()
const bookingOwner = (() => {
  try { return JSON.parse((typeof window === 'undefined' ? null : window.sessionStorage.getItem('user')) || '{}').username || '' } catch { return '' }
})()
const hiddenBookingsKey = bookingVisibilityKey(bookingOwner)
const hiddenBookingIds = ref(readHiddenBookingIds(safeLocalStorage, hiddenBookingsKey))
const visibleSlots = computed(() => showAllSlots.value ? slots.value : slots.value.slice(0, initialSlotCount))
const visibleBookings = computed(() => showAllBookings.value ? myBookings.value : myBookings.value.slice(0, initialBookingCount))
function formatLocalTime() {
  const now = new Date()
  const pad = value => String(value).padStart(2, '0')
  return `${pad(now.getMonth() + 1)}/${pad(now.getDate())}/${now.getFullYear()} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
}
const campusDate = () => {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date())
  const value = Object.fromEntries(parts.map(part => [part.type, part.value]))
  return new Date(Number(value.year), Number(value.month) - 1, Number(value.day))
}
const todayDate = campusDate()
const iso = d => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
const today = iso(todayDate), maxDate = iso(new Date(todayDate.getFullYear(), todayDate.getMonth(), todayDate.getDate() + 30))
date.value = today
const isBooked = slot => bookedSlots.value.some(item => (item.time_slot || item.timeSlot) === slot)
const isPast = slot => unavailableSlots.value.includes(slot)
const isUnavailable = slot => isBooked(slot) || isPast(slot)
const slotStatus = slot => isBooked(slot) ? 'Booked' : isPast(slot) ? 'Unavailable' : 'Available'
const formatDate = value => String(value).slice(0, 10)
function ensureSuccess(response) {
  if (response?.code !== '0') throw new Error(response?.msg || 'The request could not be completed.')
  return response
}
async function loadRooms() {
  try { const res = ensureSuccess(await request.get('/bookings/rooms')); rooms.value = res.data || []; roomId.value = rooms.value[0] ? String(rooms.value[0].roomId) : ''; await loadAvailability() }
  catch (e) { error.value = e.response?.data?.msg || e.message || 'Could not load rooms. Please retry.' }
}
async function loadAvailability() {
  if (!roomId.value || !date.value) return
  loading.value = true; error.value = ''
  slots.value = []; bookedSlots.value = []; unavailableSlots.value = []
  showAllSlots.value = false
  try { const res = ensureSuccess(await request.get('/bookings/availability', { params: { roomId: roomId.value, date: date.value } })); slots.value = res.data.timeSlots; bookedSlots.value = res.data.bookedSlots; unavailableSlots.value = res.data.unavailableSlots || [] }
  catch (e) { error.value = e.response?.data?.msg || e.message || 'Could not load availability. Please retry.' }
  finally { loading.value = false }
}
async function loadMine() {
  const version = ++bookingRequestVersion
  mineLoading.value = true
  try {
    const res = ensureSuccess(await request.get('/bookings/mine'))
    if (version !== bookingRequestVersion) return
    hiddenBookingIds.value = new Set([
      ...hiddenBookingIds.value,
      ...readHiddenBookingIds(safeLocalStorage, hiddenBookingsKey),
    ])
    // M3: Sort newest creations first even when an older backend query is still running.
    myBookings.value = filterVisibleBookings(res.data || [], hiddenBookingIds.value).sort((a, b) => {
      const createdDifference = Date.parse(b.createdAt) - Date.parse(a.createdAt)
      return Number.isNaN(createdDifference) || createdDifference === 0
        ? Number(b.bookingId) - Number(a.bookingId)
        : createdDifference
    })
    if (myBookings.value.length <= initialBookingCount) showAllBookings.value = false
  } catch (e) {
    if (version === bookingRequestVersion) error.value = e.response?.data?.msg || e.message || 'Could not load your bookings.'
  } finally {
    if (version === bookingRequestVersion) mineLoading.value = false
  }
}
// M3: Hide existing rows across reloads while preserving server-side booking history.
function clearBookingList() {
  if (mineLoading.value) return
  bookingRequestVersion += 1
  hiddenBookingIds.value = hideBookingIds(safeLocalStorage, hiddenBookingsKey, myBookings.value, hiddenBookingIds.value)
  myBookings.value = []
  mineLoading.value = false
  showAllBookings.value = false
}
async function book(timeSlot) {
  submitting.value = true; error.value = ''; feedback.value = ''
  try { ensureSuccess(await request.post('/bookings', { roomId: roomId.value, date: date.value, timeSlot })); feedback.value = 'Booking confirmed.'; await Promise.all([loadAvailability(), loadMine()]) }
  catch (e) { await loadAvailability(); error.value = e.response?.data?.msg || e.message || 'Booking failed. Refresh availability and try again.' }
  finally { submitting.value = false }
}
async function cancel(bookingId) {
  submitting.value = true; error.value = ''; feedback.value = ''
  try { ensureSuccess(await request.post(`/bookings/${bookingId}/cancel`)); feedback.value = 'Booking cancelled and the slot released.'; await Promise.all([loadAvailability(), loadMine()]) }
  catch (e) { error.value = e.response?.data?.msg || e.message || 'Cancellation failed. Please refresh and retry.' }
  finally { submitting.value = false }
}
onMounted(() => { loadRooms(); loadMine(); localTimeTimer = window.setInterval(() => { localTime.value = formatLocalTime() }, 1000) })
onUnmounted(() => window.clearInterval(localTimeTimer))
</script>

<style scoped>
/* M3: Pin the booking workspace to the viewport and scroll only within expanded lists. */
.booking-page{height:100%;min-height:0;max-width:1200px;margin:0 auto;padding:24px 32px;color:#1e2938;display:flex;flex-direction:column;overflow:hidden}
.back-link{flex:0 0 auto;color:#526c88;text-decoration:none;font-size:13px}
.heading{flex:0 0 auto;display:flex;align-items:end;justify-content:space-between;margin:18px 0 18px}
.eyebrow{font-size:12px;color:#68788c;margin-bottom:5px}
h1{font-size:27px;line-height:1.3}
h2{font-size:17px;line-height:1.3;margin-bottom:16px}
.timezone{font-size:12px;color:#5f7288}
.booking-grid{flex:1;min-height:0;display:grid;grid-template-columns:minmax(0,1.15fr) minmax(300px,.85fr);gap:32px;border-top:1px solid #dce4eb;padding-top:18px}
.availability,.my-bookings{display:flex;flex-direction:column;min-width:0;min-height:0}
.filters{flex:0 0 auto;display:grid;grid-template-columns:1fr 1fr;gap:14px;margin-bottom:14px}
.filters label{display:grid;gap:7px;font-size:12px;color:#64748b}
.filters select,.filters input{height:40px;min-width:0;padding:0 10px;border:1px solid #cbd5df;border-radius:5px;background:white;color:#1e2938;font:inherit}
.slot-list,.booking-list-wrap{flex:1;min-height:0;display:flex;flex-direction:column}
.slots,.booking-list{flex:1;min-height:0;border-top:1px solid #e2e8ef;overflow-y:auto;overscroll-behavior:contain;scrollbar-gutter:stable}
.slot-row,.booking-list li{display:flex;align-items:center;justify-content:space-between;gap:16px;min-height:58px;border-bottom:1px solid #e2e8ef}
.slot-row div,.booking-list li>div:first-child{display:grid;gap:5px;min-width:0}
.slot-row strong,.booking-list strong{font-size:14px;font-weight:600}
.slot-row span,.booking-list li span{font-size:12px;color:#64748b}
.slot-row button,.cancel{flex:0 0 auto;border:1px solid #157a67;border-radius:4px;padding:7px 14px;background:#fff;color:#126c5b;font-weight:600;cursor:pointer}
.slot-row button:disabled{border-color:#ccd5dd;color:#8793a1;cursor:not-allowed}
.section-title{flex:0 0 auto;display:flex;align-items:center;justify-content:space-between;margin-bottom:8px}
.clear-bookings{width:32px;height:32px;border:1px solid #cbd5df;background:#fff;border-radius:4px;font-size:16px;cursor:pointer;color:#334155;display:grid;place-items:center}
.clear-bookings:disabled{opacity:.5;cursor:wait}
.booking-actions{display:flex;align-items:center;gap:12px}
.cancel{color:#a13e36;border-color:#d9aaa6;padding:6px 10px}
.cancel:disabled{opacity:.5}
.status{padding:4px 7px;border-radius:3px;background:#e7f3ef;color:#136b58!important}
.status.cancelled{background:#f2f3f4;color:#717c88!important}
.expand-toggle{flex:0 0 auto;align-self:flex-start;margin-top:8px;padding:4px 0;border:0;background:transparent;color:#246b68;font-size:12px;font-weight:600;cursor:pointer}
.state{padding:18px 0;color:#718096;font-size:14px}
.notice{flex:0 0 auto;margin-top:8px;padding:8px 10px;border-radius:4px;font-size:12px}
.error{background:#fff0ee;color:#9b342b}
.success{background:#eaf5f1;color:#126653}
.booking-list{list-style:none;margin:0;padding:0}
@media(max-width:760px){.booking-page{padding:16px 16px 12px}.heading{margin:10px 0 12px;align-items:center;gap:12px}.booking-grid{grid-template-columns:minmax(0,1fr);grid-template-rows:minmax(0,1.05fr) minmax(0,.95fr);gap:12px;padding-top:12px}.availability,.my-bookings{overflow:hidden}h2{font-size:15px;margin-bottom:8px}.filters{grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:8px;margin-bottom:8px}.filters select,.filters input{height:36px;padding:0 6px;font-size:12px}.slot-row,.booking-list li{min-height:48px;gap:8px}.slot-row strong,.booking-list strong{font-size:12px}.booking-actions{gap:5px}.cancel{padding:5px 7px}.status{padding:3px 5px}.expand-toggle{margin-top:4px}.notice{max-width:100%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}}
@media(max-height:640px){.booking-page{padding-top:10px;padding-bottom:8px}.heading{margin:8px 0}.booking-grid{padding-top:10px}h2{margin-bottom:8px}.slot-row,.booking-list li{min-height:42px}}
</style>
