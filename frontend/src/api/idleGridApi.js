// In dev (`npm run dev`), Vite proxies /api → VITE_MASTER_URL (see vite.config.js).
// In production builds, we hit the master directly — set VITE_MASTER_URL in .env.local
// to the IP of whichever lab PC is running the Spring Boot backend, e.g.:
//   VITE_MASTER_URL=http://192.168.1.45:9090
const API_BASE = import.meta.env.VITE_MASTER_URL
  ? `${import.meta.env.VITE_MASTER_URL}`   // prod: direct call, no proxy
  : '/api'                                   // dev fallback (proxy handles it)


async function request(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: { 'Content-Type': 'application/json', ...options.headers },
    ...options,
  })

  if (!response.ok) {
    throw new Error(`Request failed with status ${response.status}`)
  }

  if (response.status === 204) return null
  return response.json()
}

export const idleGridApi = {
  getNodes: () => request('/nodes/summary'),
  getJobs: () => request('/jobs'),
  getJobStatus: (jobId) => request(`/jobs/${jobId}/status`),
  submitJob: (payload) => request('/jobs/submit', {
    method: 'POST',
    body: JSON.stringify(payload),
  }),
}
