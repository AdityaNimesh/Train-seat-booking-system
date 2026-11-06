// Fires 100 simultaneous booking attempts at the running backend and verifies the invariants.
// Usage: node scripts/concurrency-test.mjs        (Node 18+; backend on http://localhost:8080)
const BASE = process.env.API_URL || 'http://localhost:8080/api'
const USERS = 100

async function call(method, path, body) {
  const res = await fetch(BASE + path, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined,
  })
  const text = await res.text()
  return { status: res.status, body: text ? JSON.parse(text) : null }
}

async function attempt(userId, seatNumbers) {
  const r = await call('POST', '/reservations', { userId, seatNumbers })
  if (r.status !== 201) return { ok: false, code: `${r.status} ${r.body?.error}`, seatNumbers }
  const c = await call('POST', `/reservations/${r.body.reservationId}/confirm`, { userId })
  return { ok: c.status === 200, code: `${c.status} ${c.body?.error ?? 'CONFIRMED'}`, seatNumbers }
}

async function scenario(name, pickSeats, expectedWinners) {
  await call('POST', '/admin/reset')
  const results = await Promise.all(Array.from({ length: USERS }, (_, i) => attempt(`user-${i}`, pickSeats(i))))

  const winners = results.filter((r) => r.ok)
  const seats = (await call('GET', '/seats')).body
  const bookings = (await call('GET', '/bookings')).body
  const bookedSeats = seats.filter((s) => s.status === 'BOOKED').length
  const uniqueBooked = new Set(bookings.map((b) => b.seatNumber)).size
  const seatsWon = winners.reduce((n, w) => n + w.seatNumbers.length, 0)

  const tally = {}
  results.filter((r) => !r.ok).forEach((r) => (tally[r.code] = (tally[r.code] || 0) + 1))

  const checks = [
    ['confirmed seats <= 20', bookings.length <= 20],
    ['no seat booked twice', uniqueBooked === bookings.length],
    ['seat map matches bookings', bookedSeats === bookings.length],
    ['winners own exactly the booked seats', seatsWon === bookings.length],
    ...(expectedWinners != null ? [[`exactly ${expectedWinners} successful booking(s)`, winners.length === expectedWinners]] : []),
  ]
  console.log(`\n== ${name}`)
  console.log(`   ${USERS} simultaneous users -> ${winners.length} succeeded, ${results.length - winners.length} rejected`)
  console.log('   rejections:', tally)
  console.log(`   seats booked: ${bookedSeats}, bookings rows: ${bookings.length}, distinct seats: ${uniqueBooked}`)
  checks.forEach(([label, pass]) => console.log(`   ${pass ? 'PASS' : 'FAIL'}  ${label}`))
  return checks.every(([, pass]) => pass)
}

const rnd = (i) => { let x = (i + 1) * 9301 % 233280; return () => ((x = (x * 9301 + 49297) % 233280) / 233280) }
const randomSeats = (i) => {
  const r = rnd(i), picks = new Set(), n = 1 + Math.floor(r() * 3)
  while (picks.size < n) picks.add(1 + Math.floor(r() * 20))
  return [...picks]
}

const passed = [
  await scenario('A: 100 users, 20 seats (5 users per seat)', (i) => [(i % 20) + 1], 20),
  await scenario('B: 100 users all want seat 1', () => [1], 1),
  await scenario('C: 100 users request 1-3 random, overlapping seats', randomSeats, null),
]
await call('POST', '/admin/reset')
console.log(passed.every(Boolean) ? '\nALL SCENARIOS PASSED' : '\nSOME CHECKS FAILED')
process.exit(passed.every(Boolean) ? 0 : 1)
