import { Copy, Download, HardDrive, Upload } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { idleGridApi } from '../api/idleGridApi'

const gb = (mb = 0) => `${(Math.max(0, mb) / 1024).toFixed(1)} GB`
const size = (bytes = 0) => bytes < 1024 * 1024 ? `${(bytes / 1024).toFixed(1)} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`

export default function StoragePage() {
  const [summary, setSummary] = useState(null)
  const [files, setFiles] = useState([])
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [copied, setCopied] = useState('')
  const [lookupKey, setLookupKey] = useState('')
  const refresh = useCallback(async () => {
    try {
      const [s, f] = await Promise.all([idleGridApi.getStorageSummary(), idleGridApi.getStoredFiles()])
      setSummary(s); setFiles(f || []); setError('')
    } catch (e) { setError(e.message || 'Could not load storage') }
  }, [])

  useEffect(() => {
    refresh()
    const timer = window.setInterval(refresh, 5000)
    return () => window.clearInterval(timer)
  }, [refresh])

  const upload = async (event) => {
    const file = event.target.files?.[0]
    if (!file) return
    setBusy(true); setError('')
    try { await idleGridApi.uploadFile(file); await refresh() }
    catch (e) { setError(e.message || 'Upload failed') }
    finally { setBusy(false); event.target.value = '' }
  }

  const copyKey = async (key) => {
    await navigator.clipboard.writeText(key)
    setCopied(key); window.setTimeout(() => setCopied(''), 1800)
  }

  const total = summary?.onlineNodeDiskTotalMb || 0
  const free = summary?.onlineNodeDiskFreeMb || 0

  return <div className="animate-fade-in">
    <div className="flex flex-wrap items-end justify-between gap-4">
      <div><div className="mb-2 flex items-center gap-2 text-ember"><HardDrive size={17} /><span className="font-mono text-[10px] uppercase tracking-[0.16em]">Shared storage</span></div><h1 className="font-display text-4xl">Files across the grid.</h1><p className="mt-2 text-sm text-black/45 dark:text-white/45">Files are stored by the Master and copied to up to two online worker PCs.</p></div>
      <label className={`inline-flex cursor-pointer items-center gap-2 rounded-xl bg-ember px-4 py-3 text-xs font-semibold text-white ${busy ? 'opacity-60' : ''}`}><Upload size={15} />{busy ? 'Uploading…' : 'Add a file'}<input type="file" className="hidden" disabled={busy} onChange={upload} /></label>
    </div>
    {error && <div className="mt-5 rounded-xl border border-red-500/20 bg-red-500/5 px-4 py-3 text-xs text-red-600">{error}</div>}
    <section className="mt-8 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <Metric label="Disk free on online PCs" value={gb(free)} detail={`of ${gb(total)} total`} />
      <Metric label="Master storage free" value={gb(summary?.masterDiskFreeMb)} detail={`of ${gb(summary?.masterDiskTotalMb)} total`} />
      <Metric label="Stored files" value={summary?.fileCount ?? files.length} detail="indexed by the Master" />
      <Metric label="Replica target" value="2 copies" detail="plus the Master copy, when worker capacity allows" />
    </section>
    <form className="mt-6 flex flex-wrap gap-2 rounded-2xl border border-black/[0.07] bg-white p-4 dark:border-white/[0.08] dark:bg-[#211c19]" onSubmit={(event) => { event.preventDefault(); if (lookupKey.trim()) window.location.assign(idleGridApi.fileUrl(lookupKey.trim())) }}>
      <input value={lookupKey} onChange={(event) => setLookupKey(event.target.value)} placeholder="Paste a file access key" className="min-w-[240px] flex-1 rounded-lg border border-black/10 bg-transparent px-3 py-2 font-mono text-xs outline-none focus:border-ember dark:border-white/10" />
      <button className="rounded-lg border border-ember/30 px-3 py-2 text-xs font-semibold text-ember">Retrieve file</button>
    </form>
    <section className="mt-8 overflow-hidden rounded-2xl border border-black/[0.07] bg-white dark:border-white/[0.08] dark:bg-[#211c19]">
      <div className="border-b border-black/[0.07] px-5 py-4 dark:border-white/[0.08]"><h2 className="font-display text-2xl">Your files</h2><p className="mt-1 text-xs text-black/45 dark:text-white/45">Keep the access key private. Anyone with a key can download its file.</p></div>
      {files.length ? <div className="divide-y divide-black/[0.06] dark:divide-white/[0.06]">{files.map((f) => <div key={f.key} className="flex flex-wrap items-center justify-between gap-3 px-5 py-4">
        <div className="min-w-0"><div className="truncate text-sm font-medium">{f.fileName}</div><div className="mt-1 font-mono text-[10px] text-black/40 dark:text-white/40">{size(f.sizeBytes)} · {f.status} · {f.replicaNodeIds?.length || 0} worker copies</div><div className="mt-1 truncate font-mono text-[10px] text-black/35 dark:text-white/35">Key: {f.key}</div></div>
        <div className="flex items-center gap-2"><button onClick={() => copyKey(f.key)} className="rounded-lg border border-black/10 p-2 text-black/50 hover:text-ember dark:border-white/10" title="Copy access key"><Copy size={15} /></button><a href={idleGridApi.fileUrl(f.key)} className="inline-flex items-center gap-1.5 rounded-lg bg-ember px-3 py-2 text-xs font-semibold text-white"><Download size={14} />Download</a></div>
        {copied === f.key && <span className="text-xs text-emerald-600">Key copied</span>}
      </div>)}</div> : <div className="px-5 py-12 text-center text-sm text-black/40">No files yet. Add a file to get its access key.</div>}
    </section>
  </div>
}

function Metric({ label, value, detail }) { return <div className="rounded-2xl border border-black/[0.07] bg-white p-5 dark:border-white/[0.08] dark:bg-[#211c19]"><div className="font-mono text-[10px] uppercase tracking-[0.14em] text-black/40 dark:text-white/40">{label}</div><div className="mt-4 font-display text-3xl">{value}</div><div className="mt-1 text-xs text-black/45 dark:text-white/45">{detail}</div></div> }
