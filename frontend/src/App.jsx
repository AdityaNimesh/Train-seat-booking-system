import { useCallback, useEffect, useState } from 'react'
import * as api from './api'

// Each browser tab gets its own identity, so two tabs behave like two different passengers.
const userId = (() => {
  let id = sessionStorage.getItem('userId')
  if (!id) {
    id = 'user-' + Math.random().toString(36).slice(2, 8)
    sessionStorage.setItem('userId', id)
  }
  return id
})()

const ROWS = 5 // 5 rows x (2 + aisle + 2) = 20 seats
const fmt = (m) => `${Math.floor(m / 60)}:${String(m % 60).padStart(2, '0')}`

export default function App() {
  const [seats, setSeats] = useState([])
  const [bookings, setBookings] = useState([])
  const [selected, setSelected] = useState([])
  const [reservation, setReservation] = useState(null) // { id, seatNumbers, expiresAtMs }
  const [secondsLeft, setSecondsLeft] = useState(0)
  const [notice, setNotice] = useState(null)
  const [busy, setBusy] = useState(false)

  const refresh = useCallback(async () => {
    try {
      const [s, b] = await Promise.all([api.getSeats(), api.getBookings()])
      setSeats(s)
      setBookings(b)
    } catch (e) {
      setNotice({ type: 'error', text: `Can't reach the booking server. ${e.message}` })
    }
  }, [])

  // poll seat status from the backend
  useEffect(() => {
    refresh()
    const t = setInterval(refresh, 2000)
    return () => clearInterval(t)
  }, [refresh])

  // restore an active reservation after a page reload
  useEffect(() => {
    const id = sessionStorage.getItem('reservationId')
    if (!id) return
    api
      .getReservation(id)
      .then((r) => {
        if (r.status === 'ACTIVE') {
          setReservation({ id: r.reservationId, seatNumbers: r.seatNumbers, expiresAtMs: Date.now() + r.secondsRemaining * 1000 })
        } else sessionStorage.removeItem('reservationId')
      })
      .catch(() => sessionStorage.removeItem('reservationId'))
  }, [])

  // countdown
  useEffect(() => {
    if (!reservation) return
    const tick = () => {
      const left = Math.max(0, Math.ceil((reservation.expiresAtMs - Date.now()) / 1000))
      setSecondsLeft(left)
      if (left === 0) {
        sessionStorage.removeItem('reservationId')
        setReservation(null)
        setNotice({ type: 'error', text: 'Your 2 minutes ran out. The seats were released — pick them again to restart.' })
        refresh()
      }
    }
    tick()
    const t = setInterval(tick, 250)
    return () => clearInterval(t)
  }, [reservation, refresh])

  const clearReservation = () => {
    sessionStorage.removeItem('reservationId')
    setReservation(null)
  }

  const onSeatClick = (seat) => {
    if (reservation) return
    if (seat.status === 'BOOKED') return setNotice({ type: 'error', text: `Seat ${seat.seatNumber} is already booked.` })
    if (seat.status === 'LOCKED') return setNotice({ type: 'error', text: `Seat ${seat.seatNumber} is held by another passenger right now. It frees up if they don't pay within 2 minutes.` })
    setNotice(null)
    setSelected((sel) => (sel.includes(seat.seatNumber) ? sel.filter((n) => n !== seat.seatNumber) : [...sel, seat.seatNumber].sort((a, b) => a - b)))
  }

  const startCheckout = async () => {
    setBusy(true)
    setNotice(null)
    try {
      const r = await api.reserve(userId, selected)
      sessionStorage.setItem('reservationId', r.reservationId)
      setReservation({ id: r.reservationId, seatNumbers: r.seatNumbers, expiresAtMs: Date.now() + r.secondsRemaining * 1000 })
      setSelected([])
    } catch (e) {
      setNotice({ type: 'error', text: e.message })
      if (e.unavailableSeats?.length) setSelected((sel) => sel.filter((n) => !e.unavailableSeats.includes(n)))
    } finally {
      setBusy(false)
      refresh()
    }
  }

  const pay = async () => {
    setBusy(true)
    try {
      const seatsPaid = reservation.seatNumbers.join(', ')
      await api.confirm(reservation.id, userId)
      clearReservation()
      setNotice({ type: 'ok', text: `Booked seat ${seatsPaid}. Have a good trip!` })
    } catch (e) {
      setNotice({ type: 'error', text: e.message })
      if (e.status === 410 || e.status === 409) clearReservation()
    } finally {
      setBusy(false)
      refresh()
    }
  }

  const cancelCheckout = async () => {
    setBusy(true)
    try {
      await api.cancel(reservation.id, userId)
      clearReservation()
      setNotice({ type: 'info', text: 'Reservation cancelled. Your seats are free again.' })
    } catch (e) {
      setNotice({ type: 'error', text: e.message })
      if (e.status === 410 || e.status === 409) clearReservation()
    } finally {
      setBusy(false)
      refresh()
    }
  }

  const visual = (s) => {
    if (reservation?.seatNumbers.includes(s.seatNumber)) return 'mine'
    if (selected.includes(s.seatNumber)) return 'selected'
    return s.status.toLowerCase()
  }

  const bySeat = Object.fromEntries(seats.map((s) => [s.seatNumber, s]))
  const counts = seats.reduce((acc, s) => ({ ...acc, [s.status]: (acc[s.status] || 0) + 1 }), {})

  const seatButton = (n) => {
    const s = bySeat[n]
    if (!s) return <span key={n} className="seat ghost" />
    const v = visual(s)
    return (
      <button key={n} className={`seat ${v}`} onClick={() => onSeatClick(s)} aria-label={`Seat ${n}, ${v}`} aria-pressed={v === 'selected'}>
        {n}
      </button>
    )
  }

  return (
    <main className="page">
      <header className="top">
        <div>
          <h1>Coach A</h1>
          <p className="sub">20 seats · seats you pick are held for 2 minutes while you check out</p>
        </div>
        <p className="who">Signed in as <strong>{userId}</strong></p>
      </header>

      <div className="layout">
        <section className="coach" aria-label="Seat map">
          <div className="front">Front of coach</div>
          {Array.from({ length: ROWS }, (_, r) => (
            <div className="row" key={r}>
              {[1, 2].map((i) => seatButton(r * 4 + i))}
              <span className="aisle" />
              {[3, 4].map((i) => seatButton(r * 4 + i))}
            </div>
          ))}
          <ul className="legend">
            <li><i className="dot available" />Available ({counts.AVAILABLE || 0})</li>
            <li><i className="dot locked" />Held by someone ({counts.LOCKED || 0})</li>
            <li><i className="dot booked" />Booked ({counts.BOOKED || 0})</li>
            <li><i className="dot selected" />Your selection</li>
          </ul>
        </section>

        <aside className="panel">
          {notice && <p className={`notice ${notice.type}`} role="status">{notice.text}</p>}

          {reservation ? (
            <div className="checkout">
              <h2>Checkout</h2>
              <p>Seat {reservation.seatNumbers.join(', ')}</p>
              <p className={`timer ${secondsLeft <= 20 ? 'urgent' : ''}`} aria-live="polite">{fmt(secondsLeft)}</p>
              <p className="hint">Pay before the timer ends or the seats go back on sale.</p>
              <div className="actions">
                <button className="primary" disabled={busy} onClick={pay}>Pay and confirm</button>
                <button className="ghost-btn" disabled={busy} onClick={cancelCheckout}>Cancel reservation</button>
              </div>
            </div>
          ) : (
            <div className="checkout">
              <h2>Your seats</h2>
              <p>{selected.length ? `Seat ${selected.join(', ')}` : 'Tap available seats to select them.'}</p>
              <div className="actions">
                <button className="primary" disabled={busy || !selected.length} onClick={startCheckout}>
                  Hold seats and check out
                </button>
              </div>
            </div>
          )}

          <div className="history">
            <h2>Confirmed bookings</h2>
            {bookings.length === 0 ? (
              <p className="hint">No seats booked yet.</p>
            ) : (
              <table>
                <thead><tr><th>Seat</th><th>Passenger</th></tr></thead>
                <tbody>
                  {bookings.map((b) => (
                    <tr key={b.seatNumber}><td>{b.seatNumber}</td><td>{b.userId}{b.userId === userId ? ' (you)' : ''}</td></tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </aside>
      </div>
    </main>
  )
}
