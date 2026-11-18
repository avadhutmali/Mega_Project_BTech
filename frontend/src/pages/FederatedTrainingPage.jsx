import { AlertTriangle, CheckCircle2, Clock3, Download, Network, Play, Upload } from 'lucide-react'
import { useEffect, useState } from 'react'
import { idleGridApi } from '../api/idleGridApi'

const initial = { workers: 3, cpuReq: 1, ramReqMb: 1024, localEpochs: 2, image: 'ghcr.io/avadhutmali/idlegrid-pytorch-cpu@sha256:74e60bc071f7349b0f7d9656f2105d7238152133e52dc71377b08eaa51883221' }
const terminal = (status) => ['COMPLETED', 'FAILED'].includes(status)

export default function FederatedTrainingPage() {
  const [form, setForm] = useState(initial)
  const [bundle, setBundle] = useState(null)
  const [run, setRun] = useState(null)
  const [runs, setRuns] = useState([])
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    idleGridApi.getFederatedRuns().then((items) => {
      if (!active) return
      const available = items || []
      setRuns(available)
      const lastId = window.localStorage.getItem('idlegrid-last-federated-run')
      setRun(available.find((item) => item.runId === lastId) || available[0] || null)
    }).catch((e) => { if (active) setError(e.message || 'Could not load federated run history') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [])

  useEffect(() => { if (run?.runId) window.localStorage.setItem('idlegrid-last-federated-run', run.runId) }, [run?.runId])

  useEffect(() => {
    if (!run?.runId || terminal(run.status)) return undefined
    let active = true
    const refresh = async () => {
      try {
        const latest = await idleGridApi.getFederatedRun(run.runId)
        if (active) {
          setRun(latest)
          setRuns((items) => [latest, ...items.filter((item) => item.runId !== latest.runId)])
        }
      } catch (e) { if (active) setError(e.message || 'Could not refresh this training run') }
    }
    refresh()
    const timer = window.setInterval(refresh, 2500)
    return () => { active = false; window.clearInterval(timer) }
  }, [run?.runId, run?.status])

  const update = (event) => setForm((current) => ({ ...current, [event.target.name]: event.target.value }))
  const loadExample = async () => {
    setError('')
    try {
      const response = await fetch('/examples/idlegrid-fedavg-demo.zip')
      if (!response.ok) throw new Error('Could not load the built-in demo bundle')
      setBundle(new File([await response.blob()], 'idlegrid-fedavg-demo.zip', { type: 'application/zip' }))
    } catch (e) { setError(e.message || 'Could not load demo bundle') }
  }
  const submit = async (event) => {
    event.preventDefault(); setBusy(true); setError('')
    try {
      const created = await idleGridApi.createFederatedRun({ ...form, bundle, workers: Number(form.workers), cpuReq: Number(form.cpuReq), ramReqMb: Number(form.ramReqMb), localEpochs: Number(form.localEpochs) })
      setRun(created); setRuns((items) => [created, ...items.filter((item) => item.runId !== created.runId)])
    } catch (e) { setError(e.message || 'Could not start federated training') }
    finally { setBusy(false) }
  }

  return <div className="animate-fade-in">
    <div className="mb-8 flex items-end gap-3"><div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-ember-soft text-ember dark:bg-ember/15"><Network size={22} /></div><div><p className="mb-1 font-mono text-[10px] uppercase tracking-[0.16em] text-ember">Coordinator-aggregated FedAvg</p><h1 className="font-display text-4xl leading-none sm:text-5xl">Federated image training.</h1></div></div>

    <div className="grid gap-7 xl:grid-cols-[minmax(0,1fr)_380px]">
      <form onSubmit={submit} className="rounded-2xl border border-black/[0.07] bg-white p-5 dark:border-white/[0.08] dark:bg-[#211c19] sm:p-7">
        <h2 className="font-display text-2xl">Launch a federated run</h2>
        <p className="mt-1 text-sm text-black/50 dark:text-white/50">Use 2–20 available PCs. Each trains a data slice, then the Master aggregates updates. The demo also measures the same full-data workload on one PC for comparison.</p>
        <div className="mt-5 flex flex-wrap items-center gap-3"><button type="button" onClick={loadExample} className="rounded-lg bg-ember-soft px-3 py-2 text-xs font-semibold text-ember dark:bg-ember/15">Load built-in demo bundle</button><a href="/examples/idlegrid-fedavg-demo.zip" download className="inline-flex items-center gap-1 text-xs text-black/55 underline dark:text-white/55"><Download size={13} />Download ZIP</a><span className="min-w-0 truncate text-xs text-black/45 dark:text-white/45">{bundle?.name || 'Or choose your compatible FedAvg bundle'}</span></div>
        <label className="mt-3 flex cursor-pointer items-center gap-3 rounded-xl border border-dashed border-black/15 px-4 py-4 text-sm dark:border-white/15"><Upload size={17} className="text-ember" /><span className="flex-1">Choose ZIP</span><input type="file" accept=".zip,application/zip" className="hidden" onChange={(e) => setBundle(e.target.files?.[0] || null)} /></label>
        <div className="mt-5 grid gap-3 sm:grid-cols-2"><Field label="Workers (2–20)" name="workers" type="number" min="2" max="20" value={form.workers} onChange={update} /><Field label="Local epochs per PC" name="localEpochs" type="number" min="1" max="10" value={form.localEpochs} onChange={update} /><Field label="CPU cores per PC" name="cpuReq" type="number" min="0.1" step="0.1" value={form.cpuReq} onChange={update} /><Field label="RAM per PC (MB)" name="ramReqMb" type="number" min="512" step="256" value={form.ramReqMb} onChange={update} /></div>
        {error && <div className="mt-5 flex gap-2 rounded-xl border border-red-500/20 bg-red-500/5 p-3 text-sm text-red-600"><AlertTriangle size={17} className="shrink-0" />{error}</div>}
        <div className="mt-6 flex justify-end"><button disabled={busy || !bundle} className="inline-flex items-center gap-2 rounded-xl bg-ember px-5 py-3 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50"><Play size={15} fill="currentColor" />{busy ? 'Reserving PCs…' : 'Start federated run'}</button></div>
      </form>
      <aside className="space-y-5">
        <section className="rounded-2xl border border-ember/15 bg-ember-soft/50 p-5 dark:bg-ember/10"><h2 className="font-display text-xl">How it works</h2><ol className="mt-3 list-decimal space-y-2 pl-5 text-xs leading-5 text-black/60 dark:text-white/60"><li>The Master assigns a different data slice to each available PC (up to 20).</li><li>Each PC trains locally and uploads its checkpoint to the Master.</li><li>Rank 0 computes a sample-weighted average.</li><li>Rank 0 then trains the same model on the full dataset as a single-PC baseline.</li><li>The report includes worker metrics, distributed wall time, baseline time and model artifacts.</li></ol><p className="mt-3 text-xs leading-5 text-black/55 dark:text-white/55">Workers contact the Master over HTTP; they do not need direct peer connections. The baseline runs on worker 0 and adds time to the overall run duration.</p></section>
        <section className="rounded-2xl border border-black/[0.07] bg-white p-5 dark:border-white/[0.08] dark:bg-[#211c19]"><h2 className="font-display text-xl">Bundle contract</h2><pre className="mt-3 overflow-x-auto rounded-xl bg-black/[0.05] p-3 font-mono text-xs leading-5 dark:bg-black/20">{'fedavg_train.py\nfedavg_merge.py\ndata/\n└── class-name/*.png'}</pre><p className="mt-3 text-xs leading-5 text-black/60 dark:text-white/60">The included demo bundle is ready to run. Custom bundles must split samples by <code>--rank</code>/<code>--world-size</code>, save a checkpoint, and use the same model initialization on every worker.</p></section>
      </aside>
    </div>

    {run && <section className="mt-7 rounded-2xl border border-black/[0.07] bg-white p-5 dark:border-white/[0.08] dark:bg-[#211c19]"><div className="flex flex-wrap items-center justify-between gap-3"><div><p className="font-mono text-[10px] uppercase tracking-[0.15em] text-black/40 dark:text-white/40">Run {run.runId}</p><h2 className="mt-1 font-display text-2xl">{run.status}</h2><p className="mt-1 text-xs text-black/50 dark:text-white/50">{run.strategy} · elapsed {formatDuration(run.durationMs)}</p></div><div className="flex gap-2">{run.reportUrl && <a href={idleGridApi.federatedReportUrl(run.runId)} className="inline-flex items-center gap-2 rounded-xl border border-black/10 px-3 py-2 text-xs font-semibold dark:border-white/10"><Download size={14} />Run report</a>}{run.artifactUrl && <a href={idleGridApi.federatedArtifactUrl(run.runId)} className="inline-flex items-center gap-2 rounded-xl bg-ember px-3 py-2 text-xs font-semibold text-white"><Download size={14} />Model</a>}</div></div>
      {run.error && <p className="mt-3 whitespace-pre-wrap text-sm text-red-600">{run.error}</p>}
      {run.comparison && (run.comparison.baseline || run.comparison.fedavg) && <div className="mt-5 rounded-xl border border-ember/20 bg-ember-soft/30 p-4 dark:bg-ember/5"><h3 className="font-display text-lg">Single PC vs distributed comparison</h3><p className="mt-1 text-[11px] text-black/50 dark:text-white/50">Both use the same dataset and epoch count. The distributed phase includes worker waiting and update transfers; total run elapsed also includes the baseline pass.</p><div className="mt-3 grid gap-3 sm:grid-cols-2">{run.comparison.fedavg && <MetricCard title={`FedAvg · ${run.workerCount} PCs`} lines={[`${run.comparison.fedavg.totalSamples} samples`, `Weighted accuracy ${percent(run.comparison.fedavg.weightedAccuracy)}`, `Weighted loss ${number(run.comparison.fedavg.weightedLoss)}`, `Distributed phase ${seconds(run.comparison.fedavg.distributedWallSeconds)}`, `Longest worker compute ${seconds(run.comparison.fedavg.parallelComputeSeconds)}`]} />}{run.comparison.baseline && <MetricCard title="Single PC baseline" lines={[`${run.comparison.baseline.samples} samples`, `Accuracy ${percent(run.comparison.baseline.accuracy)}`, `Loss ${number(run.comparison.baseline.loss)}`, `Training time ${seconds(run.comparison.baseline.seconds)}`]} />}</div>{run.comparison.fedavg && run.comparison.baseline && <p className="mt-3 font-mono text-xs text-black/65 dark:text-white/65">Baseline / distributed phase time ratio: {ratio(run.comparison.baseline.seconds, run.comparison.fedavg.distributedWallSeconds)}</p>}<p className="mt-2 text-[10px] text-black/45 dark:text-white/45">The ratio compares these measured phases only. Overall elapsed time above also includes scheduling and the single-PC baseline run.</p></div>}
      <div className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">{(run.workers || []).map((worker) => <div key={worker.rank} className="rounded-xl border border-black/[0.07] p-4 dark:border-white/[0.08]"><div className="flex items-center justify-between text-sm font-semibold"><span>Worker {worker.rank}</span>{worker.status === 'DONE' ? <CheckCircle2 size={16} className="text-emerald-600" /> : <span className="font-mono text-[10px] text-black/45 dark:text-white/45">{worker.status}</span>}</div><p className="mt-2 font-mono text-xs text-black/50 dark:text-white/50">{worker.nodeId || 'Waiting for worker'}{worker.nodeIp ? ` · ${worker.nodeIp}` : ''}</p><p className="mt-1 text-[10px] text-black/45 dark:text-white/45">{worker.updateUploaded ? 'Update received' : 'Training'} · {formatDuration(worker.durationMs)}</p>{worker.output && <pre className="mt-3 max-h-44 overflow-auto whitespace-pre-wrap rounded-lg bg-black/[0.04] p-2 font-mono text-[10px] leading-4 dark:bg-black/20">{worker.output}</pre>}</div>)}</div>
    </section>}

    <section className="mt-7 rounded-2xl border border-black/[0.07] bg-white p-5 dark:border-white/[0.08] dark:bg-[#211c19]"><div className="flex items-center gap-2"><Clock3 size={16} className="text-ember" /><h2 className="font-display text-xl">Previous federated runs</h2></div><p className="mt-1 text-xs text-black/45 dark:text-white/45">The Master reloads the selected run and history when this page is refreshed.</p>{loading ? <p className="mt-4 text-sm text-black/45 dark:text-white/45">Loading run history…</p> : runs.length ? <div className="mt-4 space-y-2">{runs.map((item) => <button key={item.runId} type="button" onClick={() => { setRun(item); setError('') }} className={`flex w-full items-center justify-between gap-3 rounded-xl border px-3 py-3 text-left ${run?.runId === item.runId ? 'border-ember/40 bg-ember-soft/50 dark:bg-ember/10' : 'border-black/[0.07] hover:bg-black/[0.02] dark:border-white/[0.08]'}`}><span className="min-w-0"><span className="block truncate font-mono text-xs">{item.runId}</span><span className="mt-1 block text-[10px] text-black/40 dark:text-white/40">{item.createdAt ? new Date(item.createdAt).toLocaleString() : 'Time unavailable'} · {item.workerCount} workers · {formatDuration(item.durationMs)}</span></span><span className={`shrink-0 font-mono text-[10px] font-semibold ${item.status === 'COMPLETED' ? 'text-emerald-600' : item.status === 'FAILED' ? 'text-red-600' : 'text-amber-600'}`}>{item.status}</span></button>)}</div> : <p className="mt-4 text-sm text-black/45 dark:text-white/45">No federated runs yet.</p>}</section>
  </div>
}

function Field({ label, ...props }) { return <label className="block text-xs font-semibold">{label}<input {...props} required className="mt-2 w-full rounded-xl border border-black/10 bg-transparent px-3 py-2.5 font-mono text-sm font-normal outline-none focus:border-ember dark:border-white/10" /></label> }
function MetricCard({ title, lines }) { return <div className="rounded-lg border border-black/[0.07] bg-white/70 p-3 dark:border-white/[0.08] dark:bg-black/10"><h4 className="text-xs font-semibold">{title}</h4>{lines.map((line) => <p key={line} className="mt-1 font-mono text-[10px] text-black/55 dark:text-white/55">{line}</p>)}</div> }
function number(value) { return Number.isFinite(Number(value)) ? Number(value).toFixed(4) : '—' }
function percent(value) { return Number.isFinite(Number(value)) ? `${(Number(value) * 100).toFixed(1)}%` : '—' }
function seconds(value) { return Number.isFinite(Number(value)) ? `${Number(value).toFixed(2)}s` : '—' }
function ratio(baseline, distributed) { const a = Number(baseline); const b = Number(distributed); return a > 0 && b > 0 ? `${(a / b).toFixed(2)}×` : '—' }
function formatDuration(ms) { if (ms == null || !Number.isFinite(Number(ms))) return '—'; const total = Math.max(0, Math.floor(Number(ms) / 1000)); const min = Math.floor(total / 60); return min ? `${min}m ${total % 60}s` : `${total}s` }
