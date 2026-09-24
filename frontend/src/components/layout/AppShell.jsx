import { Outlet } from 'react-router-dom'
import Sidebar from './Sidebar'
import TopBar from './TopBar'

export default function AppShell() {
  return (
    <div className="min-h-screen bg-canvas text-ink transition-colors dark:bg-[#171412] dark:text-[#f8f3ed]">
      <Sidebar />
      <div className="lg:pl-[248px]">
        <TopBar />
        <main className="mx-auto max-w-[1440px] px-5 pb-12 pt-8 sm:px-8 lg:px-12">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
