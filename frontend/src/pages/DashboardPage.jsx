import { ArrowUpRight, Cpu, HardDrive, Layers3, Server } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useApp } from '../context/AppContext'
import NodeCard from '../components/dashboard/NodeCard'
import StatCard from '../components/dashboard/StatCard'

export default function DashboardPage() {
  const { nodes, onlineNodes, loading, error } = useApp()
  const totalRam = onlineNodes.reduce((sum, node) => sum + (node.ramFreeMb || 0), 0)
  const totalCpu = onlineNodes.reduce((sum, node) => sum + (node.cpuFree || 0), 0)

  return (
    <div className="animate-fade-in">
      <section className="relative overflow-hidden rounded-[28px] border border-ember/15 bg-ember-soft px-6 py-10 dark:border-ember/20 dark:bg-[#2a1d16] sm:px-10 sm:py-14">
        <div className="pointer-events-none absolute -right-20 -top-28 h-72 w-72 rounded-full border-[36px] border-ember/10" />
        <div className="relative max-w-2xl">
          <div className="mb-4 flex items-center gap-2 font-mono text-[10px] uppercase tracking-[0.18em] text-ember"><span className="h-1.5 w-1.5 rounded-full bg-ember" /> About IdleGrid</div>
          <h1 className="font-display text-5xl leading-[0.98] tracking-tight text-ink dark:text-[#fff8f0] sm:text-7xl">Shared capacity, thoughtfully used.</h1>
          <p className="mt-6 max-w-xl text-sm leading-7 text-black/60 dark:text-white/60">IdleGrid turns the quiet moments of campus lab computers into useful compute. It gives student workloads a place to run while respecting the people who use those machines every day.</p>
          <Link to="/jobs" className="mt-8 inline-flex items-center gap-2 rounded-xl bg-ink px-4 py-3 text-xs font-semibold text-white transition hover:bg-black dark:bg-white dark:text-ink dark:hover:bg-white/90">Open job workspace <ArrowUpRight size={15} /></Link>
        </div>
      </section>

      {error && <div className="mt-6 rounded-xl border border-red-500/20 bg-red-500/5 px-4 py-3 text-xs text-red-600 dark:text-red-400">{error}. Start the Master service to see live capacity.</div>}

      <section className="mt-10 grid gap-4 sm:grid-cols-3">
        <StatCard label="Online nodes" value={onlineNodes.length} detail={`${nodes.length} registered`} icon={Server} accent />
        <StatCard label="CPU available" value={totalCpu} detail="cores across grid" icon={Cpu} />
        <StatCard label="RAM available" value={`${Math.round(totalRam / 1024 * 10) / 10} GB`} detail="for new work" icon={HardDrive} />
      </section>

      <section className="mt-12">
        <div className="mb-5 flex items-end justify-between"><div><div className="mb-2 flex items-center gap-2 text-ember"><Layers3 size={17} /><span className="font-mono text-[10px] uppercase tracking-[0.16em]">The network</span></div><h2 className="font-display text-3xl">A calm view of the grid.</h2><p className="mt-2 text-sm text-black/45 dark:text-white/45">Each machine reports its available capacity through a lightweight heartbeat.</p></div><span className="font-mono text-[10px] uppercase tracking-[0.14em] text-black/30 dark:text-white/30">Refresh / 3 sec</span></div>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">{loading && !nodes.length ? <div className="col-span-full rounded-2xl border border-dashed border-black/10 px-5 py-14 text-center text-sm text-black/40 dark:border-white/10 dark:text-white/40">Connecting to the grid...</div> : nodes.length ? nodes.map((node) => <NodeCard key={node.id || node.nodeId} node={node} />) : <div className="col-span-full rounded-2xl border border-dashed border-black/10 px-5 py-14 text-center text-sm text-black/40 dark:border-white/10 dark:text-white/40">No nodes have sent a heartbeat yet.</div>}</div>
      </section>
    </div>
  )
}
