import { AlertTriangle, Check, Copy, Download, FileArchive, HardDrive, LoaderCircle, Play, RefreshCw, Square, TerminalSquare, Upload } from 'lucide-react'
import { useEffect, useState } from 'react'
import { clientApi } from './api'

const DDP_IMAGE = 'ghcr.io/avadhutmali/idlegrid-pytorch-cpu:latest'

export function ClientTerminal({ token, onSubmitted }) {
  const [command, setCommand] = useState('echo hello from idlegrid')
  const [cpuReq, setCpuReq] = useState(1)
  const [ramReqMb, setRamReqMb] = useState(512)
  const [job, setJob] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (!job || !['QUEUED', 'ASSIGNED', 'RUNNING'].includes(job.status)) return undefined
    let active = true
    const poll = async () => {
      try {
        const next = await clientApi.job(token, job.id)
        if (active) setJob(next)
      } catch (e) { if (active) setError(e.message || 'Could not refresh job status') }
    }
    const timer = window.setInterval(poll, 1800)
    poll()
    return () => { active = false; window.clearInterval(timer) }
  }, [job?.id, job?.status, token])

  const submit = async (event) => {
    event.preventDefault(); setError(''); setBusy(true)
    try {
      const created = await clientApi.submit(token, { command: command.trim(), cpuReq: Number(cpuReq), ramReqMb: Number(ramReqMb) })
      setJob(created); onSubmitted?.();
    } catch (e) { setError(e.message || 'Could not queue command') }
    finally { setBusy(false) }
  }

  const release = async () => {
    if (!job) return
    try { const updated = await clientApi.cancel(token, job.id); setJob(updated); onSubmitted?.() }
    catch (e) { setError(e.message || 'Could not release job') }
  }

  return <section className="panel tool-panel" id="terminal">
    <ToolHeading icon={<TerminalSquare size={17} />} eyebrow="COMMAND WORKSPACE" title="Run a terminal job" text="Commands execute in a resource-capped Docker container. Output is saved with your job history." />
    <form className="tool-form" onSubmit={submit}>
      <label>Command<textarea rows={3} required value={command} onChange={(e) => setCommand(e.target.value)} placeholder="python3 script.py --help" /></label>
      <div className="tool-form-row"><label>CPU cores<input type="number" min="0.1" step="0.1" required value={cpuReq} onChange={(e) => setCpuReq(e.target.value)} /></label><label>RAM (MB)<input type="number" min="1" step="128" required value={ramReqMb} onChange={(e) => setRamReqMb(e.target.value)} /></label><button className="primary-button" disabled={busy || Boolean(job && ['QUEUED', 'ASSIGNED', 'RUNNING'].includes(job.status))}><Play size={14} fill="currentColor" />{busy ? 'Queueing…' : 'Run command'}</button></div>
    </form>
    {error && <ToolError>{error}</ToolError>}
    {job && <div className="tool-output"><div className="output-head"><span>Job {job.id}</span><b>{job.status}</b>{['QUEUED', 'ASSIGNED', 'RUNNING'].includes(job.status) && <button onClick={release}><Square size={12} /> Release</button>}</div><div className="output-command"><span>$</span> {job.command}</div>{['QUEUED', 'ASSIGNED', 'RUNNING'].includes(job.status) ? <p className="tool-wait"><LoaderCircle className="spin" size={14} /> {job.status.toLowerCase()} · waiting for result</p> : <pre>{job.result || '(no output)'}</pre>}</div>}
  </section>
}

export function ClientStorage({ token }) {
  const [summary, setSummary] = useState(null)
  const [files, setFiles] = useState([])
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [copied, setCopied] = useState('')

  const refresh = async () => {
    try {
      const [nextSummary, nextFiles] = await Promise.all([clientApi.storageSummary(token), clientApi.storedFiles(token)])
      setSummary(nextSummary); setFiles(nextFiles || []); setError('')
    } catch (e) { setError(e.message || 'Could not load your files') }
  }
  useEffect(() => { refresh(); const timer = window.setInterval(refresh, 5000); return () => window.clearInterval(timer) }, [token])

  const upload = async (event) => {
    const file = event.target.files?.[0]
    if (!file) return
    setBusy(true); setError('')
    try { await clientApi.uploadFile(token, file); await refresh() }
    catch (e) { setError(e.message || 'Upload failed') }
    finally { setBusy(false); event.target.value = '' }
  }

  const download = async (file) => {
    try {
      const blob = await clientApi.downloadFile(token, file.key)
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url; anchor.download = file.fileName; anchor.click()
      window.setTimeout(() => URL.revokeObjectURL(url), 1000)
    } catch (e) { setError(e.message || 'Download failed') }
  }

  const copy = async (key) => { await navigator.clipboard.writeText(key); setCopied(key); window.setTimeout(() => setCopied(''), 1500) }
  const gb = (mb = 0) => `${(Math.max(0, mb) / 1024).toFixed(1)} GB`
  const fileSize = (bytes = 0) => bytes < 1048576 ? `${(bytes / 1024).toFixed(1)} KB` : `${(bytes / 1048576).toFixed(1)} MB`

  return <section className="panel tool-panel" id="storage">
    <div className="tool-title-row"><ToolHeading icon={<HardDrive size={17} />} eyebrow="PRIVATE FILE STORAGE" title="Your files" text="Only files uploaded by your account appear here. Their access keys are scoped to your account in this client." /><label className="upload-button"><Upload size={14} />{busy ? 'Uploading…' : 'Add file'}<input type="file" hidden disabled={busy} onChange={upload} /></label></div>
    {error && <ToolError>{error}</ToolError>}
    <div className="storage-summary"><div><small>Files owned by you</small><b>{summary?.fileCount ?? files.length}</b></div><div><small>Your primary storage</small><b>{fileSize(summary?.primaryStoredBytes || 0)}</b></div><div><small>Grid disk free</small><b>{gb(summary?.onlineNodeDiskFreeMb)}</b></div><div><small>Replica target</small><b>2 workers</b></div></div>
    {files.length ? <div className="owned-files">{files.map((file) => <article key={file.key} className="owned-file"><span className="file-mark"><FileArchive size={17} /></span><div className="owned-file-main"><b>{file.fileName}</b><small>{fileSize(file.sizeBytes)} · {file.status} · {file.replicaNodeIds?.length || 0} worker copies</small><code>{file.key}</code></div><button className="small-action" onClick={() => copy(file.key)} title="Copy access key">{copied === file.key ? <Check size={14} /> : <Copy size={14} />}</button><button className="small-action" onClick={() => download(file)} title="Download file"><Download size={14} /></button></article>)}</div> : <div className="empty-owned">No files in your account yet. Upload a file to store it on the grid.</div>}
  </section>
}

export function ClientTraining({ token }) {
  const [form, setForm] = useState({ command: 'train.py --epochs 2 --batch-size 32', workers: 3, cpuReq: 1, ramReqMb: 2048, image: DDP_IMAGE, outputFile: 'model.pt' })
  const [bundle, setBundle] = useState(null)
  const [runs, setRuns] = useState([])
  const [run, setRun] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const refresh = async () => {
    try {
      const history = await clientApi.trainingRuns(token)
      setRuns(history || [])
      if (run?.runId) {
        const current = history?.find((item) => item.runId === run.runId)
        if (current) setRun(current)
      } else if (history?.length) setRun(history[0])
      setError('')
    } catch (e) { setError(e.message || 'Could not load your training history') }
  }
  useEffect(() => { refresh() }, [token])
  useEffect(() => {
    if (!run?.runId || ['COMPLETED', 'FAILED'].includes(run.status)) return undefined
    let active = true
    const poll = async () => {
      try {
        const latest = await clientApi.trainingRun(token, run.runId)
        if (!active) return
        setRun(latest); setRuns((current) => [latest, ...current.filter((item) => item.runId !== latest.runId)])
      } catch (e) { if (active) setError(e.message || 'Could not refresh run status') }
    }
    const timer = window.setInterval(poll, 2500)
    poll()
    return () => { active = false; window.clearInterval(timer) }
  }, [token, run?.runId, run?.status])

  const submit = async (event) => {
    event.preventDefault(); setError(''); setBusy(true)
    try {
      const created = await clientApi.createTrainingRun(token, { ...form, bundle, workers: Number(form.workers), cpuReq: Number(form.cpuReq), ramReqMb: Number(form.ramReqMb) })
      setRun(created); setRuns((current) => [created, ...current.filter((item) => item.runId !== created.runId)])
    } catch (e) { setError(e.message || 'Could not start distributed training') }
    finally { setBusy(false) }
  }

  const download = async () => {
    try {
      const blob = await clientApi.downloadArtifact(token, run.runId)
      const url = URL.createObjectURL(blob); const anchor = document.createElement('a')
      anchor.href = url; anchor.download = run.outputFile || 'model.pt'; anchor.click(); window.setTimeout(() => URL.revokeObjectURL(url), 1000)
    } catch (e) { setError(e.message || 'Could not download model artifact') }
  }
  const update = (event) => setForm((current) => ({ ...current, [event.target.name]: event.target.value }))

  return <section className="panel tool-panel" id="training">
    <ToolHeading icon={<FileArchive size={17} />} eyebrow="MULTI-NODE PYTORCH DDP" title="Distributed training" text="Launch a DDP run across the selected lab PCs. Run history is private to your account." />
    <div className="training-grid">
      <form className="tool-form training-form" onSubmit={submit}>
        <label>Training bundle ZIP<input type="file" accept=".zip,application/zip" required onChange={(e) => setBundle(e.target.files?.[0] || null)} /></label>
        <label>Python entrypoint and arguments<input name="command" required value={form.command} onChange={update} /></label>
        <div className="tool-form-row"><label>Workers<input type="number" min="2" max="3" name="workers" value={form.workers} onChange={update} /></label><label>CPU per PC<input type="number" min="0.1" step="0.1" name="cpuReq" value={form.cpuReq} onChange={update} /></label><label>RAM per PC (MB)<input type="number" min="512" step="256" name="ramReqMb" value={form.ramReqMb} onChange={update} /></label></div>
        <label>Docker image<input name="image" required value={form.image} onChange={update} /></label><label>Checkpoint file<input name="outputFile" required value={form.outputFile} onChange={update} /></label>
        <button className="primary-button" disabled={busy || !bundle}><Play size={14} fill="currentColor" />{busy ? 'Reserving workers…' : 'Start DDP run'}</button>
      </form>
      <aside className="training-history"><div className="history-head"><h3>Your training history</h3><button className="small-action" onClick={refresh} title="Refresh history"><RefreshCw size={14} /></button></div>
        {runs.length ? runs.map((item) => <button key={item.runId} className={`history-item ${run?.runId === item.runId ? 'selected' : ''}`} onClick={() => setRun(item)}><span>{item.runId}</span><b>{item.status}</b><small>{item.createdAt ? new Date(item.createdAt).toLocaleString() : ''} · {item.workerCount} workers</small></button>) : <p className="empty-owned">No DDP runs recorded.</p>}
      </aside>
    </div>
    {error && <ToolError>{error}</ToolError>}
    {run && <div className="run-detail"><div className="run-detail-head"><div><small>RUN {run.runId}</small><h3>{run.status}</h3></div>{run.status === 'COMPLETED' && <button className="download-button" onClick={download}><Download size={14} /> Download {run.outputFile}</button>}</div>{run.error && <p className="run-error">{run.error}</p>}<div className="worker-grid">{(run.workers || []).map((worker) => <article key={worker.rank}><b>Rank {worker.rank} · {worker.status}</b><small>{worker.nodeId || 'Waiting for worker'}{worker.nodeIp ? ` · ${worker.nodeIp}` : ''}</small>{worker.output && <pre>{worker.output}</pre>}</article>)}</div></div>}
  </section>
}

function ToolHeading({ icon, eyebrow, title, text }) {
  return <div className="panel-heading"><div className="panel-icon">{icon}</div><div><p className="tool-eyebrow">{eyebrow}</p><h2>{title}</h2><p>{text}</p></div></div>
}

function ToolError({ children }) {
  return <div className="tool-error"><AlertTriangle size={15} />{children}</div>
}
