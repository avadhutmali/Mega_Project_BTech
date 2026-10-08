import { ArrowUpRight, CheckCircle2, CircleDashed, Clock3, Copy, LoaderCircle, Terminal, XCircle } from 'lucide-react'
import { useState } from 'react'

const statusMap = {
  QUEUED:   { icon: CircleDashed, className: 'text-black/45 dark:text-white/45' },
  ASSIGNED: { icon: Clock3,       className: 'text-amber-600 dark:text-amber-400' },
  RUNNING:  { icon: LoaderCircle, className: 'text-blue-600 dark:text-blue-400' },
  DONE:     { icon: CheckCircle2, className: 'text-emerald-600 dark:text-emerald-400' },
  FAILED:   { icon: XCircle,      className: 'text-red-600 dark:text-red-400' },
}

export default function JobTable({ jobs, compact = false }) {
  const visibleJobs = compact ? jobs.slice(0, 5) : [...jobs].sort((a, b) => {
    const order = { RUNNING: 0, ASSIGNED: 1, QUEUED: 2, DONE: 3, FAILED: 4 }
    return (order[a.status] ?? 5) - (order[b.status] ?? 5)
  })

  return (
    <div className="overflow-hidden rounded-2xl border border-black/[0.07] bg-white dark:border-white/[0.08] dark:bg-[#211c19]">
      <div className="grid grid-cols-[1.4fr_.7fr_.7fr_1fr_1.4fr] gap-4 border-b border-black/[0.07] px-5 py-3 font-mono text-[9px] uppercase tracking-[0.14em] text-black/35 dark:border-white/[0.08] dark:text-white/35">
        <span>Job</span><span>Status</span><span>Resources</span><span>Node IP</span><span>SSH</span>
      </div>
      {visibleJobs.length
        ? visibleJobs.map((job) => <JobRow key={job.id} job={job} />)
        : <div className="px-5 py-12 text-center text-sm text-black/40 dark:text-white/40">No jobs have entered the grid yet.</div>
      }
    </div>
  )
}

function JobRow({ job }) {
  const status = statusMap[job.status] || statusMap.QUEUED
  const Icon = status.icon
  const [copied, setCopied] = useState(false)

  const sshCmd = (job.status === 'RUNNING' && job.nodeIp && job.sshPort)
    ? `ssh student@${job.nodeIp} -p ${job.sshPort}`
    : null

  const copy = async () => {
    if (!sshCmd) return
    await navigator.clipboard.writeText(sshCmd)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <div className="grid grid-cols-[1.4fr_.7fr_.7fr_1fr_1.4fr] items-center gap-4 border-b border-black/[0.05] px-5 py-4 last:border-0 dark:border-white/[0.06]">
      {/* Job ID + command */}
      <div className="min-w-0">
        <div className="flex items-center gap-2 font-mono text-xs font-medium">
          <span className="truncate">{job.id?.slice(0, 12)}</span>
          <ArrowUpRight size={12} className="shrink-0 text-black/25 dark:text-white/25" />
        </div>
        <div className="mt-1 truncate text-xs text-black/40 dark:text-white/40">
          {job.durationMinutes > 0 ? `SSH session · ${job.durationMinutes} min` : job.command}
        </div>
      </div>

      {/* Status */}
      <div className={`flex items-center gap-1.5 font-mono text-[10px] ${status.className}`}>
        <Icon size={13} className={job.status === 'RUNNING' ? 'animate-spin' : ''} />
        {job.status}
      </div>

      {/* Resources */}
      <div className="font-mono text-[10px] text-black/50 dark:text-white/50">
        {job.cpuReq} CPU / {job.ramReqMb} MB
      </div>

      {/* Node IP */}
      <div className="truncate font-mono text-[10px] text-black/45 dark:text-white/45">
        {job.nodeIp || job.assignedNode?.slice(0, 12) || 'waiting…'}
      </div>

      {/* SSH */}
      <div>
        {sshCmd ? (
          <button
            onClick={copy}
            title={sshCmd}
            className="flex items-center gap-1.5 rounded-lg border border-emerald-500/30 bg-emerald-500/5 px-2.5 py-1.5 font-mono text-[10px] text-emerald-700 transition hover:bg-emerald-500/15 dark:text-emerald-400"
          >
            {copied ? '✓ Copied' : <><Terminal size={11} /> {`…-p ${job.sshPort}`}</>}
            {!copied && <Copy size={10} className="ml-1 opacity-50" />}
          </button>
        ) : (
          <span className="font-mono text-[10px] text-black/25 dark:text-white/25">—</span>
        )}
      </div>
    </div>
  )
}
