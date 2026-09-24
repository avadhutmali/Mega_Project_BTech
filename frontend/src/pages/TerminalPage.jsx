import { Check, ChevronRight, CircleStop, LoaderCircle, Play, RotateCcw, TerminalSquare, X } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { idleGridApi } from '../api/idleGridApi'

const DEFAULT_COMMAND = 'echo hello from idlegrid'

export default function TerminalPage() {
  const [command, setCommand] = useState('')
  const [lines, setLines] = useState([
    { type: 'system', text: 'IdleGrid terminal / batch bridge' },
    { type: 'system', text: 'Commands run in a capped, network-isolated Docker container.' },
  ])
  const [activeJob, setActiveJob] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const inputRef = useRef(null)
  const outputRef = useRef(null)

  useEffect(() => {
    inputRef.current?.focus()
  }, [])

  useEffect(() => {
    if (!activeJob) return undefined

    let cancelled = false
    const poll = async () => {
      try {
        const job = await idleGridApi.getJobStatus(activeJob.id)
        if (cancelled) return
        if (job.status === 'DONE' || job.status === 'FAILED') {
          setLines((current) => [
            ...current,
            { type: job.status === 'DONE' ? 'output' : 'error', text: job.result || '(no output)' },
            { type: 'status', text: `${job.status.toLowerCase()} / exit ${job.exitCode ?? (job.status === 'DONE' ? 0 : 1)}` },
          ])
          setActiveJob(null)
        } else {
          setActiveJob((current) => current ? { ...current, status: job.status } : current)
        }
      } catch (error) {
        if (!cancelled) {
          setLines((current) => [...current, { type: 'error', text: error.message || 'Status request failed' }])
          setActiveJob(null)
        }
      }
    }

    poll()
    const interval = window.setInterval(poll, 1200)
    return () => {
      cancelled = true
      window.clearInterval(interval)
    }
  }, [activeJob?.id])

  useEffect(() => {
    if (outputRef.current) outputRef.current.scrollTop = outputRef.current.scrollHeight
  }, [lines, activeJob])

  const runCommand = async () => {
    const nextCommand = command.trim()
    if (!nextCommand || submitting || activeJob) return

    setSubmitting(true)
    setLines((current) => [...current, { type: 'command', text: nextCommand }])
    setCommand('')
    try {
      const result = await idleGridApi.submitJob({ command: nextCommand, cpuReq: 1, ramReqMb: 512 })
      setActiveJob({ id: result.jobId, status: 'QUEUED' })
      setLines((current) => [...current, { type: 'status', text: `queued / ${result.jobId}` }])
    } catch (error) {
      setLines((current) => [...current, { type: 'error', text: error.message || 'Could not queue command' }])
    } finally {
      setSubmitting(false)
    }
  }

  const onKeyDown = (event) => {
    if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
      event.preventDefault()
      runCommand()
    }
  }

  const clearTerminal = () => {
    setLines([])
    inputRef.current?.focus()
  }

  return (
    <div className="animate-fade-in">
      <div className="mb-8 flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
        <div>
          <Link to="/" className="mb-4 flex items-center gap-2 text-xs text-black/45 hover:text-ember dark:text-white/45"><ChevronRight size={14} className="rotate-180" /> Overview</Link>
          <div className="mb-3 flex items-center gap-2 font-mono text-[10px] uppercase tracking-[0.18em] text-ember"><TerminalSquare size={14} /> Command workspace</div>
          <h1 className="font-display text-4xl leading-none sm:text-5xl">A quiet place to run things.</h1>
          <p className="mt-3 max-w-xl text-sm leading-6 text-black/50 dark:text-white/50">Try a command on shared capacity. Output appears here when the assigned container completes.</p>
        </div>
        <button type="button" onClick={clearTerminal} className="flex items-center gap-2 self-start rounded-lg px-3 py-2 text-xs text-black/45 transition hover:bg-black/[0.04] hover:text-ink dark:text-white/45 dark:hover:bg-white/[0.05] dark:hover:text-white sm:self-auto"><RotateCcw size={14} /> Clear</button>
      </div>

      <section className="overflow-hidden rounded-2xl border border-black/[0.08] bg-[#171412] shadow-soft dark:border-white/[0.1]">
        <div className="flex items-center justify-between border-b border-white/[0.08] px-4 py-3"><div className="flex items-center gap-2"><span className="h-2 w-2 rounded-full bg-red-400/80" /><span className="h-2 w-2 rounded-full bg-yellow-400/80" /><span className="h-2 w-2 rounded-full bg-green-400/80" /><span className="ml-2 font-mono text-[10px] text-white/35">idlegrid / shell</span></div><span className="font-mono text-[10px] uppercase tracking-[0.14em] text-white/30">sandboxed</span></div>
        <div ref={outputRef} className="h-[min(52vh,520px)] overflow-y-auto px-5 py-5 font-mono text-xs leading-6 text-[#f8f3ed] sm:px-7 sm:text-sm">
          {lines.map((line, index) => <TerminalLine key={`${index}-${line.text}`} line={line} />)}
          {activeJob && <div className="mt-1 flex items-center gap-2 text-orange-300"><LoaderCircle size={13} className="animate-spin" /> {activeJob.status.toLowerCase()} / waiting for container output...</div>}
        </div>
        <div className="border-t border-white/[0.08] p-4 sm:p-5">
          <div className="flex items-start gap-3"><span className="mt-3 font-mono text-sm text-ember">$</span><textarea ref={inputRef} value={command} onChange={(event) => setCommand(event.target.value)} onKeyDown={onKeyDown} placeholder={DEFAULT_COMMAND} rows={2} disabled={Boolean(activeJob)} className="min-h-[52px] flex-1 resize-none bg-transparent font-mono text-sm leading-6 text-white outline-none placeholder:text-white/25 disabled:cursor-not-allowed disabled:opacity-40" /></div>
          <div className="mt-3 flex items-center justify-between gap-3"><span className="font-mono text-[10px] text-white/30">Ctrl + Enter to run · 1 CPU · 512 MB</span><button type="button" onClick={runCommand} disabled={!command.trim() || submitting || Boolean(activeJob)} className="flex items-center gap-2 rounded-xl bg-ember px-4 py-2.5 text-xs font-semibold text-white transition hover:bg-orange-500 disabled:cursor-not-allowed disabled:opacity-40"><Play size={13} fill="currentColor" />{submitting ? 'Queueing' : activeJob ? 'Running' : 'Run command'}</button></div>
        </div>
      </section>

      <div className="mt-5 flex items-start gap-3 rounded-xl border border-amber-500/15 bg-amber-500/5 px-4 py-3 text-xs leading-5 text-black/50 dark:text-white/50"><CircleStop size={15} className="mt-0.5 shrink-0 text-amber-600 dark:text-amber-400" />This first version runs commands as short-lived jobs and shows output after completion. Live stdin/stdout streaming will be added as the Agent-WebSocket transport.</div>
    </div>
  )
}

function TerminalLine({ line }) {
  const className = {
    system: 'text-white/40',
    command: 'mt-4 text-orange-300',
    output: 'whitespace-pre-wrap text-white/85',
    error: 'whitespace-pre-wrap text-red-300',
    status: 'text-emerald-300/75',
  }[line.type]
  const Icon = line.type === 'status' ? Check : line.type === 'error' ? X : null
  return <div className={className}>{Icon && <Icon size={13} className="mr-1 inline" />}{line.type === 'command' && <span className="mr-2 text-ember">$</span>}{line.text}</div>
}
