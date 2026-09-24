export default function StatCard({ label, value, detail, icon: Icon, accent = false }) {
  return (
    <div className={`rounded-2xl border p-5 shadow-soft transition hover:-translate-y-0.5 ${accent ? 'border-ember/20 bg-ember text-white' : 'border-black/[0.07] bg-white dark:border-white/[0.08] dark:bg-[#211c19]'}`}>
      <div className="flex items-start justify-between">
        <span className={`font-mono text-[10px] uppercase tracking-[0.14em] ${accent ? 'text-white/65' : 'text-black/40 dark:text-white/40'}`}>{label}</span>
        <Icon size={17} className={accent ? 'text-white/75' : 'text-ember'} strokeWidth={1.7} />
      </div>
      <div className="mt-6 flex items-end justify-between gap-3">
        <div className="font-display text-4xl leading-none">{value}</div>
        <span className={`text-right text-xs ${accent ? 'text-white/70' : 'text-black/45 dark:text-white/45'}`}>{detail}</span>
      </div>
    </div>
  )
}
