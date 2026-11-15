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
                  <title>QA Dashboard — Test Run Report</title>
                  <style>
                    :root {
                      color-scheme: dark;
                      --bg: #080d15; --sidebar: rgba(13, 22, 35, .78); --surface: #111a27;
                      --surface-raised: #151f2e; --line: #28384b; --text: #f0f5fb;
                      --muted: #a4b2c3; --subtle: #899bb1; --cyan: #6fd9ec; --green: #65dda5;
                      --red: #f2878d; --amber: #edc16f; --blue: #83baff;
                      --ease-out: cubic-bezier(.23, 1, .32, 1);
                    }
                    * { box-sizing: border-box; }
                    body { margin: 0; min-height: 100vh; color: var(--text);
                      background: radial-gradient(ellipse at 78% -30%, #102638 0, transparent 40%), var(--bg);
                      font: 14px/1.55 Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                      scrollbar-color: #34475d #0d1520; scrollbar-width: thin; }
                    button, input, select { font: inherit; color: inherit; }
                    ::selection { color: #f3fcff; background: #1b6174; }
                    ::-webkit-scrollbar { width: 9px; height: 9px; }
                    ::-webkit-scrollbar-track { background: #0d1520; }
                    ::-webkit-scrollbar-thumb { border: 2px solid #0d1520; border-radius: 999px; background: #34475d; }
                    ::-webkit-scrollbar-thumb:hover { background: #49647e; }
                    .app { min-height: 100vh; display: grid; grid-template-columns: 248px minmax(0, 1fr); }
                    .sidebar { position: sticky; top: 0; z-index: 2; height: 100vh; gap: 34px; padding: 25px 16px;
                      display: flex; flex-direction: column; background: var(--sidebar); border-right: 1px solid #34465a;
                      -webkit-backdrop-filter: blur(18px) saturate(1.18); backdrop-filter: blur(18px) saturate(1.18);
                      box-shadow: inset -1px 0 #d9f8ff0c, inset 0 1px #ffffff12, 10px 0 34px #02081126; }
                    .brand { display: flex; align-items: center; min-height: 44px; gap: 12px; padding: 0 8px;
                      font-size: 15px; font-weight: 700; letter-spacing: -.025em; }
                    .brand-mark { display: grid; place-items: center; flex: 0 0 36px; width: 36px; height: 36px;
                      border: 1px solid #39788b; border-radius: 11px; color: var(--cyan);
                      background: linear-gradient(145deg, #19354a, #10202e); box-shadow: inset 0 1px 0 #c8f7ff20; }
                    .brand-mark svg { width: 20px; height: 20px; display: block; }
                    .side-label { padding: 0 12px 9px; color: #8fa1b7; font-size: 10px; font-weight: 700;
                      letter-spacing: .13em; text-transform: uppercase; }
                    .nav { display: grid; gap: 6px; }
                    .nav a { min-height: 42px; display: flex; align-items: center; gap: 11px; padding: 9px 11px;
                      border: 1px solid #ffffff08; border-radius: 10px; color: #a8b7c8; text-decoration: none;
                      background: #ffffff03; -webkit-backdrop-filter: blur(10px); backdrop-filter: blur(10px);
                      transition: color .16s ease, background-color .16s ease, border-color .16s ease, transform .16s var(--ease-out); }
                    .nav-icon { width: 17px; height: 17px; flex: 0 0 17px; color: #91a4bb; }
                    .nav a:hover { color: #d9f8fb; background: #ffffff0c; }
                    .nav a.active { color: #b9f3f7; border-color: #5795a044; background: #5ec9df1a;
                      box-shadow: inset 2px 0 var(--cyan), 0 5px 18px #01080e2b; }
                    .nav a.active .nav-icon { color: var(--cyan); }
                    .sidebar-note { margin-top: auto; padding: 15px 11px 0; border-top: 1px solid #ffffff19;
                      color: #9aabbd; font-size: 11px; line-height: 1.7; }
                    main { width: 100%; min-width: 0; padding: 22px clamp(22px, 3.2vw, 52px) 38px; }
                    .topbar { position: relative; z-index: 3; display: flex; justify-content: space-between; align-items: center; gap: 16px;
                      min-height: 56px; margin-bottom: 30px; padding: 10px 14px;
                      border: 1px solid #9ebbd23b; border-radius: 12px; background: linear-gradient(115deg, #d8f5ff0d, transparent 38%), #142338a8;
                      box-shadow: inset 0 1px #ffffff1c, 0 8px 24px #02081136; }
                    .glass, .control, .glass-button { -webkit-backdrop-filter: blur(14px) saturate(1.16); backdrop-filter: blur(14px) saturate(1.16); }
                    .topbar-title { display: flex; align-items: center; gap: 10px; min-width: 0; color: #c1ccda; font-size: 12px; letter-spacing: .005em; }
                    .topbar-context { color: #f0f5fb; font-weight: 600; }
                    .breadcrumb-separator { color: #62778e; }
                    .run-mark { width: 22px; height: 22px; display: grid; place-items: center; border: 1px solid #4b879950;
                      border-radius: 7px; color: var(--cyan); background: #163044a8; }
                    .run-mark svg { width: 14px; height: 14px; }
                    .topbar-meta { display: flex; align-items: center; gap: 10px; }
                    .source-chip { display: inline-flex; align-items: center; gap: 7px; padding: 6px 9px; border: 1px solid #91a8be22;
                      border-radius: 8px; color: #afbdcd; background: #ffffff08; font-size: 10px; font-weight: 600; }
                    .source-chip svg { width: 13px; height: 13px; color: var(--cyan); }
                    .avatar { display: grid; place-items: center; width: 32px; height: 32px; border: 1px solid #80adc33f;
                      border-radius: 9px; background: #1a3044; color: #c0f2f7; font-size: 10px; font-weight: 700; letter-spacing: .04em; }
                    .heading { display: flex; justify-content: space-between; align-items: end; gap: 24px; margin-bottom: 25px; }
                    .page-intro { min-width: 0; }
                    h1 { margin: 0 0 7px; font-size: 36px; line-height: 1.12; letter-spacing: -.035em; }
                    .subtitle { max-width: 680px; margin: 0; color: var(--muted); font-size: 13px; }
                    .run-summary { min-width: 290px; display: grid; justify-items: end; gap: 7px; color: #96a8bc; font-size: 11px; }
                    .generated { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 5px; line-height: 1.5; }
                    .generated strong { color: #e1e9f2; font-size: 11px; font-weight: 600; }
                    .run-summary-meta { display: flex; align-items: center; justify-content: flex-end; gap: 14px; }
                    .run-state { display: flex; align-items: baseline; gap: 6px; color: #a9b8c9; }
                    .run-state strong { color: var(--cyan); font-size: 19px; line-height: 1; letter-spacing: -.025em; font-variant-numeric: tabular-nums; }
                    .run-duration { color: #a9b8c9; }
                    .run-duration strong { color: #e1e9f2; font-weight: 600; font-variant-numeric: tabular-nums; }
                    .run-state-badge { display: inline-flex; align-items: center; gap: 7px; width: fit-content;
                      padding: 5px 9px; border: 1px solid #3f8a684b; border-radius: 8px; color: #9ae8bd;
                      background: #10271f; font-size: 10px; font-weight: 650; }
                    .run-state-badge::before { width: 6px; height: 6px; border-radius: 50%; background: currentColor; content: ""; }
                    .run-state-badge.warning { border-color: #90713b61; color: #f2d186; background: #302717; }
                    .run-state-badge.failed { border-color: #a4495061; color: #ffafb3; background: #321b22; }
                    .metrics { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 12px; margin-bottom: 18px; }
                    .metric { min-width: 0; min-height: 112px; padding: 16px 17px; border: 1px solid #2b3a4e;
                      border-radius: 12px; background: #101925; box-shadow: 0 8px 20px #0207111c; }
                    .metric-label { color: #b5c2d1; font-size: 12px; font-weight: 600; letter-spacing: .01em; }
                    .metric-value { margin: 9px 0 3px; color: #f0f5fb; font-size: 29px; font-weight: 700;
                      line-height: 1.12; letter-spacing: -.035em; font-variant-numeric: tabular-nums; }
                    .metric-note { color: #a2b1c2; font-size: 11px; }
                    .metric.pass .metric-value { color: var(--green); }
                    .metric.fail .metric-value, .metric.error .metric-value { color: var(--red); }
                    .metric.skip .metric-value { color: var(--amber); }
                    .panels { display: grid; grid-template-columns: minmax(0, 1.08fr) minmax(0, .92fr); gap: 15px; margin: 15px 0; }
                    .panel { min-width: 0; border: 1px solid #2b3a4e; border-radius: 12px; padding: 20px 21px;
                      background: radial-gradient(ellipse at 18% 0%, #20354b22, transparent 55%), #101925; }
                    .panel h2, .results-heading h2 { margin: 0; color: #edf3fa; font-size: 15px; font-weight: 650; letter-spacing: -.02em; }
                    .panel-subtitle { margin: 5px 0 18px; color: #aab8c9; font-size: 12px; }
                    .distribution-layout { display: grid; grid-template-columns: minmax(130px, .8fr) minmax(0, 1fr); align-items: center; gap: 24px; }
                    .distribution { position: relative; width: min(100%, 190px); aspect-ratio: 1; justify-self: center;
                      border: 1px solid #ffffff14; border-radius: 50%; background: conic-gradient(var(--green) 0deg 360deg);
                      box-shadow: inset 0 0 0 1px #00000040, 0 8px 22px #02071129; }
                    .distribution::after { position: absolute; inset: 23%; border: 1px solid #ffffff12; border-radius: 50%;
                      background: #101925; box-shadow: inset 0 1px 8px #02071170; content: ""; }
                    .distribution-center { position: absolute; z-index: 1; inset: 23%; display: grid; align-content: center; justify-items: center; }
                    .distribution-total { color: #f0f5fb; font-size: clamp(22px, 2.4vw, 30px); font-weight: 700;
                      line-height: 1.05; font-variant-numeric: tabular-nums; }
                    .distribution-caption { margin-top: 5px; color: #b1bece; font-size: 11px; }
                    .legend { display: grid; gap: 8px; }
                    .legend-item { display: grid; grid-template-columns: 10px minmax(0, 1fr) auto; align-items: center; gap: 9px;
                      color: #c0ccda; font-size: 12px; }
                    .legend-value { color: #eef4fb; font-variant-numeric: tabular-nums; }
                    .dot { width: 8px; height: 8px; border-radius: 50%; box-shadow: 0 0 0 3px #ffffff08; }
                    .category-heading, .category-row { display: grid; grid-template-columns: 70px minmax(45px, 1fr) 82px 76px;
                      align-items: center; gap: 10px; }
                    .category-heading { margin-bottom: 10px; color: #9eafc2; font-size: 10px; font-weight: 650;
                      letter-spacing: .06em; text-transform: uppercase; }
                    .category-list { display: grid; gap: 14px; }
                    .category-row { min-height: 28px; }
                    .category-name { color: #e0e8f1; font-size: 12px; font-weight: 600; }
                    .category-count, .category-rate { color: #c1ccda; font-size: 11px; font-variant-numeric: tabular-nums; text-align: right; white-space: nowrap; }
                    .category-rate { color: var(--green); font-weight: 650; }
                    .category-track { height: 8px; overflow: hidden; border-radius: 999px; background: #263448; }
                    .category-track span { display: block; height: 100%; border-radius: inherit; background: linear-gradient(90deg, #3baec8, var(--cyan)); }
                    .results { margin-top: 17px; padding: 0; overflow: hidden; }
                    .results-heading { display: flex; justify-content: space-between; align-items: center; gap: 14px; min-height: 60px; padding: 17px 20px; border-bottom: 1px solid var(--line); }
                    .result-count { color: #b2c0d0; font-size: 12px; font-variant-numeric: tabular-nums; }
                    .toolbar { position: relative; z-index: 1; display: flex; flex-wrap: wrap; align-items: center; gap: 9px; padding: 12px 15px;
                      border-bottom: 1px solid #91a9c033; background: linear-gradient(110deg, #d9f6ff0b, transparent 45%), #17283bbd;
                      box-shadow: inset 0 1px #ffffff14, 0 5px 16px #02071120; }
                    .control { min-height: 38px; padding: 8px 11px; border: 1px solid #8ea8c04d; border-radius: 9px;
                      background: #09121ebd; -webkit-backdrop-filter: blur(12px) saturate(1.16); backdrop-filter: blur(12px) saturate(1.16);
                      box-shadow: inset 0 1px #ffffff0c; font-size: 12px; transition: border-color .16s ease, box-shadow .16s ease; }
                    .control:focus-visible, .glass-button:focus-visible, .nav a:focus-visible, summary:focus-visible {
                      outline: 2px solid var(--cyan); outline-offset: 2px; }
                    .control:focus-visible { border-color: var(--cyan); box-shadow: 0 0 0 3px #63d7ed1c; }
                    .search { flex: 1 1 280px; }
                    .toolbar select { min-width: 145px; }
                    .sort-button { display: inline-flex; align-items: center; gap: 7px; padding: 5px 7px; border: 1px solid transparent;
                      border-radius: 7px; color: inherit; background: #ffffff05; cursor: pointer; font: inherit; letter-spacing: inherit;
                      text-transform: inherit; -webkit-backdrop-filter: blur(10px); backdrop-filter: blur(10px); }
                    .sort-button:hover, .sort-button[aria-pressed="true"] { border-color: #7ca4c044; color: #dff8fb; background: #8ad9e711; }
                    .sort-button:focus-visible { outline: 2px solid var(--cyan); outline-offset: 2px; }
                    .sort-indicator { width: 12px; color: #95a9be; text-align: center; }
                    .glass-button { min-height: 38px; padding: 8px 13px; border: 1px solid #7089a34f; border-radius: 9px;
                      cursor: pointer;
                      background: #243951b8; font-size: 11px; font-weight: 600;
                      transition: color .16s ease, border-color .16s ease, background-color .16s ease, transform .16s var(--ease-out); }
                    .glass-button:hover { border-color: #7ec9d899; color: #d3f9fc; background: #29445dbf; }
                    .glass-button:active { transform: scale(.98); }
                    .table-wrap { overflow-x: auto; scrollbar-gutter: stable; }
                    table { width: 100%; min-width: 800px; border-collapse: collapse; }
                    th { padding: 12px 15px; color: #aab9ca; text-align: left; font-size: 10px;
                      letter-spacing: .08em; text-transform: uppercase; background: #151f2d; }
                    td { padding: 13px 15px; border-top: 1px solid #243347; color: #dce5ef; vertical-align: top; font-size: 12px; }
                    tbody tr { transition: background-color .14s ease; }
                    tbody tr:hover { background: #182638; }
                    .test-name { color: #f0f5fb; font-weight: 600; overflow-wrap: anywhere; }
                    .test-class { color: #b1bece; font-size: 12px; overflow-wrap: anywhere; }
                    .category-pill, .status-pill { display: inline-flex; align-items: center; gap: 6px; padding: 4px 9px;
                      border: 1px solid transparent; border-radius: 8px; font-size: 11px; font-weight: 600; white-space: nowrap; }
                    .category-pill { border-color: #3d708341; color: #b5eaf1; background: #183344; }
                    .status-pill::before { width: 6px; height: 6px; border-radius: 50%; background: currentColor; content: ""; }
                    .status-pill.passed { border-color: #3f8a684b; color: #9ce8be; background: #142b23; }
                    .status-pill.failed, .status-pill.error { border-color: #a449504b; color: #ffadb2; background: #321d24; }
                    .status-pill.skipped { border-color: #90713b4b; color: #f3d58f; background: #302819; }
                    .details { max-width: 380px; color: #b4c1d0; font-size: 12px; }
                    .details summary { color: #ffb3b7; cursor: pointer; font-weight: 650; overflow-wrap: anywhere; }
                    .details pre { max-width: 100%; max-height: 260px; margin: 8px 0 0; padding: 10px; overflow: auto;
                      border: 1px solid #573840; border-radius: 8px; color: #e0c3c6; background: #0b1119; font: 11px/1.55 ui-monospace, SFMono-Regular, Consolas, monospace;
                      white-space: pre-wrap; overflow-wrap: anywhere; }
                    .details-unavailable { color: #d9b978; font-size: 11px; }
                    .empty { display: grid; justify-items: center; gap: 8px; padding: 38px 20px; color: #a8b7c8;
                      font-size: 12px; text-align: center; background: #0e1722; }
                    .empty[hidden] { display: none; }
                    .empty-mark { width: 30px; height: 30px; display: grid; place-items: center; border: 1px solid #31445a;
                      border-radius: 9px; color: #9aabc0; background: #152233; }
                    .empty-mark svg { width: 16px; height: 16px; }
                    .table-wrap:focus-visible { outline: 2px solid var(--cyan); outline-offset: -2px; }
                    footer { display: flex; justify-content: space-between; gap: 14px; padding: 16px 3px 0; color: #a0afc1; font-size: 11px; }
                    .sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden;
                      clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
                    @media (hover: hover) and (pointer: fine) {
                      .glass-button:hover { transform: translateY(-1px); }
                    }
                    @supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
                      .sidebar { background: #0d1623; }
                      .topbar, .toolbar { background: #16263a; }
                      .nav a, .sort-button { background: #17283b; }
                      .glass-button { background: #293f57; }
                      .control { background: #101a28; }
                    }
                    @media (max-width: 1240px) {
                      .app { grid-template-columns: 212px minmax(0, 1fr); }
                      .heading { align-items: flex-start; flex-direction: column; gap: 13px; }
                      .run-summary { justify-items: start; }
                      .generated, .run-summary-meta { justify-content: flex-start; }
                      .metrics { grid-template-columns: repeat(3, minmax(0, 1fr)); }
                    }
                    @media (max-width: 920px) {
                      .panels { grid-template-columns: 1fr; }
                    }
                    @media (max-width: 760px) {
                      .app { display: block; }
                      .sidebar { position: relative; height: auto; padding: 12px 14px; border-right: 0; border-bottom: 1px solid var(--line); }
                      .brand { margin-bottom: 10px; }
                      .side-label, .sidebar-note { display: none; }
                      .nav { display: flex; overflow-x: auto; gap: 5px; }
                      .nav a { min-height: 38px; white-space: nowrap; padding: 7px 10px; }
                      .nav::-webkit-scrollbar { display: none; }
                      main { padding: 16px 13px 28px; }
                      .topbar { margin-bottom: 23px; }
                      .heading { align-items: flex-start; flex-direction: column; gap: 14px; }
                      .run-summary { min-width: 0; width: 100%; }
                      .run-summary-meta { flex-wrap: wrap; }
                      .metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); }
                      .metric { min-height: 100px; padding: 14px; }
                      .metric-value { font-size: 26px; }
                      .panels { grid-template-columns: 1fr; gap: 10px; }
                      .panel { padding: 16px; }
                      .distribution-layout { grid-template-columns: minmax(120px, .7fr) minmax(0, 1fr); gap: 14px; }
                      .distribution { width: min(100%, 170px); }
                      .category-heading, .category-row { grid-template-columns: 58px minmax(28px, 1fr) 48px 52px; gap: 6px; }
                      .category-heading { font-size: 9px; letter-spacing: 0; }
                      .category-name, .category-count, .category-rate { font-size: 10px; }
                      .results-heading { align-items: flex-start; flex-direction: column; padding: 16px; }
                      .toolbar { padding: 10px; }
                      .search { flex-basis: 100%; }
                      .toolbar select { flex: 1 1 115px; min-width: 0; }
                      footer { flex-direction: column; gap: 5px; }
                    }
                    @media (max-width: 390px) {
                      .topbar { gap: 9px; padding-inline: 10px; }
                      .topbar-title { font-size: 11px; }
                      .topbar-meta { gap: 6px; }
                      .source-chip { display: none; }
                      .avatar { width: 30px; height: 30px; flex: 0 0 30px; }
                      .metrics { grid-template-columns: 1fr; }
                      .distribution-layout { grid-template-columns: minmax(105px, .7fr) minmax(0, 1fr); gap: 10px; }
                      .distribution { width: min(100%, 145px); }
                      .legend-item { grid-template-columns: 8px minmax(0, 1fr) auto; gap: 6px; font-size: 11px; }
                      .category-heading, .category-row { grid-template-columns: 52px minmax(24px, 1fr) 42px 46px; gap: 5px; }
                      .category-heading { font-size: 8px; }
                      .category-name, .category-count, .category-rate { font-size: 9px; }
                    }
                    @media (prefers-reduced-motion: reduce) {
                      *, *::before, *::after { scroll-behavior: auto !important; animation-duration: .01ms !important;
                        animation-iteration-count: 1 !important; transition-duration: .01ms !important; }
                    }
                  </style>
                </head>
                <body>
                  <div class="app">
                    <aside class="sidebar">
                      <div class="brand"><span class="brand-mark" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none"><path d="M4 17h4v-5h4v2h4V8h4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/><path d="m14 15 2 2 4-5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg></span><span>QA Dashboard</span></div>
                      <div>
                        <div class="side-label">Workspace</div>
                        <nav class="nav" aria-label="Dashboard sections">
                          <a class="active" href="#overview" aria-current="location"><svg class="nav-icon" aria-hidden="true" viewBox="0 0 20 20" fill="none"><path d="m3 9 7-6 7 6v8H5V9m4 8v-5h4v5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>Overview</a>
                          <a href="#analytics"><svg class="nav-icon" aria-hidden="true" viewBox="0 0 20 20" fill="none"><path d="M3 16V9m5 7V4m5 12v-5m5 5V7" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/></svg>Analytics</a>
                          <a href="#test-cases"><svg class="nav-icon" aria-hidden="true" viewBox="0 0 20 20" fill="none"><path d="M6 4h11M6 10h11M6 16h11M2.5 4h.01M2.5 10h.01M2.5 16h.01" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/></svg>Test cases</a>
                        </nav>
                      </div>
                      <div class="sidebar-note">Automated quality report<br>Java · Selenium · JUnit 5</div>
                    </aside>
                    <main>
                      <header class="topbar glass">
                        <div class="topbar-title"><span class="run-mark" aria-hidden="true"><svg viewBox="0 0 16 16" fill="none"><path d="M2 12.5h3V8h3v2h3V4h3" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg></span><span>QA Automation <span class="breadcrumb-separator" aria-hidden="true">/</span> <span class="topbar-context">Latest execution</span></span></div>
                        <div class="topbar-meta"><span class="source-chip"><svg aria-hidden="true" viewBox="0 0 16 16" fill="none"><path d="M3 3.5h10M3 7.5h10M3 11.5h6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>Maven Surefire</span><div class="avatar" aria-label="QA Dashboard">QA</div></div>
                      </header>
                      <section id="overview" aria-labelledby="page-title">
                        <div class="heading">
                          <div class="page-intro"><h1 id="page-title">QA Dashboard</h1><p class="subtitle">A focused view of API, web and end-to-end test quality.</p></div>
                          <div class="run-summary"><div class="generated">Report generated <strong id="generated-at">—</strong></div><div class="run-summary-meta"><div class="run-state-badge" id="run-outcome">—</div><div class="run-state">Pass rate <strong id="pass-rate">—</strong></div><div class="run-duration">Run duration <strong id="run-duration">—</strong></div></div></div>
                        </div>
                        <div id="metrics" class="metrics" aria-label="Run metrics"></div>
                      </section>
                      <section id="analytics" class="panels" aria-label="Test analytics">
                        <article class="panel">
                          <h2>Overall result distribution</h2><p class="panel-subtitle">Share of all test case executions by outcome.</p>
                          <div class="distribution-layout">
                            <div id="distribution" class="distribution" role="img" aria-label="Test result distribution">
                              <div class="distribution-center"><span id="distribution-total" class="distribution-total">—</span><span class="distribution-caption">tests</span></div>
                            </div>
                            <div id="legend" class="legend" aria-label="Result counts and percentages"></div>
                          </div>
                        </article>
                        <article class="panel">
                          <h2>Results by category</h2><p class="panel-subtitle">Bar length shows test volume; pass rate is reported separately.</p>
                          <div class="category-heading" aria-hidden="true"><span>Category</span><span>Test volume</span><span>Tests</span><span>Pass rate</span></div>
                          <div id="categories" class="category-list"></div>
                        </article>
                      </section>
                      <section id="test-cases" class="panel results" aria-labelledby="results-title">
                        <div class="results-heading"><h2 id="results-title">Test case executions</h2><span id="result-count" class="result-count" aria-live="polite"></span></div>
                        <div class="toolbar glass" role="search">
                          <label class="sr-only" for="search">Search test cases</label>
                          <input class="control search" id="search" type="search" placeholder="Search by test, class or category…" autocomplete="off">
                          <label class="sr-only" for="category-filter">Filter by category</label>
                          <select class="control" id="category-filter"><option value="all">All categories</option><option>API</option><option>Web UI</option><option>E2E</option><option>Other</option></select>
                          <label class="sr-only" for="status-filter">Filter by status</label>
                          <select class="control" id="status-filter"><option value="all">All statuses</option><option>Passed</option><option>Failed</option><option>Error</option><option>Skipped</option></select>
                          <button id="reset-filters" class="glass-button" type="button">Reset filters</button>
                        </div>
                        <div class="table-wrap" tabindex="0" role="region" aria-label="Test case execution results">
                          <table>
                            <thead><tr>
                              <th scope="col" aria-sort="none"><button class="sort-button" type="button" data-sort="name" aria-pressed="false">Test<span class="sort-indicator" aria-hidden="true">↕</span></button></th>
                              <th scope="col" aria-sort="none"><button class="sort-button" type="button" data-sort="className" aria-pressed="false">Class<span class="sort-indicator" aria-hidden="true">↕</span></button></th>
                              <th scope="col" aria-sort="none"><button class="sort-button" type="button" data-sort="category" aria-pressed="false">Category<span class="sort-indicator" aria-hidden="true">↕</span></button></th>
                              <th scope="col" aria-sort="none"><button class="sort-button" type="button" data-sort="status" aria-pressed="false">Status<span class="sort-indicator" aria-hidden="true">↕</span></button></th>
                              <th scope="col" aria-sort="none"><button class="sort-button" type="button" data-sort="durationSeconds" aria-pressed="false">Duration<span class="sort-indicator" aria-hidden="true">↕</span></button></th>
                              <th scope="col">Details</th>
                            </tr></thead>
                            <tbody id="case-rows"></tbody>
                          </table>
                          <div id="empty-state" class="empty" role="status" aria-live="polite" hidden><span class="empty-mark" aria-hidden="true"><svg viewBox="0 0 20 20" fill="none"><circle cx="8.5" cy="8.5" r="4.5" stroke="currentColor" stroke-width="1.5"/><path d="m12 12 4 4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg></span><span>No test cases match the selected filters.</span></div>
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
                        {key: 'failed', label: 'Failures', color: 'var(--red)'},
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
                      document.getElementById('pass-rate').textContent = `${rate.toFixed(1)}%`;
                      document.getElementById('run-duration').textContent = duration(summary.durationSeconds);
                      const runOutcome = document.getElementById('run-outcome');
                      const hasFailures = summary.failed > 0 || summary.errors > 0;
                      runOutcome.textContent = hasFailures ? 'Needs attention'
                        : summary.skipped > 0 ? 'Completed with skipped tests' : 'All tests passed';
                      runOutcome.classList.toggle('failed', hasFailures);
                      runOutcome.classList.toggle('warning', !hasFailures && summary.skipped > 0);
                      const cards = [
                        ['Total tests', summary.total, 'All recorded executions', ''],
                        ['Passed', summary.passed, 'Assertions completed successfully', 'pass'],
                        ['Failures', summary.failed, 'Assertion failures', 'fail'],
                        ['Errors', summary.errors, 'Execution errors', 'error'],
                        ['Skipped', summary.skipped, 'Not executed by JUnit', 'skip']
                      ];
                      const metrics = document.getElementById('metrics');
                      cards.forEach(([label, value, note, style]) => {
                        const card = make('article', `metric ${style}`);
                        card.append(make('div', 'metric-label', label), make('div', 'metric-value', String(value)), make('div', 'metric-note', note));
                        metrics.append(card);
                      });

                      const distribution = document.getElementById('distribution');
                      const legend = document.getElementById('legend');
                      const distributionSegments = [];
                      let currentAngle = 0;
                      document.getElementById('distribution-total').textContent = String(summary.total);
                      const distributionValues = [];
                      statuses.forEach(item => {
                        const count = summary[item.key];
                        const percentage = pct(count, summary.total);
                        const degrees = percentage * 3.6;
                        if (count > 0) {
                          distributionSegments.push(`${item.color} ${currentAngle}deg ${currentAngle + degrees}deg`);
                          currentAngle += degrees;
                        }
                        distributionValues.push(`${item.label}: ${count} (${percentage.toFixed(1)}%)`);
                        const legendItem = make('span', 'legend-item');
                        const dot = make('span', 'dot');
                        dot.style.background = item.color;
                        legendItem.append(dot, make('span', '', item.label), make('span', 'legend-value', `${count} · ${percentage.toFixed(1)}%`));
                        legend.append(legendItem);
                      });
                      distribution.style.background = distributionSegments.length
                        ? `conic-gradient(${distributionSegments.join(', ')})` : '#263448';
                      distribution.setAttribute('aria-label', `Test result distribution. ${distributionValues.join('. ')}`);

                      const categoryStats = categories.map(category => ({category, ...countByStatus(report.cases.filter(test => test.category === category))}));
                      const maxCategory = Math.max(1, ...categoryStats.map(item => item.total));
                      const categoryList = document.getElementById('categories');
                      categoryStats.forEach(item => {
                        const row = make('div', 'category-row');
                        const name = make('div', 'category-name', item.category);
                        const track = make('div', 'category-track');
                        const bar = make('span');
                        bar.style.width = `${item.total * 100 / maxCategory}%`;
                        track.setAttribute('role', 'img');
                        track.setAttribute('aria-label', `${item.total} tests; volume scale maximum ${maxCategory}`);
                        track.title = `${item.total} tests (volume)`;
                        track.append(bar);
                        const count = make('div', 'category-count', String(item.total));
                        const passRate = make('div', 'category-rate', `${pct(item.passed, item.total).toFixed(1)}%`);
                        row.append(name, track, count, passRate);
                        categoryList.append(row);
                      });

                      const search = document.getElementById('search');
                      const categoryFilter = document.getElementById('category-filter');
                      const statusFilter = document.getElementById('status-filter');
                      const body = document.getElementById('case-rows');
                      const empty = document.getElementById('empty-state');
                      const resultCount = document.getElementById('result-count');
                      const sortButtons = [...document.querySelectorAll('.sort-button')];
                      const statusOrder = {Passed: 0, Failed: 1, Error: 2, Skipped: 3};
                      let sortState = null;
                      const statusClass = status => status.toLowerCase();
                      const updateSortControls = () => sortButtons.forEach(button => {
                        const header = button.closest('th');
                        const active = sortState && sortState.key === button.dataset.sort;
                        const indicator = button.querySelector('.sort-indicator');
                        header.setAttribute('aria-sort', active ? sortState.direction === 1 ? 'ascending' : 'descending' : 'none');
                        button.setAttribute('aria-pressed', String(Boolean(active)));
                        indicator.textContent = active ? sortState.direction === 1 ? '↑' : '↓' : '↕';
                        button.setAttribute('aria-label', `Sort by ${button.firstChild.textContent.trim()}${active ? sortState.direction === 1 ? ', ascending' : ', descending' : ''}`);
                      });
                      sortButtons.forEach(button => button.addEventListener('click', () => {
                        const key = button.dataset.sort;
                        if (!sortState || sortState.key !== key) {
                          sortState = {key, direction: 1};
                        } else if (sortState.direction === 1) {
                          sortState.direction = -1;
                        } else {
                          sortState = null;
                        }
                        updateSortControls();
                        renderRows();
                      }));
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
                        if (sortState) {
                          filtered.sort((left, right) => {
                            let comparison;
                            if (sortState.key === 'durationSeconds') {
                              comparison = left.durationSeconds - right.durationSeconds;
                            } else if (sortState.key === 'status') {
                              comparison = statusOrder[left.status] - statusOrder[right.status];
                            } else {
                              comparison = String(left[sortState.key]).localeCompare(String(right[sortState.key]), undefined,
                                {numeric: true, sensitivity: 'base'});
                            }
                            return comparison * sortState.direction;
                          });
                        }
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
                          const hasFailure = test.status === 'Failed' || test.status === 'Error';
                          if (test.message || test.detail || hasFailure) {
                            const details = make('details', 'details');
                            details.append(make('summary', '', test.message || (hasFailure ? 'Open failure details' : 'Open execution details')));
                            if (test.detail) {
                              details.append(make('pre', '', test.detail));
                            } else if (hasFailure) {
                              details.append(make('p', 'details-unavailable', test.message
                                ? 'Stack trace is not available in the Surefire XML.'
                                : 'Failure message and stack trace are not available in the Surefire XML.'));
                            }
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
                      updateSortControls();
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
