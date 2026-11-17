import { Fragment, useEffect, useLayoutEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { motion, useReducedMotion } from 'motion/react'
import {
  Bar, BarChart, CartesianGrid, Cell, Pie, PieChart, ResponsiveContainer,
  Tooltip, XAxis, YAxis,
} from 'recharts'
import { parseReport, statusMeta, type QaReport, type TestCase, type TestStatus } from './types'
import SparkleButton from './components/SparkleButton'

type Page = 'Overview' | 'Analytics' | 'Test Cases'
type SortKey = 'name' | 'status' | 'durationSeconds'
const pages: Page[] = ['Overview', 'Analytics', 'Test Cases']
const statusOrder: Record<TestStatus, number> = { Passed: 0, Failed: 1, Error: 2, Skipped: 3 }

function Icon({ name, size = 18 }: { name: string; size?: number }) {
  const common = { width: size, height: size, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: 1.7, strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const, 'aria-hidden': true as const }
  if (name === 'overview') return <svg {...common}><rect x="3.5" y="3.5" width="7" height="7" rx="1.5"/><rect x="13.5" y="3.5" width="7" height="4" rx="1.5"/><rect x="13.5" y="10.5" width="7" height="10" rx="1.5"/><rect x="3.5" y="13.5" width="7" height="7" rx="1.5"/></svg>
  if (name === 'analytics') return <svg {...common}><path d="M4 19.5h16"/><path d="M6.5 15V9m5 6V4.5m5 10.5v-4"/><circle cx="6.5" cy="7" r="1"/><circle cx="11.5" cy="3" r="1"/><circle cx="16.5" cy="9" r="1"/></svg>
  if (name === 'cases') return <svg {...common}><path d="M8 5.5h11M8 12h11M8 18.5h11"/><path d="m3.5 5.5 1 1 2-2M3.5 12l1 1 2-2M3.5 18.5l1 1 2-2"/></svg>
  if (name === 'search') return <svg {...common}><circle cx="10.8" cy="10.8" r="6.2"/><path d="m15.3 15.3 4.4 4.4"/></svg>
  if (name === 'chevron') return <svg {...common}><path d="m9 18 6-6-6-6"/></svg>
  if (name === 'menu') return <svg {...common}><path d="M4 7h16M4 12h16M4 17h16"/></svg>
  if (name === 'close') return <svg {...common}><path d="m6 6 12 12M18 6 6 18"/></svg>
  if (name === 'arrow') return <svg {...common}><path d="M5 12h14m-6-6 6 6-6 6"/></svg>
  if (name === 'refresh') return <svg {...common}><path d="M20 7v5h-5M4 17v-5h5"/><path d="M5.5 9a7 7 0 0 1 12-2l2.5 2M4 15l2.5 2a7 7 0 0 0 12-2"/></svg>
  if (name === 'passed') return <svg {...common}><path d="m5 12.5 4.2 4.2L19 7"/></svg>
  if (name === 'failed') return <svg {...common}><path d="m7 7 10 10M17 7 7 17"/></svg>
  if (name === 'error') return <svg {...common}><path d="M12 8v5m0 3h.01"/><circle cx="12" cy="12" r="9"/></svg>
  if (name === 'skipped') return <svg {...common}><path d="M6 12h12"/></svg>
  return <svg {...common}><circle cx="12" cy="12" r="8.5"/><path d="M12 8v4.5l3 1.7"/></svg>
}

function duration(value: number | null) {
  if (value === null) return 'Not reported'
  if (!Number.isFinite(value) || value <= 0) return '0.0s'
  if (value < 60) return `${value.toFixed(1)}s`
  const rounded = Math.round(value)
  const mins = Math.floor(rounded / 60)
  const seconds = rounded % 60
  return seconds ? `${mins}m ${seconds}s` : `${mins}m`
}

function formatGeneratedAt(value?: string) {
  if (!value || Number.isNaN(Date.parse(value))) return 'Not provided'
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

function compareDuration(a: number | null, b: number | null, direction: 1 | -1) {
  if (a === null) return b === null ? 0 : 1
  if (b === null) return -1
  return (a - b) * direction
}

function StatusTag({ status }: { status: TestStatus }) {
  return <span className={`status-tag status-${status.toLowerCase()}`}><StatusIcon status={status} />{statusMeta[status].label}</span>
}

function TestName({ name }: { name: string }) {
  return name.split(/(?<=[a-z0-9])(?=[A-Z])|(?<=[_,.])/).map((part, index) => <Fragment key={index}>{index > 0 && <wbr />}{part}</Fragment>)
}

function StatusIcon({ status, size = 12 }: { status: TestStatus; size?: number }) {
  return <Icon name={status.toLowerCase()} size={size} />
}

function statusFromLabel(label?: string): TestStatus | null {
  if (label === 'Passed') return 'Passed'
  if (label === 'Failed') return 'Failed'
  if (label === 'Errors' || label === 'Error') return 'Error'
  if (label === 'Skipped') return 'Skipped'
  return null
}

function reportInconsistencies(report: QaReport) {
  const messages: string[] = []
  const counts: Record<TestStatus, number> = { Passed: 0, Failed: 0, Error: 0, Skipped: 0 }
  for (const test of report.cases) counts[test.status] += 1
  if (report.cases.length !== report.summary.total) messages.push(`The summary reports ${report.summary.total} tests, while the case list contains ${report.cases.length}.`)
  const expectedPassRate = report.summary.total ? report.summary.passed / report.summary.total * 100 : 0
  if (Math.abs(report.summary.passRate - expectedPassRate) > .1) messages.push(`The reported pass rate is ${report.summary.passRate.toFixed(1)}%, while the summary counts imply ${expectedPassRate.toFixed(1)}%.`)
  const summaryByStatus: Record<TestStatus, number> = { Passed: report.summary.passed, Failed: report.summary.failed, Error: report.summary.errors, Skipped: report.summary.skipped }
  for (const status of Object.keys(counts) as TestStatus[]) {
    if (counts[status] !== summaryByStatus[status]) messages.push(`${statusMeta[status].label}: summary ${summaryByStatus[status]}, case list ${counts[status]}.`)
  }
  if (report.categories.length) {
    const categoryTotal = report.categories.reduce((total, category) => total + category.total, 0)
    if (categoryTotal !== report.summary.total) messages.push(`Category totals add up to ${categoryTotal}, while the summary reports ${report.summary.total} tests.`)
    const categoryStatusTotals: Record<TestStatus, number> = { Passed: 0, Failed: 0, Error: 0, Skipped: 0 }
    for (const category of report.categories) {
      const expectedRate = category.total ? category.passed / category.total * 100 : 0
      if (Math.abs(category.passRate - expectedRate) > .1) messages.push(`${category.name || 'Unnamed category'}: the reported pass rate does not match its test counts.`)
      categoryStatusTotals.Passed += category.passed
      categoryStatusTotals.Failed += category.failed
      categoryStatusTotals.Error += category.errors
      categoryStatusTotals.Skipped += category.skipped
    }
    for (const status of Object.keys(categoryStatusTotals) as TestStatus[]) {
      if (categoryStatusTotals[status] !== summaryByStatus[status]) messages.push(`${statusMeta[status].label}: summary ${summaryByStatus[status]}, categories ${categoryStatusTotals[status]}.`)
    }
  }
  return messages
}

function Panel({ title, subtitle, action, children, className = '' }: { title: string; subtitle?: string; action?: ReactNode; children: ReactNode; className?: string }) {
  return <section className={`panel ${className}`}>
    <div className="panel-heading"><div><h2>{title}</h2>{subtitle && <p>{subtitle}</p>}</div>{action}</div>
    {children}
  </section>
}

function App() {
  const reduceMotion = useReducedMotion()
  const [page, setPage] = useState<Page>('Overview')
  const [mobileOpen, setMobileOpen] = useState(false)
  const [report, setReport] = useState<QaReport | null>(null)
  const [loadError, setLoadError] = useState('')
  const [query, setQuery] = useState('')
  const [statusFilter, setStatusFilter] = useState('all')
  const [categoryFilter, setCategoryFilter] = useState('all')
  const [sortKey, setSortKey] = useState<SortKey>('durationSeconds')
  const [sortDirection, setSortDirection] = useState<1 | -1>(-1)
  const [expanded, setExpanded] = useState<number | null>(null)
  const [isMobile, setIsMobile] = useState(() => window.matchMedia('(max-width: 760px)').matches)
  const menuButtonRef = useRef<HTMLButtonElement>(null)
  const wasMobileOpen = useRef(false)
  const [focusTarget, setFocusTarget] = useState<'search' | number | null>(null)

  useEffect(() => {
    let active = true
    fetch(`${import.meta.env.BASE_URL}qa-dashboard.json`, { headers: { Accept: 'application/json' } })
      .then(async (response) => {
        if (response.status === 422) throw new Error('invalid-json')
        if (!response.ok) throw new Error(response.status === 404 ? 'missing-report' : 'report-request-failed')
        const body = await response.text()
        let data: unknown
        try { data = JSON.parse(body) } catch { throw new Error('invalid-json') }
        try { return parseReport(data) } catch (error) {
          throw new Error(`invalid-report:${error instanceof Error ? error.message : 'The report format is incomplete.'}`)
        }
      })
      .then((data) => { if (active) setReport(data) })
      .catch((error: unknown) => {
        if (!active) return
        const message = error instanceof Error ? error.message : ''
        setLoadError(message === 'invalid-json'
          ? 'The report file contains invalid JSON. Regenerate it from the Surefire reports.'
          : message.startsWith('invalid-report:')
            ? `The report data is incomplete or unsupported. ${message.slice('invalid-report:'.length)}`
            : message === 'missing-report'
              ? 'No QA report found. Generate it from Maven Surefire reports, then refresh this page.'
              : 'The report could not be loaded. Check that it is valid JSON, then try again.')
      })
    return () => { active = false }
  }, [])

  useEffect(() => {
    const media = window.matchMedia('(max-width: 760px)')
    const update = () => { setIsMobile(media.matches); if (!media.matches) setMobileOpen(false) }
    media.addEventListener('change', update)
    return () => media.removeEventListener('change', update)
  }, [])

  useEffect(() => {
    if (wasMobileOpen.current && !mobileOpen && isMobile) menuButtonRef.current?.focus()
    wasMobileOpen.current = mobileOpen
  }, [mobileOpen, isMobile])

  useEffect(() => {
    if (!mobileOpen) return
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const navigation = document.getElementById('main-navigation')
    const focusable = navigation?.querySelectorAll<HTMLElement>('a[href], button:not(:disabled), input:not(:disabled), select:not(:disabled), [tabindex]:not([tabindex="-1"])')
    const focusFrame = requestAnimationFrame(() => focusable?.[0]?.focus())
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        setMobileOpen(false)
        return
      }
      if (event.key !== 'Tab' || !focusable?.length) return
      const first = focusable[0]
      const last = focusable[focusable.length - 1]
      if (!navigation?.contains(document.activeElement)) {
        event.preventDefault()
        const target = event.shiftKey ? last : first
        target.focus()
      } else if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => {
      cancelAnimationFrame(focusFrame)
      document.body.style.overflow = previousOverflow
      window.removeEventListener('keydown', onKeyDown)
    }
  }, [mobileOpen])

  const categories = useMemo(() => report?.categories ?? [], [report])
  const consistencyMessages = useMemo(() => report ? reportInconsistencies(report) : [], [report])
  const filteredCases = useMemo(() => {
    if (!report) return []
    const normalized = query.trim().toLocaleLowerCase()
    return report.cases.filter((test) => {
      const text = `${test.name} ${test.className} ${test.category}`.toLocaleLowerCase()
      return (!normalized || text.includes(normalized))
        && (statusFilter === 'all' || test.status === statusFilter)
        && (categoryFilter === 'all' || test.category === categoryFilter)
    }).sort((a, b) => {
      if (sortKey === 'durationSeconds') return compareDuration(a.durationSeconds, b.durationSeconds, sortDirection)
      const result = sortKey === 'status' ? statusOrder[a.status] - statusOrder[b.status]
          : a.name.localeCompare(b.name, undefined, { numeric: true, sensitivity: 'base' })
      return result * sortDirection
    })
  }, [report, query, statusFilter, categoryFilter, sortKey, sortDirection])

  function navigate(next: Page) {
    setPage(next)
    setMobileOpen(false)
    setFocusTarget(next === 'Test Cases' ? 'search' : null)
  }

  function openCase(caseIndex: number) {
    setExpanded(caseIndex)
    setPage('Test Cases')
    setMobileOpen(false)
    setFocusTarget(caseIndex)
  }

  useLayoutEffect(() => {
    // Reset before paint; opening a specific case still scrolls to its details afterward.
    window.scrollTo({ top: 0, left: 0, behavior: 'instant' })
    if (typeof focusTarget === 'number') return
    const frame = requestAnimationFrame(() => window.scrollTo({ top: 0, left: 0, behavior: 'instant' }))
    return () => cancelAnimationFrame(frame)
  }, [page])

  useEffect(() => {
    if (page !== 'Test Cases' || focusTarget === null) return
    const target = focusTarget === 'search' ? document.getElementById('case-search')
      : document.querySelector<HTMLElement>(`#${isMobile ? 'mobile' : 'desktop'}-case-${focusTarget} .details-toggle`)
        ?? document.getElementById('selected-case')
    target?.focus({ preventScroll: true })
    if (focusTarget !== 'search') target?.scrollIntoView({ behavior: reduceMotion ? 'instant' : 'smooth', block: 'center' })
    setFocusTarget(null)
  }, [page, focusTarget, isMobile, reduceMotion])

  function sortBy(key: SortKey) {
    if (key === sortKey) setSortDirection((current) => current === 1 ? -1 : 1)
    else { setSortKey(key); setSortDirection(key === 'durationSeconds' ? -1 : 1) }
  }

  const viewVariants = reduceMotion ? { initial: { opacity: 1 }, enter: { opacity: 1 }, exit: { opacity: 1 } }
    : { initial: { opacity: 0, y: 8 }, enter: { opacity: 1, y: 0 }, exit: { opacity: 0, y: -5 } }

  return <div className={`app-shell ${page === 'Overview' ? 'overview-theme' : ''}`}>
    <a className="skip-link" href="#main-content">Skip to content</a>
    <aside id="main-navigation" className={`sidebar ${mobileOpen ? 'sidebar-open' : ''}`} aria-label="Main navigation" aria-hidden={isMobile && !mobileOpen} inert={isMobile && !mobileOpen}>
      <button className="icon-button sidebar-close" aria-label="Close navigation" onClick={() => setMobileOpen(false)}><Icon name="close" /></button>
      <a className="brand" href="#overview" onClick={(event) => { event.preventDefault(); navigate('Overview') }}>
        <span className="brand-mark"><span /><span /><span /></span><span className="brand-name">quality<span>works</span></span>
      </a>
      <div className="workspace-pill"><span className="workspace-dot" />QA Workspace</div>
      <nav className="main-nav" aria-label="Dashboard pages">
        <span className="nav-label">Workspace</span>
        {pages.map((item) => <button key={item} type="button" className={`nav-link ${page === item ? 'active' : ''}`} onClick={() => navigate(item)} aria-current={page === item ? 'page' : undefined}>
          {page === item && <motion.span layoutId="active-nav" className="active-rail" transition={reduceMotion ? { duration: 0 } : { duration: .3, ease: [0, 0, .2, 1] }} />}
          <Icon name={item === 'Overview' ? 'overview' : item === 'Analytics' ? 'analytics' : 'cases'} /><span>{item}</span>
          {item === 'Test Cases' && report && <span className="nav-count">{report.summary.total}</span>}
        </button>)}
      </nav>
      <div className="sidebar-bottom">
        <div className="source-note"><span className="source-pulse" /><div><strong>Surefire report</strong><small>Generated execution data</small></div></div>
        <div className="user-chip"><span className="avatar">QA</span><span><strong>QA Dashboard</strong><small>Portfolio edition</small></span></div>
      </div>
    </aside>
    {mobileOpen && <button className="mobile-scrim" tabIndex={-1} aria-label="Close navigation backdrop" onClick={() => setMobileOpen(false)} />}

    <div className="main-column" inert={isMobile && mobileOpen}>
      <header className="topbar">
        <button ref={menuButtonRef} className="icon-button menu-toggle" aria-label={mobileOpen ? 'Close menu' : 'Open menu'} aria-expanded={mobileOpen} aria-controls="main-navigation" onClick={() => setMobileOpen((open) => !open)}><Icon name={mobileOpen ? 'close' : 'menu'} /></button>
        <div className="breadcrumbs"><span>Dashboard</span><Icon name="chevron" size={14} /><strong>{page}</strong></div>
        <div className="topbar-right"><span className="run-indicator"><i />{report ? 'Report available' : 'Waiting for report'}</span><button className="top-search" aria-label="Search test cases" onClick={() => navigate('Test Cases')}><Icon name="search" size={16} /><span>Find a test</span></button></div>
      </header>
      <main id="main-content" className="content-area" tabIndex={-1}>
        <div className="page-intro">
          <div><h1>{page}</h1><p>{page === 'Overview' ? 'A clear view of your latest test execution.' : page === 'Analytics' ? 'Understand where quality and execution time are concentrated.' : 'Search and inspect every recorded test execution.'}</p></div>
          <div className="page-actions"><span className="dataset-label"><span className="dataset-dot" />Single execution</span>{page === 'Overview' ? <SparkleButton onClick={() => navigate('Test Cases')}>Explore tests</SparkleButton> : page !== 'Test Cases' && <button className="text-button" onClick={() => navigate('Test Cases')}>Explore tests <Icon name="arrow" size={15} /></button>}</div>
        </div>

        {loadError ? <div className="report-state report-error" role="alert"><span className="state-icon"><Icon name="refresh" size={20} /></span><div><h2>Results aren’t available</h2><p>{loadError}</p></div><button className="secondary-button" onClick={() => window.location.reload()}>Try again</button></div>
          : !report ? <div className="report-state" role="status"><span className="state-spinner" /><div><h2>Loading execution data</h2><p>Reading the latest Maven Surefire report.</p></div></div>
            : <motion.div key={page} className="view-content" variants={viewVariants} initial="initial" animate="enter" transition={reduceMotion ? { duration: 0 } : { duration: .2, ease: [0, 0, .2, 1] }}>
                {consistencyMessages.length > 0 && <aside className="data-integrity-note" role="status" aria-live="polite"><Icon name="error" size={15} /><div><strong>Report totals do not match</strong><ul>{consistencyMessages.map((message) => <li key={message}>{message}</li>)}</ul></div></aside>}
                {page === 'Overview' && <Overview report={report} onExplore={(caseIndex) => caseIndex === undefined ? navigate('Test Cases') : openCase(caseIndex)} />}
                {page === 'Analytics' && <Analytics report={report} onExplore={(caseIndex) => caseIndex === undefined ? navigate('Test Cases') : openCase(caseIndex)} />}
                {page === 'Test Cases' && <TestCases report={report} cases={filteredCases} query={query} setQuery={setQuery} statusFilter={statusFilter} setStatusFilter={setStatusFilter} categoryFilter={categoryFilter} setCategoryFilter={setCategoryFilter} categories={categories.map((item) => item.name)} sortKey={sortKey} sortDirection={sortDirection} sortBy={sortBy} expanded={expanded} setExpanded={setExpanded} />}
              </motion.div>}
        <footer className="footer"><span>Data source: Maven Surefire XML reports</span><span>{report ? `${report.cases.length} executions in this report` : 'No external services'}</span></footer>
      </main>
    </div>
  </div>
}

function Overview({ report, onExplore }: { report: QaReport; onExplore: (caseIndex?: number) => void }) {
  const { summary } = report
  const distribution = statusDistribution(report)
  const attention = summary.failed + summary.errors > 0
  const runText = !summary.total ? 'No tests recorded' : attention ? 'Attention needed' : summary.skipped ? 'Completed with skipped tests' : 'All tests passed'
  return <div className="view-stack overview-view">
    <section className="execution-summary" aria-labelledby="execution-title">
      <div className="execution-heading"><div><h2 id="execution-title">Current execution</h2><p>Generated {formatGeneratedAt(report.generatedAt)} · Maven Surefire</p></div>
        <span className={`run-status ${!summary.total ? '' : attention ? 'tone-danger' : summary.skipped ? 'tone-warn' : 'tone-good'}`}><Icon name={!summary.total ? 'clock' : attention ? 'error' : summary.skipped ? 'skipped' : 'passed'} size={16} />{runText}</span>
      </div>
      <div className="summary-facts">
        <div className="summary-total"><span>Total test cases</span><strong>{summary.total.toLocaleString()}</strong><small>{report.categories.length} {report.categories.length === 1 ? 'category' : 'categories'} in this report</small></div>
        <div><span>Pass rate</span><strong>{summary.passRate.toFixed(1)}<small>%</small></strong><small>Reported share of tests passed</small></div>
        <div><span>Accumulated test time</span><strong>{duration(summary.durationSeconds)}</strong><small>Sum of test durations, not elapsed run time</small></div>
      </div>
      <div className="outcome-strip" aria-hidden="true">{distribution.filter((entry) => entry.value > 0).map((entry) => <span key={entry.status} style={{ flexGrow: entry.value, background: entry.color }} />)}</div>
      <div className="summary-statuses">{distribution.map((entry) => <div key={entry.status}><StatusTag status={entry.status} /><strong>{entry.value.toLocaleString()}</strong><small>{summary.total ? (entry.value / summary.total * 100).toFixed(1) : '0.0'}% of run</small></div>)}</div>
    </section>
    <div className="overview-grid">
      <Panel title="Tests in this report" subtitle="First six records in source order" className="preview-panel" action={<SparkleButton size="sm" onClick={() => onExplore()}>View all</SparkleButton>}>
        {report.cases.length ? <div className="compact-list">{report.cases.slice(0, 6).map((test, caseIndex) => <div className="compact-row" key={caseIndex}>
          <div className="compact-test"><strong><TestName name={test.name} /></strong><span>{test.className || 'Class not provided'}</span></div>
          <StatusTag status={test.status} /><span className="compact-duration">{duration(test.durationSeconds)}</span>
          <button className="row-open" onClick={() => onExplore(caseIndex)} aria-label={`Open details for ${test.name}`}><Icon name="chevron" size={18} /></button>
        </div>)}</div> : <EmptyChart message="This report does not contain any test cases." />}
      </Panel>
      <Panel title="Results by category" subtitle="Recorded tests · bars show pass rate" className="category-panel">
        {report.categories.length ? <div className="category-list">{report.categories.map((category, index) => <div className="category-row" key={`${category.name}-${index}`}>
          <div className="category-top"><strong>{category.name || 'Not provided'}</strong><span>{category.total} tests</span></div>
          <div className="category-track" aria-hidden="true"><span style={{ width: `${Math.min(100, Math.max(0, category.passRate))}%` }} /></div>
          <div className="category-caption"><span><StatusIcon status="Passed" />{category.passRate.toFixed(1)}% passed</span><span>{duration(category.durationSeconds)}</span></div>
        </div>)}</div> : <EmptyChart message="No category data was provided." />}
      </Panel>
    </div>
  </div>
}

function statusDistribution(report: QaReport) {
  return [
    { status: 'Passed' as const, name: 'Passed', value: report.summary.passed, color: statusMeta.Passed.color },
    { status: 'Failed' as const, name: 'Failed', value: report.summary.failed, color: statusMeta.Failed.color },
    { status: 'Error' as const, name: 'Errors', value: report.summary.errors, color: statusMeta.Error.color },
    { status: 'Skipped' as const, name: 'Skipped', value: report.summary.skipped, color: statusMeta.Skipped.color },
  ]
}

function Analytics({ report, onExplore }: { report: QaReport; onExplore: (caseIndex?: number) => void }) {
  const statusData = statusDistribution(report)
  const categoryData = report.categories.map((category) => ({ name: category.name || 'Not provided', Passed: category.passed, Failed: category.failed, Errors: category.errors, Skipped: category.skipped, duration: category.durationSeconds }))
  const maxDuration = Math.max(0, ...categoryData.map((category) => category.duration))
  const slowTests = report.cases.filter((test): test is TestCase & { durationSeconds: number } => test.durationSeconds !== null)
    .sort((a, b) => b.durationSeconds - a.durationSeconds).slice(0, 7)
  return <div className="view-stack analytics-view">
    <div className="analytics-callout"><Icon name="analytics" size={22} /><div><strong>One execution, fully accounted for</strong><p>Generated {formatGeneratedAt(report.generatedAt)}. Durations are accumulated test time. This report contains no execution history.</p></div></div>
    <div className="analytics-grid">
      <Panel title="Outcomes by category" subtitle="Recorded test counts, starting at zero" className="outcomes-panel">
        {categoryData.length ? <>
          <div className="chart-container" style={{ height: Math.max(210, categoryData.length * 56) }}><ResponsiveContainer width="100%" height="100%"><BarChart data={categoryData} layout="vertical" margin={{ top: 8, right: 16, bottom: 4, left: 0 }} barSize={16}>
            <CartesianGrid stroke="var(--c-700)" horizontal={false} /><XAxis type="number" domain={[0, 'auto']} tickLine={false} axisLine={false} tick={{ fill: 'var(--c-300)', fontSize: 12 }} allowDecimals={false} />
            <YAxis type="category" dataKey="name" width={86} tickLine={false} axisLine={false} tick={{ fill: 'var(--c-100)', fontSize: 12 }} tickFormatter={(value: string) => value.length > 12 ? value.slice(0, 11) + '…' : value} />
            <Tooltip content={<ChartTooltip />} isAnimationActive={false} cursor={{ fill: 'var(--hover)' }} />
            <Bar dataKey="Passed" stackId="a" fill={statusMeta.Passed.color} isAnimationActive={false} /><Bar dataKey="Failed" stackId="a" fill={statusMeta.Failed.color} isAnimationActive={false} /><Bar dataKey="Errors" stackId="a" fill={statusMeta.Error.color} isAnimationActive={false} /><Bar dataKey="Skipped" stackId="a" fill={statusMeta.Skipped.color} isAnimationActive={false} />
          </BarChart></ResponsiveContainer></div>
          <div className="chart-legend">{statusData.map((item) => <StatusTag key={item.status} status={item.status} />)}</div>
          <details className="chart-data"><summary>View category values</summary><div className="chart-data-scroll" role="region" aria-label="Category values" tabIndex={0}><table><caption className="sr-only">Exact outcomes by category</caption><thead><tr><th scope="col">Category</th>{statusData.map((item) => <th scope="col" key={item.status}>{item.name}</th>)}</tr></thead><tbody>{categoryData.map((item, index) => <tr key={index}><th scope="row">{item.name}</th><td>{item.Passed}</td><td>{item.Failed}</td><td>{item.Errors}</td><td>{item.Skipped}</td></tr>)}</tbody></table></div></details>
        </> : <EmptyChart message="No category results to chart." />}
      </Panel>
      <Panel title="Status distribution" subtitle="All four outcomes, including zero" className="analytics-status">
        {statusData.some((item) => item.value > 0) ? <div className="analytics-donut"><ResponsiveContainer width="100%" height="100%"><PieChart><Pie data={statusData.filter((item) => item.value > 0)} dataKey="value" nameKey="name" innerRadius="73%" outerRadius="92%" paddingAngle={2} stroke="none" isAnimationActive={false}>{statusData.filter((item) => item.value > 0).map((item) => <Cell key={item.name} fill={item.color} />)}</Pie><Tooltip content={<ChartTooltip />} isAnimationActive={false} /></PieChart></ResponsiveContainer><div className="donut-center"><strong>{report.summary.total}</strong><span>tests reported</span></div></div> : <EmptyChart message="No test results to chart." />}
        <div className="legend-list">{statusData.map((item) => <div className="legend-row" key={item.name}><StatusTag status={item.status} /><span className="legend-value">{item.value}<small>{report.summary.total ? (item.value / report.summary.total * 100).toFixed(1) : '0.0'}%</small></span></div>)}</div>
      </Panel>
    </div>
    <div className="analytics-bottom">
      <Panel title="Time by category" subtitle="Sum of test durations · seconds">
        {categoryData.length ? <div className="duration-list">{categoryData.map((item, index) => <div key={index}><div className="category-top"><strong>{item.name}</strong><span>{item.duration.toFixed(3)}s</span></div><div className="duration-track" aria-hidden="true"><span style={{ width: `${maxDuration ? item.duration / maxDuration * 100 : 0}%` }} /></div></div>)}<div className="duration-scale"><span>0s</span><span>{maxDuration.toFixed(3)}s</span></div></div> : <EmptyChart message="No duration data was provided." />}
      </Panel>
      <Panel title="Slowest test cases" subtitle="Up to seven tests with a recorded duration" action={<button className="text-button" onClick={() => onExplore()}>View all <Icon name="arrow" size={16} /></button>}>
        {slowTests.length ? <div className="slow-list">{slowTests.map((test, index) => <div className="slow-row" key={report.cases.indexOf(test)}><span className="slow-rank">{index + 1}</span><div className="slow-info"><button className="test-link" onClick={() => onExplore(report.cases.indexOf(test))}><TestName name={test.name} /></button><span>{test.className || 'Class not provided'}</span></div><StatusTag status={test.status} /><strong className="slow-time">{duration(test.durationSeconds)}</strong></div>)}</div> : <EmptyChart message="No test durations are available." />}
      </Panel>
    </div>
  </div>
}

function TestCases({ report, cases, query, setQuery, statusFilter, setStatusFilter, categoryFilter, setCategoryFilter, categories, sortKey, sortDirection, sortBy, expanded, setExpanded }: { report: QaReport; cases: TestCase[]; query: string; setQuery: (value: string) => void; statusFilter: string; setStatusFilter: (value: string) => void; categoryFilter: string; setCategoryFilter: (value: string) => void; categories: string[]; sortKey: SortKey; sortDirection: 1 | -1; sortBy: (key: SortKey) => void; expanded: number | null; setExpanded: (value: number | null) => void }) {
  const reset = () => { setQuery(''); setStatusFilter('all'); setCategoryFilter('all') }
  const activeFilters = Boolean(query || statusFilter !== 'all' || categoryFilter !== 'all')
  const selected = expanded === null ? undefined : report.cases[expanded]
  const selectedOutsideFilters = selected && !cases.includes(selected)
  const sortButton = (key: SortKey, label: string) => <button className="sort-button" onClick={() => sortBy(key)} aria-label={`Sort by ${label}`}>{label}<span aria-hidden="true">{sortKey === key ? sortDirection === 1 ? '↑' : '↓' : '↕'}</span></button>
  const detailsButton = (test: TestCase, index: number, view: string) => <button className="details-toggle" aria-label={`${expanded === index ? 'Hide' : 'Show'} details for ${test.name}`} aria-expanded={expanded === index} aria-controls={expanded === index ? `${view}-details-${index}` : undefined} onClick={() => setExpanded(expanded === index ? null : index)}><Icon name="chevron" size={18} /></button>
  return <div className="view-stack cases-view">
    <section className="case-toolbar" aria-label="Search, filter and sort tests">
      <label className="case-search"><Icon name="search" size={18} /><span className="sr-only">Search test cases</span><input id="case-search" type="search" placeholder="Search test, class, or category" value={query} onChange={(event) => setQuery(event.target.value)} /></label>
      <label className="select-wrap"><span className="sr-only">Filter by status</span><select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}><option value="all">All statuses</option><option value="Passed">Passed</option><option value="Failed">Failed</option><option value="Error">Errors</option><option value="Skipped">Skipped</option></select></label>
      <label className="select-wrap"><span className="sr-only">Filter by category</span><select value={categoryFilter} onChange={(event) => setCategoryFilter(event.target.value)}><option value="all">All categories</option>{Array.from(new Set(categories)).filter(Boolean).map((category) => <option key={category} value={category}>{category}</option>)}</select></label>
      <label className="select-wrap"><span className="sr-only">Sort tests by</span><select value={sortKey} onChange={(event) => sortBy(event.target.value as SortKey)}><option value="durationSeconds">Sort: duration</option><option value="name">Sort: name</option><option value="status">Sort: status</option></select></label>
      <button className="secondary-button" onClick={() => sortBy(sortKey)} aria-label={`Sort ${sortDirection === 1 ? 'descending' : 'ascending'}`}>{sortDirection === 1 ? 'Ascending' : 'Descending'}<span aria-hidden="true">{sortDirection === 1 ? '↑' : '↓'}</span></button>
      {activeFilters && <button className="clear-filters" onClick={reset}>Clear filters</button>}
      <span className="case-count" role="status"><strong>{cases.length}</strong> of {report.cases.length} tests</span>
    </section>
    {selectedOutsideFilters && <section id="selected-case" className="selected-case" tabIndex={-1} aria-label="Selected test outside current filters">
      <div className="panel-heading"><h2>Selected test</h2><button className="text-button" onClick={() => { setExpanded(null); document.getElementById('case-search')?.focus() }}>Close details <Icon name="close" size={16} /></button></div>
      <p>This test is outside your current filters. The filtered results below are unchanged.</p><CaseDetails test={selected} />
    </section>}
    <Panel title="Test executions" subtitle={`${report.cases.length.toLocaleString()} records from the current report`} className="table-panel">
      <div className="table-scroll" role="region" aria-label="Test case execution results" tabIndex={0}>
        <table><thead><tr>
          <th scope="col" aria-sort={sortKey === 'name' ? sortDirection === 1 ? 'ascending' : 'descending' : 'none'}>{sortButton('name', 'Test case')}</th>
          <th scope="col">Category</th>
          <th scope="col" aria-sort={sortKey === 'status' ? sortDirection === 1 ? 'ascending' : 'descending' : 'none'}>{sortButton('status', 'Status')}</th>
          <th scope="col" aria-sort={sortKey === 'durationSeconds' ? sortDirection === 1 ? 'ascending' : 'descending' : 'none'}>{sortButton('durationSeconds', 'Duration')}</th>
          <th scope="col"><span className="sr-only">Details</span></th>
        </tr></thead><tbody>{cases.map((test) => {
          const index = report.cases.indexOf(test)
          return <Fragment key={index}><tr id={`desktop-case-${index}`} className={expanded === index ? 'row-expanded' : ''}>
            <td><strong className="table-test-name"><TestName name={test.name} /></strong><span className="table-class">{test.className || 'Class not provided'}</span></td>
            <td><span className="category-chip">{test.category || 'Not provided'}</span></td><td><StatusTag status={test.status} /></td><td className="table-duration">{duration(test.durationSeconds)}</td><td>{detailsButton(test, index, 'desktop')}</td>
          </tr>{expanded === index && <tr className="details-row"><td colSpan={5}><CaseDetails test={test} id={`desktop-details-${index}`} /></td></tr>}</Fragment>
        })}</tbody></table>
      </div>
      <div className="mobile-cases">{cases.map((test) => {
        const index = report.cases.indexOf(test)
        return <article className="mobile-case" id={`mobile-case-${index}`} key={index}><h3><TestName name={test.name} /></h3><span className="table-class">{test.className || 'Class not provided'}</span>
          <div className="mobile-case-meta"><span className="category-chip">{test.category || 'Not provided'}</span><StatusTag status={test.status} /><span className="table-duration">{duration(test.durationSeconds)}</span>{detailsButton(test, index, 'mobile')}</div>
          {expanded === index && <CaseDetails test={test} id={`mobile-details-${index}`} />}
        </article>
      })}</div>
      {!cases.length && <div className="empty-table" role="status"><Icon name="search" size={24} /><strong>{activeFilters ? 'No matching tests' : 'No test cases in this report'}</strong><p>{activeFilters ? 'Try another name or change the selected filters.' : 'The Surefire report contains no test case records.'}</p>{activeFilters && <button className="secondary-button" onClick={reset}>Clear filters</button>}</div>}
      <div className="table-footer"><span>Source: Surefire XML</span><span>Sort: {sortKey === 'durationSeconds' ? 'Duration' : sortKey === 'name' ? 'Test case' : 'Status'} {sortDirection === 1 ? 'ascending' : 'descending'}</span></div>
    </Panel>
  </div>
}

function CaseDetails({ test, id }: { test: TestCase; id?: string }) {
  return <div className="details-content" id={id}><h3><TestName name={test.name} /></h3><p>{test.className || 'Class not provided'} · {test.category || 'Category not provided'} · {duration(test.durationSeconds)}</p><StatusTag status={test.status} />
    {test.message && <p>{test.message}</p>}
    {test.detail ? <pre tabIndex={0} aria-label="Execution detail">{test.detail}</pre> : <p>{test.message ? 'The report does not include a stack trace for this execution.' : 'The Surefire report does not include additional details for this test.'}</p>}
  </div>
}

function ChartTooltip({ active, payload, label, suffix = '' }: { active?: boolean; payload?: Array<{ name?: string; value?: number | string; color?: string; payload?: { color?: string } }>; label?: string; suffix?: string }) {
  if (!active || !payload?.length) return null
  return <div className="chart-tooltip">{label && <strong>{label}</strong>}{payload.map((item, index) => { const status = statusFromLabel(item.name); return <div key={`${item.name}-${index}`} style={status ? { color: statusMeta[status].color } : undefined}>{status ? <StatusIcon status={status} size={10} /> : <i style={{ background: item.color ?? item.payload?.color ?? 'var(--c-100)' }} />}{item.name}<b>{item.value}{suffix}</b></div> })}</div>
}

function EmptyChart({ message }: { message: string }) {
  return <div className="chart-empty"><span><Icon name="analytics" size={20} /></span><p>{message}</p></div>
}

export default App





