import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** Builds a self-contained dashboard from Maven Surefire XML reports. */
public final class GenerateDashboard {
    private GenerateDashboard() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: GenerateDashboard <surefire-reports-directory> <output-html-file>");
        }

        Path reportsDirectory = Path.of(args[0]);
        Path outputFile = Path.of(args[1]);
        List<TestCase> testCases = readTestCases(reportsDirectory);
        if (testCases.isEmpty()) {
            throw new IllegalStateException("No test cases were found in " + reportsDirectory.toAbsolutePath());
        }

        Files.createDirectories(outputFile.toAbsolutePath().getParent());
        Files.writeString(outputFile, render(testCases), StandardCharsets.UTF_8);
        System.out.printf(Locale.ROOT, "Generated QA dashboard: %s (%d test cases)%n",
                outputFile.toAbsolutePath(), testCases.size());
    }

    private static List<TestCase> readTestCases(Path reportsDirectory) throws Exception {
        if (!Files.isDirectory(reportsDirectory)) {
            throw new IllegalArgumentException("Surefire report directory does not exist: "
                    + reportsDirectory.toAbsolutePath());
        }

        List<Path> xmlFiles;
        try (var files = Files.list(reportsDirectory)) {
            xmlFiles = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("TEST-")
                            && path.getFileName().toString().endsWith(".xml"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
        if (xmlFiles.isEmpty()) {
            throw new IllegalStateException("No TEST-*.xml Surefire reports found in "
                    + reportsDirectory.toAbsolutePath());
        }

        List<TestCase> testCases = new ArrayList<>();
        for (Path xmlFile : xmlFiles) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            Document document = factory.newDocumentBuilder().parse(xmlFile.toFile());
            Element suite = document.getDocumentElement();
            NodeList cases = suite.getElementsByTagName("testcase");
            for (int index = 0; index < cases.getLength(); index++) {
                Element testCase = (Element) cases.item(index);
                String className = attributeOr(testCase, "classname", suite.getAttribute("name"));
                Element failure = firstChild(testCase, "failure");
                Element error = firstChild(testCase, "error");
                Element skipped = firstChild(testCase, "skipped");
                String status = error != null ? "Error"
                        : failure != null ? "Failed"
                        : skipped != null ? "Skipped" : "Passed";
                Element detailElement = error != null ? error : failure;
                testCases.add(new TestCase(
                        testCase.getAttribute("name"),
                        className,
                        categoryFor(className),
                        status,
                        parseDuration(testCase.getAttribute("time")),
                        detailElement == null ? "" : attributeOr(detailElement, "message", ""),
                        detailElement == null ? "" : detailElement.getTextContent().strip()));
            }
        }
        return List.copyOf(testCases);
    }

    private static Element firstChild(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
    }

    private static String attributeOr(Element element, String attribute, String fallback) {
        String value = element.getAttribute(attribute);
        return value.isBlank() ? fallback : value;
    }

    private static double parseDuration(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid test duration in Surefire XML: " + value, exception);
        }
    }

    private static String categoryFor(String className) {
        int packageSeparator = className.lastIndexOf('.');
        String packageName = packageSeparator < 0 ? "" : className.substring(0, packageSeparator);
        if (packageName.equals("com.qa.automation.tests.api")) {
            return "API";
        }
        if (packageName.equals("com.qa.automation.tests.web")) {
            return "E2E";
        }
        if (packageName.equals("com.qa.automation.tests")) {
            return "Web UI";
        }
        return "Other";
    }

    private static String render(List<TestCase> testCases) {
        long passed = testCases.stream().filter(test -> test.status().equals("Passed")).count();
        long failed = testCases.stream().filter(test -> test.status().equals("Failed")).count();
        long errors = testCases.stream().filter(test -> test.status().equals("Error")).count();
        long skipped = testCases.stream().filter(test -> test.status().equals("Skipped")).count();
        double duration = testCases.stream().mapToDouble(TestCase::durationSeconds).sum();
        String generatedAt = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        StringBuilder json = new StringBuilder("{\"generatedAt\":").append(jsonString(generatedAt))
                .append(",\"summary\":{\"total\":").append(testCases.size())
                .append(",\"passed\":").append(passed)
                .append(",\"failed\":").append(failed)
                .append(",\"errors\":").append(errors)
                .append(",\"skipped\":").append(skipped)
                .append(",\"durationSeconds\":").append(decimal(duration)).append("},\"cases\":[");
        for (int index = 0; index < testCases.size(); index++) {
            TestCase test = testCases.get(index);
            if (index > 0) {
                json.append(',');
            }
            json.append("{\"name\":").append(jsonString(test.name()))
                    .append(",\"className\":").append(jsonString(test.className()))
                    .append(",\"category\":").append(jsonString(test.category()))
                    .append(",\"status\":").append(jsonString(test.status()))
                    .append(",\"durationSeconds\":").append(decimal(test.durationSeconds()))
                    .append(",\"message\":").append(jsonString(test.message()))
                    .append(",\"detail\":").append(jsonString(test.detail())).append('}');
        }
        json.append("]}");
        return html().replace("__REPORT_JSON__", json.toString());
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    /** Escapes dynamic values for JSON embedded in a script element. */
    private static String jsonString(String value) {
        StringBuilder escaped = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                case '<' -> escaped.append("\\u003c");
                case '>' -> escaped.append("\\u003e");
                case '&' -> escaped.append("\\u0026");
                case '\u2028' -> escaped.append("\\u2028");
                case '\u2029' -> escaped.append("\\u2029");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.append('"').toString();
    }

    private static String html() {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <meta name="color-scheme" content="dark">
                  <title>QA Signal — Test Run Dashboard</title>
                  <style>
                    :root {
                      color-scheme: dark;
                      --bg: #080e18; --sidebar: #0b1422; --surface: #111d2e;
                      --surface-2: #15243a; --line: #263a54; --text: #f3f7fb;
                      --muted: #9aabc0; --cyan: #69d7f2; --green: #50d69a;
                      --red: #f4777d; --amber: #f5c66a; --blue: #74b9ff;
                    }
                    * { box-sizing: border-box; }
                    body { margin: 0; min-height: 100vh; color: var(--text); background: var(--bg);
                      font: 15px/1.5 Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
                    button, input, select { font: inherit; color: inherit; }
                    .app { min-height: 100vh; display: grid; grid-template-columns: 248px minmax(0, 1fr); }
                    .sidebar { background: var(--sidebar); border-right: 1px solid var(--line); padding: 27px 18px;
                      display: flex; flex-direction: column; gap: 42px; }
                    .brand { display: flex; align-items: center; gap: 12px; padding: 0 8px; font-size: 18px; font-weight: 750; letter-spacing: -.03em; }
                    .brand-mark { display: grid; place-items: center; width: 36px; height: 36px; border: 1px solid #2b7892;
                      border-radius: 11px; color: var(--cyan); background: #10283a; font-weight: 800; }
                    .side-label { color: #6f849d; font-size: 11px; font-weight: 750; letter-spacing: .16em; text-transform: uppercase; padding: 0 12px 10px; }
                    .nav { display: grid; gap: 7px; }
                    .nav a { color: var(--muted); text-decoration: none; padding: 11px 13px; border-radius: 11px; border-left: 3px solid transparent; }
                    .nav a:hover, .nav a.active { color: var(--cyan); background: #152d43; border-left-color: var(--cyan); }
                    .sidebar-note { margin-top: auto; color: #73869c; font-size: 12px; padding: 12px; border-top: 1px solid var(--line); }
                    main { min-width: 0; padding: 26px clamp(20px, 4vw, 58px) 48px; }
                    .topbar { display: flex; justify-content: space-between; align-items: center; gap: 16px; margin-bottom: 46px;
                      padding: 12px 16px; border: 1px solid #31465f; border-radius: 14px; background: rgba(27, 44, 65, .62); }
                    .glass { -webkit-backdrop-filter: blur(14px); backdrop-filter: blur(14px); }
                    .topbar-title { font-size: 13px; color: var(--muted); }
                    .run-mark { color: var(--cyan); font-weight: 700; }
                    .avatar { width: 34px; height: 34px; border-radius: 50%; display: grid; place-items: center; background: #17283c; color: var(--cyan); font-size: 12px; font-weight: 700; }
                    .heading { display: flex; justify-content: space-between; align-items: end; gap: 20px; margin-bottom: 25px; }
                    h1 { font-size: clamp(30px, 4vw, 42px); line-height: 1.1; letter-spacing: -.045em; margin: 0 0 8px; }
                    .subtitle { color: var(--muted); margin: 0; }
                    .generated { color: var(--muted); font-size: 12px; text-align: right; }
                    .metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px; margin-bottom: 18px; }
                    .metric { min-height: 145px; padding: 20px 21px; border: 1px solid var(--line); border-radius: 15px; background: var(--surface); }
                    .metric-label { color: var(--muted); font-size: 13px; font-weight: 550; }
                    .metric-value { margin: 9px 0 4px; font-size: 34px; line-height: 1.1; font-weight: 720; letter-spacing: -.04em; }
                    .metric-note { color: #7f93aa; font-size: 12px; }
                    .metric.pass .metric-value { color: var(--green); }
                    .metric.fail .metric-value, .metric.error .metric-value { color: var(--red); }
                    .metric.skip .metric-value { color: var(--amber); }
                    .metric.rate .metric-value, .metric.duration .metric-value { color: var(--cyan); }
                    .panels { display: grid; grid-template-columns: minmax(0, 1.15fr) minmax(0, .85fr); gap: 18px; margin: 18px 0; }
                    .panel { min-width: 0; border: 1px solid var(--line); border-radius: 15px; padding: 22px; background: var(--surface); }
                    .panel h2, .results-heading h2 { margin: 0; font-size: 17px; letter-spacing: -.02em; }
                    .panel-subtitle { margin: 4px 0 20px; color: var(--muted); font-size: 12px; }
                    .distribution { display: flex; width: 100%; height: 15px; overflow: hidden; border-radius: 999px; background: #263448; }
                    .distribution span { min-width: 0; transition: width .2s ease; }
                    .distribution .passed { background: var(--green); }
                    .distribution .failed, .distribution .error { background: var(--red); }
                    .distribution .skipped { background: var(--amber); }
                    .legend { display: flex; flex-wrap: wrap; gap: 13px 18px; margin-top: 19px; }
                    .legend-item { color: var(--muted); font-size: 12px; display: flex; align-items: center; gap: 7px; }
                    .dot { width: 8px; height: 8px; border-radius: 50%; }
                    .category-list { display: grid; gap: 17px; }
                    .category-row { display: grid; grid-template-columns: 74px 1fr auto; align-items: center; gap: 13px; }
                    .category-name { font-size: 13px; font-weight: 650; }
                    .category-meta { color: var(--muted); font-size: 12px; text-align: right; }
                    .category-track { height: 9px; border-radius: 999px; background: #263448; overflow: hidden; }
                    .category-track span { display: block; height: 100%; border-radius: inherit; background: var(--cyan); }
                    .results { margin-top: 22px; padding: 0; overflow: hidden; }
                    .results-heading { display: flex; justify-content: space-between; align-items: center; gap: 14px; padding: 20px 22px; border-bottom: 1px solid var(--line); }
                    .result-count { color: var(--muted); font-size: 12px; }
                    .toolbar { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; padding: 14px 20px; border-bottom: 1px solid var(--line); background: rgba(20, 37, 57, .52); }
                    .control { min-height: 38px; padding: 8px 11px; border: 1px solid #344c68; border-radius: 9px; background: rgba(8, 17, 29, .58); outline: none; }
                    .control:focus { border-color: var(--cyan); box-shadow: 0 0 0 3px #69d7f21c; }
                    .search { flex: 1 1 220px; }
                    .toolbar select { min-width: 135px; }
                    .glass-button { border: 1px solid #38516c; border-radius: 9px; padding: 8px 12px; background: rgba(35, 57, 80, .7); cursor: pointer; }
                    .control, .glass-button { -webkit-backdrop-filter: blur(8px); backdrop-filter: blur(8px); }
                    .glass-button:hover { border-color: var(--cyan); color: var(--cyan); }
                    .table-wrap { overflow-x: auto; }
                    table { width: 100%; border-collapse: collapse; min-width: 760px; }
                    th { padding: 13px 16px; color: #8fa2b8; text-align: left; font-size: 10px; letter-spacing: .11em; text-transform: uppercase; background: #101a29; }
                    td { padding: 14px 16px; border-top: 1px solid #203149; vertical-align: top; font-size: 13px; }
                    tbody tr:hover { background: #132136; }
                    .test-name { font-weight: 620; overflow-wrap: anywhere; }
                    .test-class { color: var(--muted); font-size: 11px; overflow-wrap: anywhere; }
                    .sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden;
                      clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
                    .category-pill, .status-pill { display: inline-flex; align-items: center; gap: 6px; border-radius: 999px; padding: 4px 9px; font-size: 11px; white-space: nowrap; }
                    .category-pill { color: #9adcf0; background: #183244; border: 1px solid #28516a; }
                    .status-pill.passed { color: var(--green); background: #113329; }
                    .status-pill.failed, .status-pill.error { color: var(--red); background: #3a202b; }
                    .status-pill.skipped { color: var(--amber); background: #382f1c; }
                    .details { max-width: 310px; color: var(--muted); font-size: 11px; }
                    .details summary { color: #f2a1a4; cursor: pointer; }
                    .details pre { max-height: 220px; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; color: #e0b8bd; }
                    .empty { padding: 34px; color: var(--muted); text-align: center; }
                    footer { display: flex; justify-content: space-between; gap: 14px; padding: 20px 3px 0; color: #6f849c; font-size: 11px; }
                    @supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
                      .topbar { background: #17263a; }
                      .toolbar { background: #142338; }
                      .glass-button, .control { background: #142338; }
                    }
                    @media (max-width: 1100px) { .app { grid-template-columns: 205px minmax(0, 1fr); } .metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
                    @media (max-width: 760px) {
                      .app { display: block; } .sidebar { display: block; padding: 14px 16px; border-right: 0; border-bottom: 1px solid var(--line); }
                      .brand { margin-bottom: 12px; } .side-label, .sidebar-note { display: none; } .nav { display: flex; overflow-x: auto; gap: 6px; }
                      .nav a { white-space: nowrap; padding: 8px 11px; } main { padding: 18px 14px 32px; }
                      .topbar { margin-bottom: 28px; } .heading { align-items: start; flex-direction: column; } .generated { text-align: left; }
                      .metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; } .metric { min-height: 122px; padding: 16px; }
                      .metric-value { font-size: 29px; } .panels { grid-template-columns: 1fr; gap: 12px; }
                      .panel { padding: 18px; } .results-heading { align-items: start; flex-direction: column; padding: 18px; }
                      .toolbar { padding: 12px; } .search { flex-basis: 100%; } .toolbar select { flex: 1 1 120px; }
                      footer { flex-direction: column; }
                    }
                    @media (max-width: 390px) { .metrics { grid-template-columns: 1fr; } .category-row { grid-template-columns: 58px 1fr auto; gap: 8px; } }
                  </style>
                </head>
                <body>
                  <div class="app">
                    <aside class="sidebar">
                      <div class="brand"><span class="brand-mark">Q</span><span>QA Signal</span></div>
                      <div>
                        <div class="side-label">Workspace</div>
                        <nav class="nav" aria-label="Dashboard sections">
                          <a class="active" href="#overview">Overview</a>
                          <a href="#analytics">Analytics</a>
                          <a href="#test-cases">Test cases</a>
                        </nav>
                      </div>
                      <div class="sidebar-note">Automated quality report<br>Java · Selenium · JUnit 5</div>
                    </aside>
                    <main>
                      <header class="topbar glass">
                        <div class="topbar-title"><span class="run-mark">●</span> &nbsp;QA Automation / Test report</div>
                        <div class="avatar" aria-label="QA">QA</div>
                      </header>
                      <section id="overview" aria-labelledby="page-title">
                        <div class="heading">
                          <div><h1 id="page-title">Test run overview</h1><p class="subtitle">A clear view of quality signals across API, web and end-to-end coverage.</p></div>
                          <div class="generated">Report generated<br><strong id="generated-at">—</strong></div>
                        </div>
                        <div id="metrics" class="metrics" aria-label="Run metrics"></div>
                      </section>
                      <section id="analytics" class="panels" aria-label="Test analytics">
                        <article class="panel">
                          <h2>Overall result distribution</h2><p class="panel-subtitle">Share of all test case executions by outcome.</p>
                          <div id="distribution" class="distribution" role="img" aria-label="Test result distribution"></div>
                          <div id="legend" class="legend"></div>
                        </article>
                        <article class="panel">
                          <h2>Results by category</h2><p class="panel-subtitle">Execution volume and pass rate from Surefire test cases.</p>
                          <div id="categories" class="category-list"></div>
                        </article>
                      </section>
                      <section id="test-cases" class="panel results" aria-labelledby="results-title">
                        <div class="results-heading"><h2 id="results-title">Test case executions</h2><span id="result-count" class="result-count"></span></div>
                        <div class="toolbar glass" role="search">
                          <label class="sr-only" for="search">Search test cases</label>
                          <input class="control search" id="search" type="search" placeholder="Search by test, class or category…" autocomplete="off">
                          <label class="sr-only" for="category-filter">Filter by category</label>
                          <select class="control" id="category-filter"><option value="all">All categories</option><option>API</option><option>Web UI</option><option>E2E</option><option>Other</option></select>
                          <label class="sr-only" for="status-filter">Filter by status</label>
                          <select class="control" id="status-filter"><option value="all">All statuses</option><option>Passed</option><option>Failed</option><option>Error</option><option>Skipped</option></select>
                          <button id="reset-filters" class="glass-button" type="button">Reset filters</button>
                        </div>
                        <div class="table-wrap">
                          <table>
                            <thead><tr><th scope="col">Test</th><th scope="col">Class</th><th scope="col">Category</th><th scope="col">Status</th><th scope="col">Duration</th><th scope="col">Details</th></tr></thead>
                            <tbody id="case-rows"></tbody>
                          </table>
                          <div id="empty-state" class="empty" hidden>No test cases match the selected filters.</div>
                        </div>
                      </section>
                      <footer><span>Source: Maven Surefire XML reports</span><span>Generated locally · No external services</span></footer>
                    </main>
                  </div>
                  <script id="report-data" type="application/json">__REPORT_JSON__</script>
                  <script>
                    (() => {
                      const report = JSON.parse(document.getElementById('report-data').textContent);
                      const categories = ['API', 'Web UI', 'E2E'];
                      const statuses = [
                        {key: 'passed', label: 'Passed', color: 'var(--green)'},
                        {key: 'failed', label: 'Failed', color: 'var(--red)'},
                        {key: 'errors', label: 'Errors', color: 'var(--red)'},
                        {key: 'skipped', label: 'Skipped', color: 'var(--amber)'}
                      ];
                      const summary = report.summary;
                      const pct = (number, total) => total ? (number * 100 / total) : 0;
                      const duration = seconds => {
                        if (seconds < 60) return `${seconds.toFixed(1)}s`;
                        const minutes = Math.floor(seconds / 60);
                        const rest = Math.round(seconds % 60);
                        return rest ? `${minutes}m ${rest}s` : `${minutes}m`;
                      };
                      const make = (tag, className, text) => {
                        const element = document.createElement(tag);
                        if (className) element.className = className;
                        if (text !== undefined) element.textContent = text;
                        return element;
                      };
                      const countByStatus = cases => ({
                        total: cases.length,
                        passed: cases.filter(test => test.status === 'Passed').length,
                        failed: cases.filter(test => test.status === 'Failed').length,
                        errors: cases.filter(test => test.status === 'Error').length,
                        skipped: cases.filter(test => test.status === 'Skipped').length
                      });
                      document.getElementById('generated-at').textContent = new Intl.DateTimeFormat(undefined, {
                        dateStyle: 'medium', timeStyle: 'short'
                      }).format(new Date(report.generatedAt));

                      const rate = pct(summary.passed, summary.total);
                      const cards = [
                        ['Total tests', summary.total, 'All recorded executions', ''],
                        ['Passed', summary.passed, 'Assertions completed successfully', 'pass'],
                        ['Failures', summary.failed, 'Assertion failures', 'fail'],
                        ['Errors', summary.errors, 'Execution errors', 'error'],
                        ['Skipped', summary.skipped, 'Not executed by JUnit', 'skip'],
                        ['Pass rate', `${rate.toFixed(1)}%`, 'Passed / total executions', 'rate'],
                        ['Total duration', duration(summary.durationSeconds), 'Sum of individual case durations', 'duration']
                      ];
                      const metrics = document.getElementById('metrics');
                      cards.forEach(([label, value, note, style]) => {
                        const card = make('article', `metric ${style}`);
                        card.append(make('div', 'metric-label', label), make('div', 'metric-value', String(value)), make('div', 'metric-note', note));
                        metrics.append(card);
                      });

                      const distribution = document.getElementById('distribution');
                      const legend = document.getElementById('legend');
                      statuses.forEach(item => {
                        const count = summary[item.key];
                        const segment = make('span', item.key);
                        segment.style.width = `${pct(count, summary.total)}%`;
                        segment.title = `${item.label}: ${count}`;
                        distribution.append(segment);
                        const legendItem = make('span', 'legend-item');
                        const dot = make('span', 'dot');
                        dot.style.background = item.color;
                        legendItem.append(dot, document.createTextNode(`${item.label} · ${count}`));
                        legend.append(legendItem);
                      });

                      const categoryStats = categories.map(category => ({category, ...countByStatus(report.cases.filter(test => test.category === category))}));
                      const maxCategory = Math.max(1, ...categoryStats.map(item => item.total));
                      const categoryList = document.getElementById('categories');
                      categoryStats.forEach(item => {
                        const row = make('div', 'category-row');
                        const name = make('div', 'category-name', item.category);
                        const track = make('div', 'category-track');
                        const bar = make('span');
                        bar.style.width = `${item.total * 100 / maxCategory}%`;
                        track.append(bar);
                        const meta = make('div', 'category-meta', `${item.total} tests · ${pct(item.passed, item.total).toFixed(1)}% pass`);
                        row.append(name, track, meta);
                        categoryList.append(row);
                      });

                      const search = document.getElementById('search');
                      const categoryFilter = document.getElementById('category-filter');
                      const statusFilter = document.getElementById('status-filter');
                      const body = document.getElementById('case-rows');
                      const empty = document.getElementById('empty-state');
                      const resultCount = document.getElementById('result-count');
                      const statusClass = status => status.toLowerCase();
                      const renderRows = () => {
                        const query = search.value.trim().toLocaleLowerCase();
                        const selectedCategory = categoryFilter.value;
                        const selectedStatus = statusFilter.value;
                        const filtered = report.cases.filter(test => {
                          const content = `${test.name} ${test.className} ${test.category}`.toLocaleLowerCase();
                          return (!query || content.includes(query))
                            && (selectedCategory === 'all' || test.category === selectedCategory)
                            && (selectedStatus === 'all' || test.status === selectedStatus);
                        });
                        body.replaceChildren();
                        filtered.forEach(test => {
                          const row = document.createElement('tr');
                          const testCell = document.createElement('td');
                          testCell.append(make('div', 'test-name', test.name));
                          const classCell = make('td', 'test-class', test.className);
                          const categoryCell = document.createElement('td');
                          categoryCell.append(make('span', 'category-pill', test.category));
                          const statusCell = document.createElement('td');
                          const badge = make('span', `status-pill ${statusClass(test.status)}`, test.status);
                          statusCell.append(badge);
                          const durationCell = make('td', '', duration(test.durationSeconds));
                          const detailCell = document.createElement('td');
                          if (test.message || test.detail) {
                            const details = make('details', 'details');
                            details.append(make('summary', '', test.message || 'View failure details'));
                            if (test.detail) details.append(make('pre', '', test.detail));
                            detailCell.append(details);
                          } else {
                            detailCell.textContent = '—';
                          }
                          row.append(testCell, classCell, categoryCell, statusCell, durationCell, detailCell);
                          body.append(row);
                        });
                        resultCount.textContent = `Showing ${filtered.length} of ${report.cases.length} executions`;
                        empty.hidden = filtered.length !== 0;
                      };
                      search.addEventListener('input', renderRows);
                      [categoryFilter, statusFilter].forEach(control => control.addEventListener('change', renderRows));
                      document.getElementById('reset-filters').addEventListener('click', () => {
                        search.value = '';
                        categoryFilter.value = 'all';
                        statusFilter.value = 'all';
                        renderRows();
                        search.focus();
                      });
                      renderRows();
                    })();
                  </script>
                </body>
                </html>
                """;
    }

    private record TestCase(String name, String className, String category, String status,
                            double durationSeconds, String message, String detail) { }
}
