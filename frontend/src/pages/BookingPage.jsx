import { CalendarClock, Check, Copy, Cpu, Grid3X3, HardDrive, LoaderCircle, Monitor, Radio, Trash2, X } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { idleGridApi } from '../api/idleGridApi'
import { useApp } from '../context/AppContext'

function nextHour() {
  const date = new Date()
  date.setMinutes(0, 0, 0)
  date.setHours(date.getHours() + 1)
  return date
}

function inputValue(date) {
  const offset = date.getTimezoneOffset() * 60000
  return new Date(date.getTime() - offset).toISOString().slice(0, 16)
}

function formatDate(value) {
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

function shortId(id = '') {
  return id.length > 14 ? `${id.slice(0, 8)}...${id.slice(-4)}` : id
}

export default function BookingPage() {
  const { nodes } = useApp()
  const [bookings, setBookings] = useState([])
  const [selectedNode, setSelectedNode] = useState('')
  const [detailsNode, setDetailsNode] = useState(null)
  const [requester, setRequester] = useState('')
  const [startAt, setStartAt] = useState(inputValue(nextHour()))
  const [endAt, setEndAt] = useState(inputValue(new Date(nextHour().getTime() + 60 * 60 * 1000)))
  const [cpuReq, setCpuReq] = useState(1)
  const [ramReqMb, setRamReqMb] = useState(512)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')

  const onlineNodes = nodes.filter((node) => node.status === 'ONLINE')

  const refreshBookings = async () => {
    try {
      setBookings(await idleGridApi.getBookings())
      setError('')
    } catch (requestError) {
      setError(requestError.message || 'Unable to load bookings')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    refreshBookings()
    const interval = window.setInterval(refreshBookings, 5000)
    return () => window.clearInterval(interval)
  }, [])

  useEffect(() => {
    if (!selectedNode && onlineNodes[0]) setSelectedNode(onlineNodes[0].id)
  }, [onlineNodes, selectedNode])

  const nodeRows = useMemo(() => {
    const rows = []
    for (let index = 0; index < nodes.length; index += 4) rows.push(nodes.slice(index, index + 4))
    return rows
  }, [nodes])

  const bookingCount = bookings.filter((booking) => new Date(booking.endAt) > new Date()).length

  const submitBooking = async (event) => {
    event.preventDefault()
    setSaving(true)
    setMessage('')
    setError('')
    try {
      await idleGridApi.createBooking({
        nodeId: selectedNode,
        requester,
        startAt: new Date(startAt).toISOString(),
        endAt: new Date(endAt).toISOString(),
        cpuReq: Number(cpuReq),
        ramReqMb: Number(ramReqMb),
      })
      setMessage('Resource reserved successfully')
      setRequester('')
      await refreshBookings()
    } catch (requestError) {
      setError(requestError.message || 'Could not reserve this resource')
    } finally {
      setSaving(false)
    }
  }

  const removeBooking = async (bookingId) => {
    try {
      await idleGridApi.deleteBooking(bookingId)
      await refreshBookings()
    } catch (requestError) {
      setError(requestError.message || 'Could not cancel booking')
    }
  }

  return (
    <div className="animate-fade-in">
      <div className="mb-8 max-w-2xl">
        <div className="mb-3 flex items-center gap-2 font-mono text-[10px] uppercase tracking-[0.18em] text-ember"><CalendarClock size={14} /> Lab reservations</div>
        <h1 className="font-display text-4xl leading-none sm:text-5xl">Reserve a machine before you run.</h1>
        <p className="mt-3 text-sm leading-6 text-black/50 dark:text-white/50">Choose a live lab node, request a resource slice, and book a time window for your compute session.</p>
      </div>

      {error && <div className="mb-5 rounded-xl border border-red-500/20 bg-red-500/5 px-4 py-3 text-xs text-red-600 dark:text-red-400">{error}</div>}
      {message && <div className="mb-5 flex items-center gap-2 rounded-xl border border-emerald-500/20 bg-emerald-500/5 px-4 py-3 text-xs text-emerald-700 dark:text-emerald-400"><Check size={14} />{message}</div>}

      <div className="grid gap-8 xl:grid-cols-[1.2fr_.8fr]">
        <section>
          <div className="mb-4 flex items-end justify-between"><div><div className="mb-2 flex items-center gap-2 font-mono text-[10px] uppercase tracking-[0.16em] text-black/35 dark:text-white/35"><Grid3X3 size={13} /> Live inventory</div><h2 className="font-display text-3xl">Choose your lab seat</h2></div><span className="font-mono text-[10px] text-black/35 dark:text-white/35">{onlineNodes.length} online · {bookingCount} booked</span></div>
          <div className="rounded-[26px] border border-black/[0.08] bg-[#f1ede7] p-4 shadow-soft dark:border-white/[0.08] dark:bg-[#211c19] sm:p-6">
            <div className="mb-7 flex flex-col items-center gap-2"><div className="flex w-full max-w-sm items-center justify-center gap-2 rounded-full border border-ember/25 bg-ember-soft/70 px-5 py-2 font-mono text-[10px] uppercase tracking-[0.18em] text-ember dark:bg-ember/10"><Monitor size={13} /> Lab supervisor desk</div><div className="h-3 w-1/2 rounded-b-full border-b-2 border-black/10 dark:border-white/10" /></div>
            <div className="space-y-5">
              {nodeRows.map((row, rowIndex) => <div key={`row-${rowIndex}`} className="flex items-start gap-3"><span className="w-8 pt-4 text-center font-mono text-[10px] text-black/30 dark:text-white/30">{String(rowIndex + 1).padStart(2, '0')}</span><div className="grid flex-1 grid-cols-2 gap-3 sm:grid-cols-4">{row.map((node) => <ClusterNode key={node.id} node={node} bookings={bookings.filter((booking) => booking.nodeId === node.id)} onSelect={setDetailsNode} />)}</div></div>)}
            </div>
            {!loading && !onlineNodes.length && <div className="rounded-2xl border border-dashed border-black/10 px-5 py-14 text-center text-sm text-black/40 dark:border-white/10 dark:text-white/40">No lab nodes are online yet.</div>}
            {loading && <div className="flex items-center gap-2 py-10 text-sm text-black/40"><LoaderCircle size={15} className="animate-spin" /> Loading live inventory...</div>}
            <div className="mt-7 flex flex-wrap items-center justify-center gap-x-5 gap-y-2 border-t border-black/[0.08] pt-4 dark:border-white/[0.08]"><Legend color="bg-emerald-500" label="Available" /><Legend color="bg-ember" label="Booked" /><Legend color="bg-black/20 dark:bg-white/20" label="Offline" /><span className="font-mono text-[10px] text-black/30 dark:text-white/30">Click a node for details</span></div>
          </div>
        </section>

        <section className="h-fit rounded-2xl border border-black/[0.08] bg-white p-5 shadow-soft dark:border-white/[0.08] dark:bg-[#211c19] sm:p-6">
          <div className="mb-5 flex items-center gap-2"><CalendarClock size={17} className="text-ember" /><h2 className="font-display text-2xl">New booking</h2></div>
          <form onSubmit={submitBooking} className="space-y-4">
            <Field label="Your name or project"><input required value={requester} onChange={(event) => setRequester(event.target.value)} placeholder="e.g. Team Atlas" className="booking-input" /></Field>
            <Field label="Lab node"><select required value={selectedNode} onChange={(event) => setSelectedNode(event.target.value)} className="booking-input"><option value="" disabled>Select an online node</option>{onlineNodes.map((node) => <option key={node.id} value={node.id}>{shortId(node.id)} · {node.cpuFree} CPU · {node.ramFreeMb} MB free</option>)}</select></Field>
            <div className="grid grid-cols-2 gap-3"><Field label="Starts"><input required type="datetime-local" value={startAt} onChange={(event) => setStartAt(event.target.value)} className="booking-input" /></Field><Field label="Ends"><input required type="datetime-local" value={endAt} onChange={(event) => setEndAt(event.target.value)} className="booking-input" /></Field></div>
            <div className="grid grid-cols-2 gap-3"><Field label="CPU cores"><input required min="1" type="number" value={cpuReq} onChange={(event) => setCpuReq(event.target.value)} className="booking-input" /></Field><Field label="RAM (MB)"><input required min="128" step="128" type="number" value={ramReqMb} onChange={(event) => setRamReqMb(event.target.value)} className="booking-input" /></Field></div>
            <button disabled={saving || !selectedNode} className="flex w-full items-center justify-center gap-2 rounded-xl bg-ember px-4 py-3 text-xs font-semibold text-white transition hover:bg-orange-500 disabled:cursor-not-allowed disabled:opacity-40">{saving ? <LoaderCircle size={14} className="animate-spin" /> : <CalendarClock size={14} />}{saving ? 'Reserving...' : 'Reserve resource'}</button>
          </form>
        </section>
      </div>
      {detailsNode && <ResourceDialog node={detailsNode} bookings={bookings.filter((booking) => booking.nodeId === detailsNode.id)} onClose={() => setDetailsNode(null)} onBook={() => { setSelectedNode(detailsNode.id); setDetailsNode(null) }} onCancel={removeBooking} />}
    </div>
  )
}

function Field({ label, children }) {
  return <label className="block"><span className="mb-1.5 block font-mono text-[10px] uppercase tracking-[0.12em] text-black/40 dark:text-white/40">{label}</span>{children}</label>
}

function ClusterNode({ node, bookings, onSelect }) {
  const online = node.status === 'ONLINE'
  const booked = bookings.some((booking) => new Date(booking.endAt) > new Date())
  const state = !online ? 'offline' : booked ? 'booked' : 'available'
  const dot = state === 'available' ? 'bg-emerald-500' : state === 'booked' ? 'bg-ember' : 'bg-black/20 dark:bg-white/20'
  return <button type="button" disabled={!online} onClick={() => onSelect(node)} className={`group relative min-h-[132px] rounded-2xl border p-3 text-left transition ${online ? 'cursor-pointer hover:-translate-y-1 hover:border-ember/50 hover:shadow-lg' : 'cursor-not-allowed opacity-55'} ${booked ? 'border-ember/30 bg-ember-soft/60 dark:bg-ember/10' : 'border-black/[0.08] bg-white dark:border-white/[0.08] dark:bg-[#2a2420]'}`}>
    <div className="flex items-start justify-between"><span className={`h-2.5 w-2.5 rounded-full ${dot}`} /><span className="font-mono text-[9px] uppercase tracking-[0.12em] text-black/35 dark:text-white/35">{state}</span></div>
    <div className="mt-6 flex items-center gap-2"><Radio size={14} className={online ? 'text-ember' : 'text-black/25 dark:text-white/25'} /><span className="font-mono text-xs font-semibold">{shortId(node.id)}</span></div>
    <div className="mt-2 font-mono text-[10px] text-black/40 dark:text-white/40">{online ? `${node.cpuFree ?? 0} CPU · ${node.ramFreeMb ?? 0} MB` : 'No heartbeat'}</div>
    {booked && <div className="absolute bottom-3 right-3 rounded-full bg-ember px-2 py-0.5 font-mono text-[8px] uppercase tracking-[0.1em] text-white">reserved</div>}
  </button>
}

function Legend({ color, label }) {
  return <span className="flex items-center gap-1.5 font-mono text-[10px] text-black/45 dark:text-white/45"><span className={`h-2 w-2 rounded-full ${color}`} />{label}</span>
}

function Capacity({ icon: Icon, label, value }) {
  return <div className="rounded-lg bg-ember-soft/60 px-3 py-2.5 dark:bg-ember/10"><div className="flex items-center gap-1.5 text-[10px] text-black/45 dark:text-white/45"><Icon size={13} />{label}</div><div className="mt-1 font-mono text-sm">{value}</div></div>
}

function ResourceDialog({ node, bookings, onClose, onBook, onCancel }) {
  const [copied, setCopied] = useState(false)
  const sshCommand = node.sshCommand || ''
  const copySshCommand = async () => {
    if (!sshCommand) return
    await navigator.clipboard.writeText(sshCommand)
    setCopied(true)
    window.setTimeout(() => setCopied(false), 1600)
  }

  return <div className="fixed inset-0 z-50 flex items-center justify-center bg-ink/35 p-5 backdrop-blur-sm" onMouseDown={onClose}>
    <section role="dialog" aria-modal="true" aria-label="Resource details" onMouseDown={(event) => event.stopPropagation()} className="w-full max-w-lg rounded-2xl border border-black/[0.08] bg-[#fffdfa] p-6 shadow-2xl dark:border-white/[0.1] dark:bg-[#211c19]">
      <div className="flex items-start justify-between gap-4"><div><div className="flex items-center gap-2 font-mono text-[10px] uppercase tracking-[0.16em] text-ember"><span className="h-2 w-2 rounded-full bg-emerald-500" />Online resource</div><h2 className="mt-2 font-display text-3xl">{shortId(node.id)}</h2><p className="mt-1 font-mono text-[11px] text-black/40 dark:text-white/40">{node.ip || 'No IP reported'}</p></div><button type="button" title="Close details" onClick={onClose} className="rounded-lg p-2 text-black/40 hover:bg-black/[0.05] dark:text-white/40 dark:hover:bg-white/[0.06]"><X size={17} /></button></div>
      <div className="mt-6 grid grid-cols-2 gap-3"><Capacity icon={Cpu} label="CPU available" value={`${node.cpuFree ?? 0} cores`} /><Capacity icon={HardDrive} label="RAM available" value={`${node.ramFreeMb ?? 0} MB`} /></div>
      <div className="mt-5 rounded-xl border border-black/[0.08] bg-[#171412] p-3 dark:border-white/[0.1]"><div className="mb-2 flex items-center justify-between"><span className="font-mono text-[10px] uppercase tracking-[0.14em] text-white/45">SSH access</span>{sshCommand && <button type="button" onClick={copySshCommand} className="flex items-center gap-1.5 font-mono text-[10px] text-orange-300 hover:text-orange-200">{copied ? <Check size={12} /> : <Copy size={12} />}{copied ? 'Copied' : 'Copy command'}</button>}</div>{sshCommand ? <code className="block break-all font-mono text-xs leading-5 text-white/85">{sshCommand}</code> : <p className="text-xs leading-5 text-white/45">SSH access has not been configured for this node.</p>}</div>
      <div className="mt-6 border-t border-black/[0.08] pt-5 dark:border-white/[0.08]"><div className="mb-3 font-mono text-[10px] uppercase tracking-[0.14em] text-black/40 dark:text-white/40">Reservation schedule</div>{bookings.length ? <div className="space-y-2">{bookings.map((booking) => <div key={booking.id} className="flex items-center justify-between gap-3 rounded-lg bg-black/[0.03] px-3 py-2 text-xs dark:bg-white/[0.04]"><div><div className="font-medium">{booking.requester}</div><div className="mt-1 text-black/45 dark:text-white/45">{formatDate(booking.startAt)} - {formatDate(booking.endAt)}</div><div className="mt-1 font-mono text-[10px] text-black/35 dark:text-white/35">{booking.cpuReq} CPU · {booking.ramReqMb} MB</div></div><button type="button" title="Cancel booking" onClick={() => onCancel(booking.id)} className="text-black/35 transition hover:text-red-500 dark:text-white/35"><Trash2 size={14} /></button></div>)}</div> : <p className="text-xs text-black/40 dark:text-white/40">No reservations for this resource.</p>}</div>
      <button type="button" onClick={onBook} className="mt-6 flex w-full items-center justify-center gap-2 rounded-xl bg-ember px-4 py-3 text-xs font-semibold text-white hover:bg-orange-500"><CalendarClock size={14} /> Use this resource</button>
    </section>
  </div>
}