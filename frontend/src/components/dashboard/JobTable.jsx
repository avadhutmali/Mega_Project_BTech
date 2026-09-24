import { ArrowUpRight, CheckCircle2, CircleDashed, Clock3, LoaderCircle, XCircle } from 'lucide-react'

const statusMap = {
  QUEUED: { icon: CircleDashed, className: 'text-black/45 dark:text-white/45' },
  ASSIGNED: { icon: Clock3, className: 'text-amber-600 dark:text-amber-400' },
  RUNNING: { icon: LoaderCircle, className: 'text-blue-600 dark:text-blue-400' },
  DONE: { icon: CheckCircle2, className: 'text-emerald-600 dark:text-emerald-400' },
  FAILED: { icon: XCircle, className: 'text-red-600 dark:text-red-400' },
}

export default function JobTable({ jobs, compact = false }) {
  const visibleJobs = compact ? jobs.slice(0, 5) : jobs
  return <div className="overflow-hidden rounded-2xl border border-black/[0.07] bg-white dark:border-white/[0.08] dark:bg-[#211c19]"><div className="grid grid-cols-[1.4fr_.7fr_.7fr_1fr] gap-4 border-b border-black/[0.07] px-5 py-3 font-mono text-[9px] uppercase tracking-[0.14em] text-black/35 dark:border-white/[0.08] dark:text-white/35"><span>Job</span><span>Status</span><span>Resources</span><span>Node</span></div>{visibleJobs.length ? visibleJobs.map((job) => <JobRow key={job.id} job={job} />) : <div className="px-5 py-12 text-center text-sm text-black/40 dark:text-white/40">No jobs have entered the grid yet.</div>}</div>
}

function JobRow({ job }) {
  const status = statusMap[job.status] || statusMap.QUEUED
  const Icon = status.icon
  return <div className="grid grid-cols-[1.4fr_.7fr_.7fr_1fr] items-center gap-4 border-b border-black/[0.05] px-5 py-4 last:border-0 dark:border-white/[0.06]"><div className="min-w-0"><div className="flex items-center gap-2 font-mono text-xs font-medium"><span className="truncate">{job.id?.slice(0, 12)}</span><ArrowUpRight size={12} className="shrink-0 text-black/25 dark:text-white/25" /></div><div className="mt-1 truncate text-xs text-black/40 dark:text-white/40">{job.command}</div></div><div className={`flex items-center gap-1.5 font-mono text-[10px] ${status.className}`}><Icon size={13} className={job.status === 'RUNNING' ? 'animate-spin' : ''} />{job.status}</div><div className="font-mono text-[10px] text-black/50 dark:text-white/50">{job.cpuReq} CPU / {job.ramReqMb} MB</div><div className="truncate font-mono text-[10px] text-black/45 dark:text-white/45">{job.assignedNode?.slice(0, 12) || 'Waiting for node'}</div></div>
}
