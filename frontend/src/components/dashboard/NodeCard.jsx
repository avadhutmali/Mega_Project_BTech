import { Cpu, HardDrive, Radio } from 'lucide-react'

function formatNodeId(id = '') {
  return id.length > 12 ? `${id.slice(0, 8)}...${id.slice(-4)}` : id
}

export default function NodeCard({ node }) {
  const online = node.status === 'ONLINE'
  const cpuPercent = node.cpuTotal ? Math.round(((node.cpuTotal - node.cpuFree) / node.cpuTotal) * 100) : 0
  const ramPercent = node.ramTotalMb ? Math.round(((node.ramTotalMb - node.ramFreeMb) / node.ramTotalMb) * 100) : 0
  return (
    <article className="group rounded-2xl border border-black/[0.07] bg-white p-5 shadow-soft transition hover:-translate-y-0.5 dark:border-white/[0.08] dark:bg-[#211c19]">
      <div className="flex items-start justify-between">
        <div className="flex items-center gap-2.5"><div className={`h-2 w-2 rounded-full ${online ? 'bg-emerald-500 shadow-[0_0_0_4px_rgba(16,185,129,0.1)]' : 'bg-black/20 dark:bg-white/20'}`} /><span className="font-mono text-xs font-medium">{formatNodeId(node.id || node.nodeId)}</span></div>
        <Radio size={15} className={online ? 'text-emerald-500' : 'text-black/20 dark:text-white/20'} />
      </div>
      <div className="mt-2 font-mono text-[10px] text-black/35 dark:text-white/35">{node.ip || 'No heartbeat yet'}</div>
      <div className="mt-6 space-y-3">
        <ResourceBar icon={Cpu} label="CPU free" value={`${node.cpuFree ?? 0} / ${node.cpuTotal ?? 0}`} percent={100 - cpuPercent} />
        <ResourceBar icon={HardDrive} label="RAM free" value={`${node.ramFreeMb ?? 0} MB`} percent={100 - ramPercent} />
      </div>
      <div className={`mt-5 inline-flex rounded-full border px-2.5 py-1 font-mono text-[9px] uppercase tracking-[0.14em] ${online ? 'border-emerald-500/20 text-emerald-600 dark:text-emerald-400' : 'border-black/10 text-black/35 dark:border-white/10 dark:text-white/35'}`}>{node.status || 'UNKNOWN'}</div>
    </article>
  )
}

function ResourceBar({ icon: Icon, label, value, percent }) {
  return <div><div className="mb-1.5 flex items-center justify-between text-[11px]"><span className="flex items-center gap-1.5 text-black/45 dark:text-white/45"><Icon size={13} />{label}</span><span className="font-mono text-[10px] text-black/55 dark:text-white/55">{value}</span></div><div className="h-1 overflow-hidden rounded-full bg-black/[0.06] dark:bg-white/[0.08]"><div className="h-full rounded-full bg-ember transition-all" style={{ width: `${Math.max(4, percent)}%` }} /></div></div>
}
