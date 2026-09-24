import { Bell, Menu, Moon, RefreshCw, Sun } from 'lucide-react'
import { useApp } from '../../context/AppContext'
import { useTheme } from '../../context/ThemeContext'

export default function TopBar() {
  const { refresh, loading, error } = useApp()
  const { theme, toggleTheme } = useTheme()
  return (
    <header className="flex h-[76px] items-center justify-between border-b border-black/[0.07] px-5 sm:px-8 lg:px-12 dark:border-white/[0.08]">
      <button type="button" className="rounded-lg p-2 text-black/55 hover:bg-black/[0.04] dark:text-white/55 dark:hover:bg-white/[0.05] lg:hidden" aria-label="Open navigation"><Menu size={20} /></button>
      <div className="hidden items-center gap-2 text-xs text-black/45 dark:text-white/45 sm:flex"><span className={`h-2 w-2 rounded-full ${error ? 'bg-red-500' : 'bg-emerald-500'}`} /> {error ? 'Master offline' : 'Master connected'}</div>
      <div className="ml-auto flex items-center gap-2">
        <button type="button" onClick={refresh} className="rounded-lg p-2 text-black/45 transition hover:bg-black/[0.04] hover:text-ink dark:text-white/45 dark:hover:bg-white/[0.05] dark:hover:text-white" aria-label="Refresh data"><RefreshCw size={16} className={loading ? 'animate-spin' : ''} /></button>
        <button type="button" onClick={toggleTheme} className="flex items-center gap-2 rounded-lg px-2.5 py-2 text-xs text-black/50 transition hover:bg-black/[0.04] hover:text-ink dark:text-white/50 dark:hover:bg-white/[0.05] dark:hover:text-white" aria-label={`Switch to ${theme === 'light' ? 'dark' : 'light'} theme`} title={`Switch to ${theme === 'light' ? 'dark' : 'light'} theme`}>
          {theme === 'light' ? <Moon size={16} /> : <Sun size={16} />}
          <span className="hidden sm:inline">{theme === 'light' ? 'Dark' : 'Light'}</span>
        </button>
        <button type="button" className="relative rounded-lg p-2 text-black/45 transition hover:bg-black/[0.04] dark:text-white/45 dark:hover:bg-white/[0.05]" aria-label="Notifications"><Bell size={17} /><span className="absolute right-1.5 top-1.5 h-1.5 w-1.5 rounded-full bg-ember" /></button>
      </div>
    </header>
  )
}
