export type TestStatus = 'Passed' | 'Failed' | 'Error' | 'Skipped'

export interface TestCase {
  name: string
  className: string
  category: string
  status: TestStatus
  durationSeconds: number | null
  message?: string
  detail?: string
}

export interface ReportSummary {
  total: number
  passed: number
  failed: number
  errors: number
  skipped: number
  passRate: number
  durationSeconds: number
}

export interface CategorySummary {
  name: string
  total: number
  passed: number
  failed: number
  errors: number
  skipped: number
  passRate: number
  durationSeconds: number
}

export interface QaReport {
  schemaVersion?: number
  generatedAt?: string
  summary: ReportSummary
  categories: CategorySummary[]
  cases: TestCase[]
}

export const statusMeta: Record<TestStatus, { label: string; color: string }> = {
  Passed: { label: 'Passed', color: 'var(--status-passed)' },
  Failed: { label: 'Failed', color: 'var(--status-failed)' },
  Error: { label: 'Errors', color: 'var(--status-error)' },
  Skipped: { label: 'Skipped', color: 'var(--status-skipped)' },
}

export function parseReport(value: unknown): QaReport {
  if (!value || typeof value !== 'object') throw new Error('Report JSON must contain an object.')
  const input = value as Partial<QaReport>
  if (!input.summary || !Array.isArray(input.cases)) throw new Error('Report is missing summary or cases data.')
  if (input.schemaVersion !== undefined && input.schemaVersion !== 1) throw new Error('This dashboard version does not support the report schema.')
  const sourceSummary = input.summary
  const requiredSummary = ['total', 'passed', 'failed', 'errors', 'skipped', 'passRate', 'durationSeconds'] as const
  if (requiredSummary.some((key) => typeof sourceSummary[key] !== 'number' || !Number.isFinite(sourceSummary[key]) || sourceSummary[key] < 0)) {
    throw new Error('Summary is missing valid numeric values.')
  }
  const cases = input.cases.map((test, index) => {
    if (!test || typeof test !== 'object') throw new Error(`Test case ${index + 1} is invalid.`)
    const candidate = test as Partial<TestCase>
    if (typeof candidate.name !== 'string' || !candidate.name.trim()) throw new Error(`Test case ${index + 1} is missing its name.`)
    if (!['Passed', 'Failed', 'Error', 'Skipped'].includes(candidate.status ?? '')) {
      throw new Error(`Test case ${index + 1} has an unsupported status.`)
    }
    return {
      name: candidate.name,
      className: typeof candidate.className === 'string' ? candidate.className : '',
      category: typeof candidate.category === 'string' ? candidate.category : '',
      status: candidate.status as TestStatus,
      durationSeconds: finiteOrNull(candidate.durationSeconds),
      message: typeof candidate.message === 'string' ? candidate.message : '',
      detail: typeof candidate.detail === 'string' ? candidate.detail : '',
    }
  })
  const categories = Array.isArray(input.categories) ? input.categories : []
  return {
    schemaVersion: input.schemaVersion,
    generatedAt: typeof input.generatedAt === 'string' ? input.generatedAt : undefined,
    summary: {
      total: finite(sourceSummary.total),
      passed: finite(sourceSummary.passed),
      failed: finite(sourceSummary.failed),
      errors: finite(sourceSummary.errors),
      skipped: finite(sourceSummary.skipped),
      passRate: finite(sourceSummary.passRate),
      durationSeconds: finite(sourceSummary.durationSeconds),
    },
    categories: categories.map((category) => ({
      name: typeof category.name === 'string' ? category.name : '',
      total: finite(category.total),
      passed: finite(category.passed),
      failed: finite(category.failed),
      errors: finite(category.errors),
      skipped: finite(category.skipped),
      passRate: finite(category.passRate),
      durationSeconds: finite(category.durationSeconds),
    })),
    cases,
  }
}

function finite(value: unknown): number {
  return typeof value === 'number' && Number.isFinite(value) && value >= 0 ? value : 0
}

function finiteOrNull(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) && value >= 0 ? value : null
}
