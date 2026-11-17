import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { defineConfig, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

const reportPath = resolve(import.meta.dirname, '../target/site/qa-dashboard.json')

function sureFireReport(): Plugin {
  return {
    name: 'surefire-report-json',
    configureServer(server) {
      server.middlewares.use('/qa-dashboard.json', async (_request, response) => {
        try {
          const report = await readFile(reportPath, 'utf8')
          try { JSON.parse(report) } catch {
            response.statusCode = 422
            response.setHeader('Content-Type', 'application/json; charset=utf-8')
            response.end(JSON.stringify({ message: 'The QA report is not valid JSON.' }))
            return
          }
          response.setHeader('Content-Type', 'application/json; charset=utf-8')
          response.end(report)
        } catch {
          response.statusCode = 404
          response.end(JSON.stringify({ message: 'QA report not found. Generate it first with the Maven dashboard task.' }))
        }
      })
    },
    async generateBundle() {
      try {
        const report = await readFile(reportPath, 'utf8')
        JSON.parse(report)
        this.emitFile({ type: 'asset', fileName: 'qa-dashboard.json', source: report })
      } catch {
        // Keep a build without report data valid; the interface explains how to generate it.
      }
    },
  }
}

export default defineConfig({
  base: './',
  plugins: [react(), tailwindcss(), sureFireReport()],
})
