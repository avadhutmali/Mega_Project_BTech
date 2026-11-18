const API_BASE = import.meta.env.VITE_MASTER_URL || '/api'
const TOKEN_KEY = 'idlegrid-client-token'

async function request(path, { token, ...options } = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: {
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  })
  if (!response.ok) {
    const body = await response.text()
    let message = body || `Request failed (${response.status})`
    try { message = JSON.parse(body).detail || JSON.parse(body).message || message } catch (_) {}
    const error = new Error(message)
    error.status = response.status
    throw error
  }
  return response.status === 204 ? null : response.json()
}

export const clientApi = {
  tokenKey: TOKEN_KEY,
  login: (username, password) => request('/client/auth/login', {
    method: 'POST', body: JSON.stringify({ username, password }),
  }),
  register: (username, password) => request('/client/auth/register', {
    method: 'POST', body: JSON.stringify({ username, password }),
  }),
  logout: (token) => request('/client/auth/logout', { method: 'POST', token }),
  me: (token) => request('/client/auth/me', { token }),
  nodes: (token) => request('/client/nodes', { token }),
  jobs: (token) => request('/client/jobs', { token }),
  job: (token, id) => request(`/client/jobs/${encodeURIComponent(id)}`, { token }),
  submit: (token, payload) => request('/client/jobs', {
    method: 'POST', token, body: JSON.stringify(payload),
  }),
  cancel: (token, id) => request(`/client/jobs/${encodeURIComponent(id)}/cancel`, { method: 'POST', token }),
  storageSummary: (token) => request('/client/storage/summary', { token }),
  storedFiles: (token) => request('/client/storage/files', { token }),
  uploadFile: async (token, file) => {
    const body = new FormData()
    body.append('file', file)
    const response = await fetch(`${API_BASE}/client/storage/files`, {
      method: 'POST', headers: { Authorization: `Bearer ${token}` }, body,
    })
    if (!response.ok) throw new Error((await response.text()) || `Upload failed (${response.status})`)
    return response.json()
  },
  downloadFile: async (token, key) => {
    const response = await fetch(`${API_BASE}/client/storage/files/${encodeURIComponent(key)}/content`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    if (!response.ok) throw new Error((await response.text()) || `Download failed (${response.status})`)
    return response.blob()
  },
  trainingRuns: (token) => request('/client/training/runs', { token }),
  trainingRun: (token, runId) => request(`/client/training/runs/${encodeURIComponent(runId)}`, { token }),
  createTrainingRun: async (token, { bundle, command, workers, cpuReq, ramReqMb, image, outputFile }) => {
    const body = new FormData()
    body.append('bundle', bundle)
    body.append('command', command)
    body.append('workers', String(workers))
    body.append('cpuReq', String(cpuReq))
    body.append('ramReqMb', String(ramReqMb))
    body.append('image', image)
    body.append('outputFile', outputFile)
    const response = await fetch(`${API_BASE}/client/training/runs`, {
      method: 'POST', headers: { Authorization: `Bearer ${token}` }, body,
    })
    if (!response.ok) throw new Error((await response.text()) || `Could not start run (${response.status})`)
    return response.json()
  },
  downloadArtifact: async (token, runId) => {
    const response = await fetch(`${API_BASE}/client/training/runs/${encodeURIComponent(runId)}/artifact`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    if (!response.ok) throw new Error((await response.text()) || `Artifact download failed (${response.status})`)
    return response.blob()
  },
}
