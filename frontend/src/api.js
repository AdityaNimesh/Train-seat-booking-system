async function request(path, options = {}) {
  const res = await fetch('/api' + path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })
  const text = await res.text()
  const body = text ? JSON.parse(text) : null
  if (!res.ok) {
    const err = new Error(body?.message || `Request failed (${res.status})`)
    err.status = res.status
    err.code = body?.error
    err.unavailableSeats = body?.unavailableSeats || []
    throw err
  }
  return body
}

const post = (path, payload) => request(path, { method: 'POST', body: JSON.stringify(payload) })

export const getSeats = () => request('/seats')
export const getBookings = () => request('/bookings')
export const getReservation = (id) => request(`/reservations/${id}`)
export const reserve = (userId, seatNumbers) => post('/reservations', { userId, seatNumbers })
export const confirm = (id, userId) => post(`/reservations/${id}/confirm`, { userId })
export const cancel = (id, userId) => post(`/reservations/${id}/cancel`, { userId })
