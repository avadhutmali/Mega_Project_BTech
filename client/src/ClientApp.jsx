import { Activity, ArrowUpRight, Check, CircleAlert, CircleDashed, Cpu, HardDrive, KeyRound, Layers3, LoaderCircle, LogOut, MemoryStick, Play, Radio, RefreshCw, Server, ShieldCheck, Square, Terminal, UserRound, XCircle } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { clientApi } from './api'
import { ClientStorage, ClientTerminal, ClientTraining } from './ClientTools'

const statusStyles = {
  QUEUED: 'status-queued', ASSIGNED: 'status-assigned', RUNNING: 'status-running',
  DONE: 'status-done', FAILED: 'status-failed', CANCELLED: 'status-cancelled',
}

export default function ClientApp() {
  const [token, setToken] = useState(() => sessionStorage.getItem(clientApi.tokenKey) || '')
  const [profile, setProfile] = useState(null)
  const [nodes, setNodes] = useState([])
  const [jobs, setJobs] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [refreshing, setRefreshing] = useState(false)
  const [form, setForm] = useState({ command: 'python3 -c "print(2 ** 10)"', cpuReq: 0.5, ramReqMb: 512 })
  const [submitting, setSubmitting] = useState(false)

  const signOut = useCallback(async () => {
    if (token) clientApi.logout(token).catch(() => {})
    sessionStorage.removeItem(clientApi.tokenKey)
    setToken(''); setProfile(null); setNodes([]); setJobs([])
  }, [token])

  const refresh = useCallback(async (quiet = false) => {
    if (!token) return
    if (!quiet) setRefreshing(true)
    try {
      const [nextNodes, nextJobs] = await Promise.all([clientApi.nodes(token), clientApi.jobs(token)])
      setNodes(nextNodes); setJobs(nextJobs); setError('')
    } catch (e) {
      if (e.status === 401) { await signOut(); setError('Your session expired. Sign in again.') }
      else setError(e.message || 'Could not load the grid right now.')
    } finally { if (!quiet) setRefreshing(false) }
  }, [token, signOut])

  useEffect(() => {
    if (!token) return
    let active = true
    setLoading(true)
    clientApi.me(token).then((data) => { if (active) setProfile(data) })
      .then(() => active && refresh(true))
      .catch(async (e) => {
        if (active) {
          if (e.status === 401) { await signOut(); setError('Your session expired. Sign in again.') }
          else setError(e.message || 'Could not authenticate this session.')
        }
      })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [token, refresh, signOut])

  useEffect(() => {
    if (!token || !profile) return undefined
    const timer = window.setInterval(() => refresh(true), 4000)
    return () => window.clearInterval(timer)
  }, [token, profile, refresh])

  const activeJobs = jobs.filter((job) => ['QUEUED', 'ASSIGNED', 'RUNNING'].includes(job.status))
  const onlineNodes = nodes.filter((node) => node.status === 'ONLINE')
  const totals = useMemo(() => onlineNodes.reduce((sum, node) => ({
    cpu: sum.cpu + (node.cpuFree || 0), ram: sum.ram + (node.ramFreeMb || 0), disk: sum.disk + (node.diskFreeMb || 0),
  }), { cpu: 0, ram: 0, disk: 0 }), [onlineNodes])

  const login = async (username, password) => {
    setError(''); setLoading(true)
    try {
      const session = await clientApi.login(username, password)
      sessionStorage.setItem(clientApi.tokenKey, session.accessToken)
      setProfile(session); setToken(session.accessToken)
    } catch (e) { setError(e.message || 'Could not sign in.') }
    finally { setLoading(false) }
  }

  const register = async (username, password) => {
    setError(''); setLoading(true)
    try {
      const session = await clientApi.register(username, password)
      sessionStorage.setItem(clientApi.tokenKey, session.accessToken)
      setProfile(session); setToken(session.accessToken)
    } catch (e) { setError(e.message || 'Could not create your account.') }
    finally { setLoading(false) }
  }

  const submit = async (event) => {
    event.preventDefault(); setError(''); setNotice(''); setSubmitting(true)
    try {
      await clientApi.submit(token, {
        command: form.command.trim(),
        cpuReq: Number(form.cpuReq),
        ramReqMb: Number(form.ramReqMb),
      })
      setNotice('Job submitted to the IdleGrid scheduler.')
      await refresh(true)
    } catch (e) { setError(e.message || 'Could not submit the job.') }
    finally { setSubmitting(false) }
  }

  const release = async (job) => {
    setError(''); setNotice('')
    try {
      await clientApi.cancel(token, job.id)
      setNotice('Release requested. The PC will become available after its Agent stops the container.')
      await refresh(true)
    } catch (e) { setError(e.message || 'Could not release this job.') }
  }

  if (!token) return <LoginScreen onLogin={login} onRegister={register} loading={loading} error={error} />
  if (loading && !profile) return <div className="boot-screen"><LoaderCircle className="spin" size={26} /> Loading your student workspace…</div>

  return <div className="app-frame">
    <aside className="sidebar">
      <a className="brand" href="#top"><span className="brand-mark"><Layers3 size={21} /></span><span>idlegrid<span className="brand-sub">STUDENT GRID</span></span></a>
      <div className="side-label">WORKSPACE</div>
      <a className="side-link active" href="#overview"><Activity size={16} />Overview</a>
      <a className="side-link" href="#jobs"><Terminal size={16} />My jobs</a>
      <a className="side-link" href="#terminal"><Terminal size={16} />Terminal</a>
      <a className="side-link" href="#storage"><HardDrive size={16} />Storage</a>
      <a className="side-link" href="#training"><Layers3 size={16} />DDP training</a>
      <div className="sidebar-bottom"><div className="secure-note"><ShieldCheck size={16} /><span>Student account<br /><b>Resource capped</b></span></div><button className="side-link signout" onClick={signOut}><LogOut size={16} />Sign out</button></div>
    </aside>

    <main className="main" id="top">
      <header className="topbar"><div className="crumb">Student workspace <span>/</span> Overview</div><div className="top-user"><span className="online-dot" /> Grid connected <span className="user-divider" /><UserRound size={16} /><b>{profile?.username}</b></div></header>
      <div className="content" id="overview">
        <div className="welcome-row"><div><p className="eyebrow">COMPUTE ACCESS · STUDENT</p><h1>Your lab, <em>on demand.</em></h1><p className="lede">Submit a capped compute job and follow it across the available lab PCs.</p></div><button className="icon-button" onClick={() => refresh()} title="Refresh" disabled={refreshing}>{refreshing ? <LoaderCircle className="spin" size={17} /> : <RefreshCw size={17} />}</button></div>

        {error && <div className="notice error"><CircleAlert size={17} />{error}<button onClick={() => setError('')}><XCircle size={16} /></button></div>}
        {notice && <div className="notice success"><Check size={17} />{notice}<button onClick={() => setNotice('')}><XCircle size={16} /></button></div>}

        <div className="stat-grid">
          <Stat label="ONLINE LAB PCs" value={`${onlineNodes.length} / ${nodes.length}`} icon={<Server size={18} />} detail="Currently reporting to Master" />
          <Stat label="FREE CPU IN GRID" value={`${totals.cpu.toFixed(1)}`} unit="cores" icon={<Cpu size={18} />} detail="Across online PCs" />
          <Stat label="FREE MEMORY" value={formatMemory(totals.ram)} icon={<MemoryStick size={18} />} detail="Available capacity estimate" />
          <Stat label="YOUR ACTIVE JOBS" value={activeJobs.length} icon={<Activity size={18} />} detail="Queued, assigned, or running" />
        </div>

        <div className="section-heading"><div><p className="eyebrow">RESOURCE POOL</p><h2>Available lab PCs</h2></div><span className="live-tag"><Radio size={13} /> LIVE</span></div>
        <div className="node-grid">
          {nodes.length ? nodes.map((node) => <NodeCard node={node} key={node.id} />) : <div className="empty-card">Waiting for the lab PCs to report in.</div>}
        </div>

        <div className="lower-grid" id="jobs">
          <section className="panel submit-panel">
            <div className="panel-heading"><div className="panel-icon"><Play size={16} /></div><div><h2>Submit a job</h2><p>Requests are scheduled against the live lab capacity.</p></div></div>
            <form onSubmit={submit} className="job-form">
              <label>Command to run<textarea required rows={3} value={form.command} onChange={(e) => setForm({ ...form, command: e.target.value })} placeholder="python3 script.py --help" /></label>
              <div className="form-pair">
                <label>CPU cores<input type="number" min="0.1" step="0.1" value={form.cpuReq} onChange={(e) => setForm({ ...form, cpuReq: e.target.value })} /></label>
                <label>Memory (MB)<input type="number" min="1" step="128" value={form.ramReqMb} onChange={(e) => setForm({ ...form, ramReqMb: e.target.value })} /></label>
              </div>
              <p className="form-hint">Requests are placed against the same live CPU and memory capacity shown above.</p>
              <button className="primary-button" disabled={submitting}><Play size={15} fill="currentColor" />{submitting ? 'Submitting…' : 'Submit compute job'}<ArrowUpRight size={15} /></button>
            </form>
          </section>

          <section className="panel jobs-panel">
            <div className="panel-heading"><div className="panel-icon muted"><Terminal size={16} /></div><div><h2>Your jobs</h2><p>Only jobs submitted by {profile?.username} appear here.</p></div></div>
            <div className="jobs-list">
              {jobs.length ? jobs.map((job) => <JobRow key={job.id} job={job} onRelease={release} />) : <div className="empty-jobs"><CircleDashed size={22} /><span>No jobs submitted yet.</span></div>}
            </div>
          </section>
        </div>
        <div className="tools-stack">
          <ClientTerminal token={token} onSubmitted={() => refresh(true)} />
          <ClientStorage token={token} />
          <ClientTraining token={token} />
        </div>
        <footer className="footer"><span>IdleGrid student access</span><span><HardDrive size={13} /> Grid capacity refreshes every 4 seconds</span></footer>
      </div>
    </main>
  </div>
}

function LoginScreen({ onLogin, onRegister, loading, error }) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [mode, setMode] = useState('login')
  const [formError, setFormError] = useState('')
  const [visible, setVisible] = useState(false)
  return <main className="login-screen"><section className="login-art"><div className="login-brand"><span className="brand-mark"><Layers3 size={21} /></span> idlegrid</div><div className="art-content"><div className="art-kicker"><span className="online-dot" /> CAMPUS COMPUTE GRID</div><h1>More compute.<br /><em>Less waiting.</em></h1><p>Run your work across available lab PCs with a resource cap for every job.</p><div className="art-orbit orbit-one" /><div className="art-orbit orbit-two" /><div className="art-node n1" /><div className="art-node n2" /><div className="art-node n3" /></div><div className="art-footer">IDLEGRID · STUDENT CLIENT <span>v0.1</span></div></section>
    <section className="login-side"><div className="login-card"><div className="login-icon"><KeyRound size={20} /></div><p className="eyebrow">STUDENT ACCESS</p><h2>{mode === 'login' ? 'Sign in to your grid' : 'Create your account'}</h2><p className="login-copy">{mode === 'login' ? 'Sign in or create an account to use the lab compute grid.' : 'Choose a username and password for your student account.'}</p>
      <form onSubmit={(e) => { e.preventDefault(); setFormError(''); if (mode === 'register' && password !== confirmPassword) { setFormError('The passwords do not match.'); return } (mode === 'login' ? onLogin : onRegister)(username, password) }}><label>Username<input autoComplete="username" minLength={3} maxLength={32} pattern="[A-Za-z0-9._-]+" required value={username} onChange={(e) => setUsername(e.target.value)} placeholder="3–32 letters, numbers, . _ -" /></label><label>Password<div className="password-wrap"><input autoComplete={mode === 'login' ? 'current-password' : 'new-password'} minLength={mode === 'register' ? 8 : undefined} maxLength={128} required type={visible ? 'text' : 'password'} value={password} onChange={(e) => setPassword(e.target.value)} placeholder={mode === 'register' ? 'at least 8 characters' : 'your password'} /><button type="button" onClick={() => setVisible(!visible)}>{visible ? 'HIDE' : 'SHOW'}</button></div></label>{mode === 'register' && <label>Confirm password<input autoComplete="new-password" minLength={8} required type={visible ? 'text' : 'password'} value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} placeholder="enter password again" /></label>}{(formError || error) && <div className="login-error"><CircleAlert size={15} />{formError || error}</div>}<button className="primary-button login-submit" disabled={loading}>{loading ? <><LoaderCircle className="spin" size={16} /> {mode === 'login' ? 'Signing in…' : 'Creating account…'}</> : mode === 'login' ? <>Continue to dashboard <ArrowUpRight size={16} /></> : <>Register and continue <ArrowUpRight size={16} /></>}</button></form><button type="button" className="auth-toggle" onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setFormError('') }}>{mode === 'login' ? 'New to IdleGrid? Create an account' : 'Already registered? Sign in'}</button><div className="login-lock"><ShieldCheck size={14} /> Your session is stored for this browser tab only.</div>
    </div><div className="login-bottom">Student accounts are registered on this Master.</div></section>
  </main>
}

function Stat({ label, value, unit, icon, detail }) {
  return <div className="stat-card"><div className="stat-top"><span>{label}</span><span className="stat-icon">{icon}</span></div><div className="stat-value">{value}<small>{unit}</small></div><div className="stat-detail">{detail}</div></div>
}

function NodeCard({ node }) {
  const online = node.status === 'ONLINE'
  const cpuPct = node.cpuTotal ? Math.max(0, Math.min(100, node.cpuFree / node.cpuTotal * 100)) : 0
  const ramPct = node.ramTotalMb ? Math.max(0, Math.min(100, node.ramFreeMb / node.ramTotalMb * 100)) : 0
  return <article className="node-card"><div className="node-top"><div className="node-title"><span className="node-symbol"><Server size={16} /></span><div><b title={node.id}>{node.id}</b><small>{node.ip || 'IP not reported'}</small></div></div><span className={`node-status ${online ? 'is-online' : ''}`}><span />{node.status}</span></div><ResourceBar icon={<Cpu size={14} />} label="CPU free" value={`${Number(node.cpuFree || 0).toFixed(1)} / ${node.cpuTotal || 0} cores`} percent={cpuPct} /><ResourceBar icon={<MemoryStick size={14} />} label="Memory free" value={formatMemory(node.ramFreeMb)} percent={ramPct} /><ResourceBar icon={<HardDrive size={14} />} label="Storage free" value={formatMemory(node.diskFreeMb)} percent={node.diskTotalMb ? node.diskFreeMb / node.diskTotalMb * 100 : 0} /></article>
}

function ResourceBar({ icon, label, value, percent }) {
  return <div className="resource-row"><div className="resource-label">{icon}<span>{label}</span><b>{value}</b></div><div className="resource-track"><span style={{ width: `${Math.min(100, Math.max(0, percent || 0))}%` }} /></div></div>
}

function JobRow({ job, onRelease }) {
  const active = ['QUEUED', 'ASSIGNED', 'RUNNING'].includes(job.status)
  return <article className="job-row"><div className="job-status-icon">{job.status === 'RUNNING' ? <LoaderCircle className="spin" size={16} /> : job.status === 'DONE' ? <Check size={16} /> : job.status === 'FAILED' ? <XCircle size={16} /> : <CircleDashed size={16} />}</div><div className="job-info"><div className="job-line"><b>{job.command || 'Compute job'}</b><span className={`job-status ${statusStyles[job.status] || ''}`}>{job.status}</span></div><div className="job-meta"><span>{job.cpuReq} CPU · {job.ramReqMb} MB</span><span>Node: {job.assignedNode || 'waiting for placement'}</span><span>{job.createdAt ? new Date(job.createdAt).toLocaleString() : ''}</span></div>{job.result && <p className={`job-result ${job.status === 'FAILED' ? 'bad' : ''}`}>{job.result}</p>}</div>{active && <button className="release-button" title="Release job" onClick={() => onRelease(job)}><Square size={14} /> Release</button>}</article>
}

function formatMemory(value = 0) {
  const mb = Number(value) || 0
  return mb >= 1024 ? `${(mb / 1024).toFixed(1)} GB` : `${Math.round(mb)} MB`
}
