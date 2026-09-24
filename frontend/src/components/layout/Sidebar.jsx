import { Activity, Command, LayoutDashboard, TerminalSquare, Workflow } from 'lucide-react'
import { NavLink } from 'react-router-dom'

const navigation = [
  { to: '/', label: 'Overview', icon: LayoutDashboard, end: true },
  { to: '/terminal', label: 'Terminal', icon: Workflow },
  { to: '/jobs', label: 'Jobs', icon: TerminalSquare },
]

export default function Sidebar() {
  return (
    <aside className="fixed inset-y-0 left-0 z-20 hidden w-[248px] flex-col border-r border-black/[0.07] bg-white/70 px-5 py-6 backdrop-blur-xl dark:border-white/[0.08] dark:bg-[#1d1916] lg:flex">
      <div className="flex items-center gap-3 px-2">
        <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-ember text-white shadow-[0_8px_20px_rgba(249,115,22,0.25)]">
          <Command size={18} strokeWidth={2.5} />
        </div>
        <div>
          <div className="font-display text-[22px] leading-none">IdleGrid</div>
          <div className="mt-1 font-mono text-[9px] uppercase tracking-[0.18em] text-black/40 dark:text-white/40">Compute, quietly shared</div>
        </div>
      </div>

      <div className="mt-14 px-2 font-mono text-[10px] uppercase tracking-[0.16em] text-black/35 dark:text-white/35">Workspace</div>
      <nav className="mt-3 space-y-1">
        {navigation.map(({ to, label, icon: Icon, end }) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            className={({ isActive }) => `group flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm transition ${isActive ? 'bg-ember-soft font-semibold text-ember dark:bg-ember/15 dark:text-orange-300' : 'text-black/55 hover:bg-black/[0.04] hover:text-ink dark:text-white/55 dark:hover:bg-white/[0.05] dark:hover:text-white'}`}
          >
            <Icon size={17} strokeWidth={1.8} />
            {label}
          </NavLink>
        ))}
      </nav>

      <div className="mt-auto space-y-4">
        <div className="rounded-2xl border border-ember/15 bg-ember-soft/60 p-4 dark:border-ember/20 dark:bg-ember/10">
          <div className="flex items-center gap-2 text-xs font-semibold text-ember dark:text-orange-300"><Activity size={14} /> Network pulse</div>
          <p className="mt-2 text-xs leading-5 text-black/55 dark:text-white/55">A quiet pool of shared machines, ready when work arrives.</p>
        </div>
        <div className="flex items-center gap-3 border-t border-black/[0.07] px-2 pt-4 dark:border-white/[0.08]">
          <div className="flex h-8 w-8 items-center justify-center rounded-full bg-[#24201d] text-xs font-semibold text-white">IG</div>
          <div><div className="text-xs font-semibold">Local workspace</div><div className="font-mono text-[10px] text-black/40 dark:text-white/40">master / localhost</div></div>
        </div>
      </div>
    </aside>
  )
}
