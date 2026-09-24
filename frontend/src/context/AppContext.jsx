import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { idleGridApi } from '../api/idleGridApi'

const AppContext = createContext(null)

export function AppProvider({ children }) {
  const [nodes, setNodes] = useState([])
  const [jobs, setJobs] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const refresh = useCallback(async () => {
    try {
      const [nextNodes, nextJobs] = await Promise.all([
        idleGridApi.getNodes(),
        idleGridApi.getJobs(),
      ])
      setNodes(nextNodes || [])
      setJobs(nextJobs || [])
      setError('')
    } catch (requestError) {
      setError(requestError.message || 'Unable to reach the Master service')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    refresh()
    const interval = window.setInterval(refresh, 3000)
    return () => window.clearInterval(interval)
  }, [refresh])

  const submitJob = async (payload) => {
    const created = await idleGridApi.submitJob(payload)
    await refresh()
    return created
  }

  const onlineNodes = nodes.filter((node) => node.status === 'ONLINE')
  const runningJobs = jobs.filter((job) => ['ASSIGNED', 'RUNNING'].includes(job.status))

  return (
    <AppContext.Provider value={{
      nodes, jobs, onlineNodes, runningJobs, loading, error, refresh, submitJob,
    }}>
      {children}
    </AppContext.Provider>
  )
}

export function useApp() {
  return useContext(AppContext)
}
