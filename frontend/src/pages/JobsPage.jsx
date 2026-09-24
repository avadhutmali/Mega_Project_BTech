import { Link } from 'react-router-dom'
import { ArrowLeft, ListFilter } from 'lucide-react'
import { useApp } from '../context/AppContext'
import JobTable from '../components/dashboard/JobTable'
import SubmitJobForm from '../components/jobs/SubmitJobForm'

export default function JobsPage() {
  const { jobs } = useApp()
  return <div className="animate-fade-in"><div className="mb-9 flex flex-col justify-between gap-5 sm:flex-row sm:items-end"><div><Link to="/" className="mb-4 flex items-center gap-2 text-xs text-black/45 hover:text-ember dark:text-white/45"><ArrowLeft size={14} /> Overview</Link><h1 className="font-display text-4xl leading-none sm:text-5xl">Every job, in one place.</h1><p className="mt-3 text-sm text-black/50 dark:text-white/50">Track queued, running, completed, and failed work.</p></div><div className="flex items-center gap-2 font-mono text-[10px] uppercase tracking-[0.14em] text-black/35 dark:text-white/35"><ListFilter size={15} /> {jobs.length} total</div></div><div className="grid gap-8 xl:grid-cols-[1fr_360px]"><div className="min-w-0"><JobTable jobs={jobs} /></div><SubmitJobForm /></div></div>
}
