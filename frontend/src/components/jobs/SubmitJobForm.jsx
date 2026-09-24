import { Play, Terminal } from 'lucide-react'
import { useState } from 'react'
import { useApp } from '../../context/AppContext'

const initialForm = { command: 'echo hello from idlegrid', cpuReq: 1, ramReqMb: 512 }

export default function SubmitJobForm() {
  const { submitJob } = useApp()
  const [form, setForm] = useState(initialForm)
  const [submitting, setSubmitting] = useState(false)
  const [message, setMessage] = useState('')

  const update = (event) => setForm((current) => ({ ...current, [event.target.name]: event.target.value }))
  const onSubmit = async (event) => {
    event.preventDefault()
    setSubmitting(true)
    setMessage('')
    try {
      const result = await submitJob({ ...form, cpuReq: Number(form.cpuReq), ramReqMb: Number(form.ramReqMb) })
      setMessage(`Queued ${result.jobId?.slice(0, 8) || 'job'}...`)
      setForm(initialForm)
    } catch (error) {
      setMessage(error.message || 'Could not submit job')
    } finally {
      setSubmitting(false)
    }
  }

  return <form onSubmit={onSubmit} className="rounded-2xl border border-black/[0.07] bg-[#211c19] p-5 text-white shadow-soft dark:border-white/[0.08] sm:p-6"><div className="flex items-center gap-3"><div className="flex h-9 w-9 items-center justify-center rounded-xl bg-ember"><Terminal size={17} /></div><div><h2 className="font-display text-2xl">Start a job</h2><p className="text-xs text-white/45">Borrow a little capacity from the grid.</p></div></div><div className="mt-6"><label className="mb-2 block font-mono text-[10px] uppercase tracking-[0.14em] text-white/45" htmlFor="command">Command</label><input id="command" name="command" value={form.command} onChange={update} className="w-full rounded-xl border border-white/10 bg-white/[0.06] px-3.5 py-3 font-mono text-sm text-white outline-none transition placeholder:text-white/25 focus:border-ember" required /></div><div className="mt-4 grid grid-cols-2 gap-3"><Field label="CPU cores" name="cpuReq" value={form.cpuReq} onChange={update} min="0.1" step="0.1" type="number" /><Field label="Memory (MB)" name="ramReqMb" value={form.ramReqMb} onChange={update} min="128" step="128" type="number" /></div><div className="mt-5 flex items-center justify-between gap-3"><span className="text-xs text-white/45">{message}</span><button disabled={submitting} className="flex items-center gap-2 rounded-xl bg-ember px-4 py-2.5 text-xs font-semibold transition hover:bg-orange-500 disabled:cursor-wait disabled:opacity-60"><Play size={14} fill="currentColor" />{submitting ? 'Sending...' : 'Queue job'}</button></div></form>
}

function Field({ label, ...props }) { return <div><label className="mb-2 block font-mono text-[10px] uppercase tracking-[0.14em] text-white/45" htmlFor={props.name}>{label}</label><input {...props} className="w-full rounded-xl border border-white/10 bg-white/[0.06] px-3.5 py-2.5 font-mono text-sm text-white outline-none transition focus:border-ember" /></div> }
