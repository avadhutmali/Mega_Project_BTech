import { AlertTriangle, Brain, CheckCircle2, Download, Play, Upload } from 'lucide-react'
import { useEffect, useState } from 'react'
import { idleGridApi } from '../api/idleGridApi'

const initial = {
  command: 'train.py --epochs 2 --batch-size 32',
  workers: 3,
  cpuReq: 1,
  ramReqMb: 2048,
  image: 'ghcr.io/avadhutmali/idlegrid-pytorch-cpu:latest',
  outputFile: 'model.pt',
}

export default function DistributedTrainingPage() {
  const [form, setForm] = useState(initial)
  const [bundle, setBundle] = useState(null)
  const [run, setRun] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!run?.runId || ['COMPLETED', 'FAILED'].includes(run.status)) return undefined
    let active = true
    const refresh = async () => {
      try {
        const next = await idleGridApi.getTrainingRun(run.runId)
        if (active) setRun(next)
      } catch (e) {
        if (active) setError(e.message || 'Could not refresh training status')
      }
    }
    const timer = window.setInterval(refresh, 2500)
    refresh()
    return () => { active = false; window.clearInterval(timer) }
  }, [run?.runId, run?.status])

  const update = (event) => setForm((current) => ({ ...current, [event.target.name]: event.target.value }))

  const submit = async (event) => {
    event.preventDefault()
    setBusy(true); setError(''); setRun(null)
    try {
      const created = await idleGridApi.createTrainingRun({
        ...form,
        bundle,
        workers: Number(form.workers),
        cpuReq: Number(form.cpuReq),
        ramReqMb: Number(form.ramReqMb),
      })
      setRun(created)
    } catch (e) {
      setError(e.message || 'Could not start distributed training')
    } finally {
      setBusy(false)
    }
  }

  return <div className="animate-fade-in">
    <div className="mb-8 flex items-end gap-3">
      <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-ember-soft text-ember dark:bg-ember/15"><Brain size={22} /></div>
      <div><p className="mb-1 font-mono text-[10px] uppercase tracking-[0.16em] text-ember">Multi-node DDP</p><h1 className="font-display text-4xl leading-none sm:text-5xl">Distributed image training.</h1></div>
    </div>

    <div className="grid gap-7 xl:grid-cols-[minmax(0,1fr)_380px]">
      <form onSubmit={submit} className="rounded-2xl border border-black/[0.07] bg-white p-5 dark:border-white/[0.08] dark:bg-[#211c19] sm:p-7">
        <h2 className="font-display text-2xl">Launch one training run</h2>
        <p className="mt-1 text-sm text-black/50 dark:text-white/50">The Master reserves all workers together and starts one rank on each PC.</p>

        <label className="mt-6 block text-xs font-semibold">Training bundle ZIP</label>
        <label className="mt-2 flex cursor-pointer items-center gap-3 rounded-xl border border-dashed border-black/15 px-4 py-4 text-sm dark:border-white/15">
          <Upload size={17} className="text-ember" />
          <span className="min-w-0 flex-1 truncate">{bundle?.name || 'Choose ZIP with train.py and data/ class folders'}</span>
          <input type="file" accept=".zip,application/zip" className="hidden" onChange={(e) => setBundle(e.target.files?.[0] || null)} />
        </label>

        <label className="mt-5 block text-xs font-semibold" htmlFor="training-command">Python entrypoint and arguments</label>
        <input id="training-command" name="command" value={form.command} onChange={update} required className="mt-2 w-full rounded-xl border border-black/10 bg-transparent px-3 py-3 font-mono text-sm outline-none focus:border-ember dark:border-white/10" />
        <p className="mt-1 text-xs text-black/45 dark:text-white/45">Example: <code>train.py --epochs 2 --batch-size 32</code>. The script must use DDP and save the rank 0 checkpoint as <code>model.pt</code>.</p>

        <div className="mt-5 grid gap-3 sm:grid-cols-2">
          <Field label="Workers" name="workers" type="number" min="2" max="3" value={form.workers} onChange={update} />
          <Field label="CPU cores per PC" name="cpuReq" type="number" min="0.1" step="0.1" value={form.cpuReq} onChange={update} />
          <Field label="RAM per PC (MB)" name="ramReqMb" type="number" min="512" step="256" value={form.ramReqMb} onChange={update} />
          <Field label="Docker image" name="image" value={form.image} onChange={update} />
        </div>

        {error && <div className="mt-5 flex gap-2 rounded-xl border border-red-500/20 bg-red-500/5 p-3 text-sm text-red-600"><AlertTriangle size={17} className="shrink-0" />{error}</div>}
        <div className="mt-6 flex justify-end">
          <button disabled={busy || !bundle} className="inline-flex items-center gap-2 rounded-xl bg-ember px-5 py-3 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50"><Play size={15} fill="currentColor" />{busy ? 'Reserving PCs…' : 'Start distributed run'}</button>
        </div>
      </form>

      <aside className="space-y-5">
        <section className="rounded-2xl border border-ember/15 bg-ember-soft/50 p-5 dark:bg-ember/10">
          <h2 className="font-display text-xl">Bundle format</h2>
          <pre className="mt-3 overflow-x-auto rounded-xl bg-black/[0.05] p-3 font-mono text-xs leading-5 dark:bg-black/20">{`training.zip
├── train.py
├── model.py
└── data/
    ├── cats/*.jpg
    └── dogs/*.jpg`}</pre>
          <p className="mt-3 text-xs leading-5 text-black/60 dark:text-white/60">Use PyTorch DistributedDataParallel and DistributedSampler. Each PC gets the bundle; the sampler gives each rank different samples and DDP synchronizes gradients.</p>
        </section>
        <section className="rounded-2xl border border-black/[0.07] bg-white p-5 dark:border-white/[0.08] dark:bg-[#211c19]">
          <h2 className="font-display text-xl">PC prerequisites</h2>
          <ul className="mt-3 list-disc space-y-2 pl-5 text-xs leading-5 text-black/60 dark:text-white/60">
            <li>All selected PCs must be online and idle enough for the requested CPU/RAM.</li>
            <li>Pull <code>ghcr.io/avadhutmali/idlegrid-pytorch-cpu:latest</code> on each PC (or build it locally if GHCR is unavailable).</li>
            <li>Enable Docker Desktop host networking and allow TCP between the PCs.</li>
            <li>Start with CPU/Gloo; this project does not pass GPUs into containers yet.</li>
          </ul>
        </section>
      </aside>
    </div>

    {run && <section className="mt-7 rounded-2xl border border-black/[0.07] bg-white p-5 dark:border-white/[0.08] dark:bg-[#211c19]">
      <div className="flex flex-wrap items-center justify-between gap-3"><div><p className="font-mono text-[10px] uppercase tracking-[0.15em] text-black/40 dark:text-white/40">Run {run.runId}</p><h2 className="mt-1 font-display text-2xl">{run.status}</h2></div>{run.artifactUrl && <a href={idleGridApi.trainingArtifactUrl(run.runId)} className="inline-flex items-center gap-2 rounded-xl bg-ember px-4 py-2.5 text-xs font-semibold text-white"><Download size={15} />Download {run.outputFile || 'model.pt'}</a>}</div>
      {run.error && <p className="mt-3 text-sm text-red-600">{run.error}</p>}
      <div className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">{(run.workers || []).map((worker) => <div key={worker.rank} className="rounded-xl border border-black/[0.07] p-4 dark:border-white/[0.08]"><div className="flex items-center justify-between text-sm font-semibold"><span>Rank {worker.rank}</span>{worker.status === 'DONE' ? <CheckCircle2 size={16} className="text-emerald-600" /> : <span className="font-mono text-[10px] text-black/45 dark:text-white/45">{worker.status}</span>}</div><p className="mt-2 font-mono text-xs text-black/50 dark:text-white/50">{worker.nodeId || 'Waiting for worker'} {worker.nodeIp ? `· ${worker.nodeIp}` : ''}</p>{worker.output && <pre className="mt-3 max-h-40 overflow-auto whitespace-pre-wrap rounded-lg bg-black/[0.04] p-2 font-mono text-[10px] leading-4 dark:bg-black/20">{worker.output}</pre>}</div>)}</div>
    </section>}
  </div>
}

function Field({ label, ...props }) {
  return <label className="block text-xs font-semibold">{label}<input {...props} required className="mt-2 w-full rounded-xl border border-black/10 bg-transparent px-3 py-2.5 font-mono text-sm font-normal outline-none focus:border-ember dark:border-white/10" /></label>
}
