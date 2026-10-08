import { CalendarClock, Check, Copy, Cpu, HardDrive, LoaderCircle, Radio, Terminal, X } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { idleGridApi } from '../api/idleGridApi'
import { useApp } from '../context/AppContext'

const DURATION_OPTIONS = [
  { label: '30 minutes', value: 30 },
  { label: '1 hour',    value: 60 },
  { label: '2 hours',   value: 120 },
  { label: '4 hours',   value: 240 },
  { label: '8 hours',   value: 480 },
  { label: '24 hours (max)', value: 1440 },
]

function shortId(id = '') {
  return id.length > 14 ? `${id.slice(0, 8)}…${id.slice(-4)}` : id
}

// ─── Main Page ──────────────────────────────────────────────────────────────
export default function BookingPage() {
  const { nodes, refresh } = useApp()
  const [selectedNode, setSelectedNode] = useState(null)

  const onlineNodes = nodes.filter((n) => n.status === 'ONLINE')

  return (
    <div className="animate-fade-in">
      <div className="mb-8 max-w-2xl">
        <div className="mb-3 flex items-center gap-2 font-mono text-[10px] uppercase tracking-[0.18em] text-ember">
          <CalendarClock size={14} /> Book a Lab PC
        </div>
        <h1 className="font-display text-4xl leading-none sm:text-5xl">
          Pick a PC, get your SSH key.
        </h1>
        <p className="mt-3 text-sm leading-6 text-black/50 dark:text-white/50">
          Choose an online lab PC, set your session duration, and get an instant SSH connection
          into a resource-capped container — all while the PC owner keeps working normally.
        </p>
      </div>

      {/* Node grid */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {!nodes.length && (
          <div className="col-span-full rounded-2xl border border-dashed border-black/10 px-5 py-14 text-center text-sm text-black/40 dark:border-white/10 dark:text-white/40">
            Waiting for lab PCs to connect…
          </div>
        )}
        {nodes.map((node) => (
          <NodeCard
            key={node.id}
            node={node}
            onBook={() => setSelectedNode(node)}
          />
        ))}
      </div>

      {/* Booking modal */}
      {selectedNode && (
        <BookingModal
          node={selectedNode}
          onClose={() => { setSelectedNode(null); refresh() }}
        />
      )}
    </div>
  )
}

// ─── Node Card ──────────────────────────────────────────────────────────────
function NodeCard({ node, onBook }) {
  const online = node.status === 'ONLINE'
  const ramTotalEst = 16384
  const cpuUsedPct  = Math.max(0, Math.min(100, 100 - (node.cpuFree ?? 0)))
  const ramUsedPct  = Math.max(0, Math.min(100, Math.round((ramTotalEst - (node.ramFreeMb ?? 0)) / ramTotalEst * 100)))

  return (
    <div className={`relative overflow-hidden rounded-2xl border p-5 transition
      ${online
        ? 'border-emerald-500/25 bg-white hover:-translate-y-1 hover:shadow-lg dark:bg-[#1a2210]'
        : 'border-black/[0.08] bg-black/[0.02] opacity-60 dark:border-white/[0.08] dark:bg-[#1a1a1a]'}`}>

      {/* top accent bar */}
      <div className={`absolute left-0 right-0 top-0 h-[3px] rounded-t-2xl ${online ? 'bg-gradient-to-r from-emerald-400 to-emerald-600' : 'bg-black/10 dark:bg-white/10'}`} />

      <div className="flex items-start justify-between">
        <div>
          <div className="font-mono text-[10px] uppercase tracking-[0.12em] text-black/35 dark:text-white/35">Node</div>
          <div className="mt-1 font-mono text-sm font-bold" title={node.id}>{shortId(node.id)}</div>
          <div className="mt-0.5 font-mono text-xs text-emerald-600 dark:text-emerald-400">{node.ip || '—'}</div>
        </div>
        <div className={`flex items-center gap-1.5 rounded-full border px-2.5 py-1 font-mono text-[9px] uppercase tracking-[0.1em]
          ${online ? 'border-emerald-500/30 text-emerald-600 dark:text-emerald-400' : 'border-black/10 text-black/35 dark:border-white/10 dark:text-white/35'}`}>
          <span className={`h-1.5 w-1.5 rounded-full ${online ? 'bg-emerald-500' : 'bg-black/25 dark:bg-white/25'}`} />
          {node.status}
        </div>
      </div>

      {/* Resource bars */}
      <div className="mt-5 space-y-3">
        <ResourceBar label="CPU" value={`${node.cpuFree ?? 0} cores free`} pct={cpuUsedPct} />
        <ResourceBar label="RAM" value={`${node.ramFreeMb ?? 0} MB free`} pct={ramUsedPct} />
      </div>

      <button
        disabled={!online}
        onClick={onBook}
        className="mt-5 flex w-full items-center justify-center gap-2 rounded-xl border border-emerald-500/40 py-2.5 font-mono text-[11px] font-bold uppercase tracking-[0.08em] text-emerald-600 transition hover:bg-emerald-500 hover:text-white disabled:cursor-not-allowed disabled:opacity-35 dark:text-emerald-400 dark:hover:text-white"
      >
        <Radio size={13} /> {online ? 'Book this PC' : 'Offline'}
      </button>
    </div>
  )
}

function ResourceBar({ label, value, pct }) {
  return (
    <div>
      <div className="mb-1.5 flex justify-between font-mono text-[10px] text-black/40 dark:text-white/40">
        <span>{label}</span><span>{value}</span>
      </div>
      <div className="h-[5px] overflow-hidden rounded-full bg-black/[0.06] dark:bg-white/[0.08]">
        <div
          className="h-full rounded-full bg-gradient-to-r from-emerald-400 to-teal-500 transition-all duration-700"
          style={{ width: `${pct}%` }}
        />
      </div>
    </div>
  )
}

// ─── Booking Modal ────────────────────────────────────────────────────────
function BookingModal({ node, onClose }) {
  const [step, setStep] = useState('form') // 'form' | 'waiting' | 'ready'
  const [durationMinutes, setDurationMinutes] = useState(60)
  const [cpuReq, setCpuReq] = useState(1)
  const [ramReqMb, setRamReqMb] = useState(512)
  const [jobId, setJobId] = useState(null)
  const [sshCommand, setSshCommand] = useState('')
  const [error, setError] = useState('')
  const [copied, setCopied] = useState(false)
  const pollRef = useRef(null)

  useEffect(() => () => { if (pollRef.current) clearInterval(pollRef.current) }, [])

  const submit = async () => {
    setError('')
    try {
      const res = await idleGridApi.submitJob({
        cpuReq: Number(cpuReq),
        ramReqMb: Number(ramReqMb),
        command: '',
        targetNodeId: node.id,
        durationMinutes: Number(durationMinutes),
      })
      setJobId(res.jobId)
      setStep('waiting')

      // Poll until RUNNING
      pollRef.current = setInterval(async () => {
        try {
          const job = await idleGridApi.getJobStatus(res.jobId)
          if (job.status === 'RUNNING' && job.nodeIp && job.sshPort) {
            clearInterval(pollRef.current)
            setSshCommand(`ssh student@${job.nodeIp} -p ${job.sshPort}`)
            setStep('ready')
          } else if (job.status === 'FAILED') {
            clearInterval(pollRef.current)
            setError('Job failed. Check agent logs on the Windows PC.')
            setStep('form')
          }
        } catch (_) {}
      }, 2000)
    } catch (e) {
      setError(e.message || 'Failed to submit booking')
    }
  }

  const copy = async () => {
    await navigator.clipboard.writeText(sshCommand)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-5 backdrop-blur-sm"
      onMouseDown={onClose}
    >
      <div
        role="dialog"
        aria-modal="true"
        onMouseDown={(e) => e.stopPropagation()}
        className="w-full max-w-md rounded-2xl border border-black/[0.09] bg-white p-6 shadow-2xl dark:border-white/[0.1] dark:bg-[#1c1c22]"
      >
        <div className="flex items-center justify-between">
          <h2 className="font-display text-2xl">
            {step === 'ready' ? '🟢 Session Ready' : 'Book a PC'}
          </h2>
          <button onClick={onClose} className="rounded-lg p-1.5 text-black/35 hover:bg-black/[0.06] dark:text-white/35 dark:hover:bg-white/[0.06]">
            <X size={17} />
          </button>
        </div>
        <p className="mt-1 font-mono text-[11px] text-black/40 dark:text-white/40">
          {node.ip} · {shortId(node.id)}
        </p>

        {/* STEP 1: form */}
        {step === 'form' && (
          <div className="mt-6 space-y-4">
            <Field label="Duration">
              <select
                value={durationMinutes}
                onChange={(e) => setDurationMinutes(Number(e.target.value))}
                className="booking-input"
              >
                {DURATION_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>{o.label}</option>
                ))}
              </select>
            </Field>
            <div className="grid grid-cols-2 gap-3">
              <Field label="CPU cores">
                <input type="number" min="1" max="8" value={cpuReq}
                  onChange={(e) => setCpuReq(e.target.value)} className="booking-input" />
              </Field>
              <Field label="RAM (MB)">
                <input type="number" min="256" step="256" value={ramReqMb}
                  onChange={(e) => setRamReqMb(e.target.value)} className="booking-input" />
              </Field>
            </div>

            {error && <p className="text-xs text-red-500">{error}</p>}

            <button
              onClick={submit}
              className="flex w-full items-center justify-center gap-2 rounded-xl bg-ember px-4 py-3 text-xs font-semibold text-white transition hover:bg-orange-500"
            >
              <Terminal size={14} /> Book &amp; Connect
            </button>
          </div>
        )}

        {/* STEP 2: waiting */}
        {step === 'waiting' && (
          <div className="mt-8 flex flex-col items-center gap-4 py-4 text-center">
            <LoaderCircle size={36} className="animate-spin text-ember" />
            <div>
              <p className="font-semibold">Spinning up your container…</p>
              <p className="mt-1 text-xs text-black/45 dark:text-white/45">
                The first run may take 1–2 min while the image downloads.
              </p>
              <p className="mt-3 font-mono text-[10px] text-black/30 dark:text-white/30">Job: {jobId?.slice(0, 8)}</p>
            </div>
          </div>
        )}

        {/* STEP 3: ready */}
        {step === 'ready' && (
          <div className="mt-6">
            <div className="rounded-xl border border-emerald-500/30 bg-emerald-500/5 p-4">
              <div className="mb-2 flex items-center gap-2 font-mono text-[10px] uppercase tracking-[0.12em] text-emerald-600 dark:text-emerald-400">
                <Terminal size={12} /> SSH Command
              </div>
              <code className="block break-all font-mono text-sm leading-6 text-black dark:text-white">
                {sshCommand}
              </code>
              <button
                onClick={copy}
                className="mt-3 flex w-full items-center justify-center gap-2 rounded-lg border border-emerald-500/40 py-2 font-mono text-[11px] font-bold text-emerald-600 transition hover:bg-emerald-500 hover:text-white dark:text-emerald-400"
              >
                {copied ? <><Check size={13} /> Copied!</> : <><Copy size={13} /> Copy command</>}
              </button>
            </div>
            <p className="mt-4 text-center text-xs text-black/40 dark:text-white/40">
              Session runs for {durationMinutes} min · No password required
            </p>
          </div>
        )}
      </div>
    </div>
  )
}

function Field({ label, children }) {
  return (
    <label className="block">
      <span className="mb-1.5 block font-mono text-[10px] uppercase tracking-[0.12em] text-black/40 dark:text-white/40">{label}</span>
      {children}
    </label>
  )
}