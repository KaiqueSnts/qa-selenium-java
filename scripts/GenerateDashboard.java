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
                      --bg: #090e16; --sidebar: #0d1622; --surface: #111b29;
                      --surface-2: #162437; --line: #29394d; --text: #eef4fb;
                      --muted: #a0afc1; --subtle: #8294aa; --cyan: #67d9ee; --green: #65dda5;
                      --red: #f2878d; --amber: #edc16f; --blue: #83baff;
                      --ease-out: cubic-bezier(.23, 1, .32, 1);
                    }
                    * { box-sizing: border-box; }
                    body { margin: 0; min-height: 100vh; color: var(--text);
                      background: radial-gradient(ellipse at 72% -22%, #123047 0, transparent 42%), var(--bg);
                      font: 14px/1.55 Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                      scrollbar-color: #34475d #0d1520; scrollbar-width: thin; }
                    button, input, select { font: inherit; color: inherit; }
                    ::selection { color: #f3fcff; background: #1b6174; }
                    ::-webkit-scrollbar { width: 9px; height: 9px; }
                    ::-webkit-scrollbar-track { background: #0d1520; }
                    ::-webkit-scrollbar-thumb { border: 2px solid #0d1520; border-radius: 999px; background: #34475d; }
                    ::-webkit-scrollbar-thumb:hover { background: #49647e; }
                    .app { min-height: 100vh; display: grid; grid-template-columns: 248px minmax(0, 1fr); }
                    .sidebar { position: sticky; top: 0; height: 100vh; gap: 38px; padding: 25px 17px;
                      display: flex; flex-direction: column; background: var(--sidebar); border-right: 1px solid #23364a; }
                    .brand { display: flex; align-items: center; min-height: 42px; gap: 11px; padding: 0 8px;
                      font-size: 15px; font-weight: 700; letter-spacing: -.025em; }
                    .brand-mark { display: grid; place-items: center; flex: 0 0 34px; width: 34px; height: 34px;
                      border: 1px solid #2b7187; border-radius: 10px; color: var(--cyan);
                      background: linear-gradient(145deg, #173246, #10202d); box-shadow: inset 0 1px 0 #b6f5ff16; }
                    .brand-mark svg { width: 20px; height: 20px; display: block; }
                    .side-label { padding: 0 12px 9px; color: #8294aa; font-size: 10px; font-weight: 700;
                      letter-spacing: .14em; text-transform: uppercase; }
                    .nav { display: grid; gap: 5px; }
                    .nav a { min-height: 40px; display: flex; align-items: center; gap: 11px; padding: 8px 11px;
                      border: 1px solid transparent; border-radius: 9px; color: #a4b3c5; text-decoration: none;
                      transition: color .16s ease, background-color .16s ease, border-color .16s ease; }
                    .nav-icon { width: 16px; height: 16px; flex: 0 0 16px; color: #8294aa; }
                    .nav a:hover { color: #d5f7fb; background: #142334; }
                    .nav a.active { color: #a8edf5; border-color: #284657; background: #14283a; box-shadow: inset 2px 0 var(--cyan); }
                    .nav a.active .nav-icon { color: var(--cyan); }
                    .sidebar-note { margin-top: auto; padding: 15px 11px 0; border-top: 1px solid var(--line);
                      color: #899bb0; font-size: 11px; line-height: 1.7; }
                    main { width: 100%; min-width: 0; max-width: 1680px; margin: 0 auto; padding: 22px clamp(22px, 3.2vw, 52px) 38px; }
                    .topbar { display: flex; justify-content: space-between; align-items: center; gap: 16px;
                      min-height: 52px; margin-bottom: 34px; padding: 9px 13px;
                      border: 1px solid #2b4057; border-radius: 11px; background: rgba(18, 31, 46, .74);
                      box-shadow: 0 7px 18px #03081124; }
                    .glass, .control, .glass-button { -webkit-backdrop-filter: blur(10px); backdrop-filter: blur(10px); }
                    .topbar-title { display: flex; align-items: center; gap: 9px; min-width: 0; color: #b4c1d0; font-size: 12px; letter-spacing: .005em; }
                    .run-mark { width: 18px; height: 18px; display: grid; place-items: center; border: 1px solid #2b5969;
                      border-radius: 6px; color: var(--cyan); background: #142a38; }
                    .run-mark svg { width: 12px; height: 12px; }
                    .avatar { display: grid; place-items: center; width: 34px; height: 34px; border: 1px solid #2c455b;
                      border-radius: 50%; background: #152638; color: #a5eaf3; font-size: 12px; font-weight: 700; }
                    .heading { display: flex; justify-content: space-between; align-items: center; gap: 20px; margin-bottom: 22px; }
                    h1 { margin: 0 0 7px; font-size: 34px; line-height: 1.15; letter-spacing: -.035em; }
                    .subtitle { max-width: 680px; margin: 0; color: var(--muted); font-size: 13px; }
                    .generated { min-width: 190px; color: #91a2b7; font-size: 11px; line-height: 1.8; text-align: right; }
                    .generated strong { color: #dce6f0; font-size: 12px; font-weight: 600; }
                    .run-state { display: flex; align-items: baseline; justify-content: flex-end; gap: 8px; margin-top: 8px; color: var(--muted); }
                    .run-state strong { color: var(--cyan); font-size: 20px; line-height: 1; letter-spacing: -.035em; font-variant-numeric: tabular-nums; }
                    .run-state-badge { display: inline-flex; align-items: center; gap: 6px; width: fit-content; margin-left: auto;
                      padding: 5px 9px; border: 1px solid #245441; border-radius: 999px; color: #78e0ae;
                      background: #10271f; font-size: 11px; font-weight: 650; }
                    .run-state-badge::before { width: 6px; height: 6px; border-radius: 50%; background: currentColor; content: ""; }
                    .run-state-badge.warning { border-color: #66512c; color: #f0c979; background: #2b2417; }
                    .run-state-badge.failed { border-color: #65333b; color: #f38e92; background: #2d1b22; }
                    .metrics { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 0; margin-bottom: 17px;
                      overflow: hidden; border: 1px solid #29394d; border-radius: 12px; background: #101925; }
                    .metric { min-height: 105px; padding: 16px 18px; border: 0; border-right: 1px solid #29394d; border-radius: 0; background: transparent; }
                    .metric:last-child { border-right: 0; }
                    .metric-label { color: #a6b4c5; font-size: 11px; font-weight: 600; letter-spacing: .01em; }
                    .metric-value { margin: 9px 0 3px; color: #eaf1f8; font-size: 28px; font-weight: 680; letter-spacing: -.035em; font-variant-numeric: tabular-nums; }
                    .metric-note { color: #899bb0; font-size: 10px; }
                    .metric.pass .metric-value { color: var(--green); }
                    .metric.fail .metric-value, .metric.error .metric-value { color: var(--red); }
                    .metric.skip .metric-value { color: var(--amber); }
                    .panels { display: grid; grid-template-columns: minmax(0, 1.12fr) minmax(0, .88fr); gap: 14px; margin: 14px 0; }
                    .panel { min-width: 0; border: 1px solid #29394d; border-radius: 12px; padding: 19px 20px; background: #101925; }
                    .panel h2, .results-heading h2 { margin: 0; color: #e7eef6; font-size: 15px; font-weight: 650; letter-spacing: -.025em; }
                    .panel-subtitle { margin: 4px 0 17px; color: #91a2b7; font-size: 11px; }
                    .distribution { display: flex; width: 100%; height: 11px; overflow: hidden; border-radius: 999px; background: #243144; }
                    .distribution span { transition: width .24s var(--ease-out); }
                    .distribution .passed { background: var(--green); }
                    .distribution .failed, .distribution .error { background: var(--red); }
                    .distribution .skipped { background: var(--amber); }
                    .legend { display: flex; flex-wrap: wrap; gap: 10px 15px; margin-top: 14px; }
                    .legend-item { display: flex; align-items: center; gap: 7px; color: #aab8c8; font-size: 11px; }
                    .dot { width: 7px; height: 7px; border-radius: 50%; }
                    .category-list { display: grid; gap: 15px; }
                    .category-row { display: grid; grid-template-columns: 68px minmax(50px, 1fr) auto; align-items: center; gap: 10px; }
                    .category-name { color: #d9e3ee; font-size: 11px; }
                    .category-meta { color: #a0afc1; font-size: 10px; font-variant-numeric: tabular-nums; }
                    .category-track { height: 6px; overflow: hidden; border-radius: 999px; background: #233144; }
                    .category-track span { display: block; height: 100%; border-radius: inherit; background: linear-gradient(90deg, #3aaec8, #67d9ec); }
                    .results { margin-top: 16px; padding: 0; overflow: hidden; }
                    .results-heading { display: flex; justify-content: space-between; align-items: center; gap: 14px; min-height: 58px; padding: 16px 19px; border-bottom: 1px solid var(--line); }
                    .result-count { color: #a0afc1; font-size: 11px; font-variant-numeric: tabular-nums; }
                    .toolbar { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; padding: 11px 14px;
                      border-bottom: 1px solid var(--line); background: rgba(16, 29, 43, .86); }
                    .control { min-height: 36px; padding: 7px 10px; border: 1px solid #31445b; border-radius: 8px;
                      background: rgba(9, 16, 26, .78); font-size: 12px; transition: border-color .16s ease, box-shadow .16s ease; }
                    .control:focus-visible, .glass-button:focus-visible, .nav a:focus-visible, summary:focus-visible {
                      outline: 2px solid var(--cyan); outline-offset: 2px; }
                    .control:focus-visible { border-color: var(--cyan); box-shadow: 0 0 0 3px #63d7ed1c; }
                    .search { flex: 1 1 250px; }
                    .toolbar select { min-width: 130px; }
                    .glass-button { min-height: 36px; padding: 7px 11px; border: 1px solid #354c64; border-radius: 8px;
                      cursor: pointer;
                      background: rgba(27, 46, 64, .74); font-size: 11px; font-weight: 600;
                      transition: color .16s ease, border-color .16s ease, background-color .16s ease, transform .16s var(--ease-out); }
                    .glass-button:hover { border-color: #4a8799; color: #b7f0f7; background: #1b3446; }
                    .glass-button:active { transform: scale(.98); }
                    .table-wrap { overflow-x: auto; scrollbar-gutter: stable; }
                    table { width: 100%; min-width: 800px; border-collapse: collapse; }
                    th { padding: 11px 14px; color: #9aabc0; text-align: left; font-size: 9px;
                      letter-spacing: .12em; text-transform: uppercase; background: #111b29; }
                    td { padding: 12px 14px; border-top: 1px solid #243448; color: #d5dfeb; vertical-align: top; font-size: 12px; }
                    tbody tr { transition: background-color .14s ease; }
                    tbody tr:hover { background: #152232; }
                    .test-name { color: #e7eef6; font-weight: 600; overflow-wrap: anywhere; }
                    .test-class { color: #9aabc0; font-size: 11px; overflow-wrap: anywhere; }
                    .category-pill, .status-pill { display: inline-flex; align-items: center; gap: 6px; padding: 3px 8px;
                      border-radius: 999px; font-size: 10px; font-weight: 600; white-space: nowrap; }
                    .category-pill { border: 1px solid #294958; color: #9bddeb; background: #142b39; }
                    .status-pill::before { width: 6px; height: 6px; border-radius: 50%; background: currentColor; content: ""; }
                    .status-pill.passed { color: #82e5b2; background: #122b22; }
                    .status-pill.failed, .status-pill.error { color: #ff9ba0; background: #321e25; }
                    .status-pill.skipped { color: #f1cd7e; background: #302817; }
                    .details { max-width: 360px; color: #9aaabd; font-size: 10px; }
                    .details summary { color: #ff9ba0; cursor: pointer; font-weight: 600; overflow-wrap: anywhere; }
                    .details pre { max-height: 240px; overflow: auto; color: #ddb6bc; white-space: pre-wrap; overflow-wrap: anywhere; }
                    .empty { display: grid; justify-items: center; gap: 8px; padding: 35px 20px; color: #a3b2c3;
                      font-size: 12px; text-align: center; background: #0e1722; }
                    .empty[hidden] { display: none; }
                    .empty-mark { width: 30px; height: 30px; display: grid; place-items: center; border: 1px solid #31445a;
                      border-radius: 9px; color: #9aabc0; background: #152233; }
                    .empty-mark svg { width: 16px; height: 16px; }
                    .table-wrap:focus-visible { outline: 2px solid var(--cyan); outline-offset: -2px; }
                    footer { display: flex; justify-content: space-between; gap: 14px; padding: 16px 3px 0; color: #899bb0; font-size: 10px; }
                    .sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden;
                      clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
                    @media (hover: hover) and (pointer: fine) {
                      .glass-button:hover { transform: translateY(-1px); }
                    }
                    @supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
                      .sidebar { background: #0c131e; }
                      .topbar, .toolbar { background: #142338; }
                      .glass-button, .control { background: #142338; }
                    }
                    @media (max-width: 1100px) {
                      .app { grid-template-columns: 212px minmax(0, 1fr); }
                      .heading { align-items: flex-start; flex-direction: column; gap: 13px; }
                      .generated { text-align: left; }
                      .run-state-badge { margin-left: 0; }
                      .run-state { justify-content: flex-start; }
                      .metrics { grid-template-columns: repeat(3, minmax(0, 1fr)); }
                      .metric:nth-child(3) { border-right: 0; }
                      .metric:nth-child(n + 4) { border-top: 1px solid #243346; }
                    }
                    @media (max-width: 900px) {
                      .panels { grid-template-columns: 1fr; }
                    }
                    @media (max-width: 760px) {
                      .app { display: block; }
                      .sidebar { position: relative; height: auto; padding: 12px 14px; border-right: 0; border-bottom: 1px solid var(--line); }
                      .brand { margin-bottom: 10px; }
                      .side-label, .sidebar-note { display: none; }
                      .nav { display: flex; overflow-x: auto; gap: 5px; }
                      .nav a { min-height: 36px; white-space: nowrap; padding: 7px 10px; }
                      main { padding: 16px 13px 28px; }
                      .topbar { margin-bottom: 23px; }
                      .heading { align-items: flex-start; flex-direction: column; gap: 14px; }
                      .generated { text-align: left; }
                      .run-state-badge { margin-left: 0; }
                      .metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); }
                      .metric { min-height: 94px; padding: 14px; border-right: 1px solid #243346; }
                      .metric:nth-child(3) { border-right: 1px solid #243346; }
                      .metric:nth-child(even) { border-right: 0; }
                      .metric:nth-child(n + 3) { border-top: 1px solid #243346; }
                      .metric-value { font-size: 26px; }
                      .panels { grid-template-columns: 1fr; gap: 10px; }
                      .panel { padding: 16px; }
                      .results-heading { align-items: flex-start; flex-direction: column; padding: 16px; }
                      .toolbar { padding: 10px; }
                      .search { flex-basis: 100%; }
                      .toolbar select { flex: 1 1 115px; min-width: 0; }
                      footer { flex-direction: column; gap: 5px; }
                    }
                    @media (max-width: 390px) {
                      .topbar { gap: 9px; padding-inline: 10px; }
                      .topbar-title { font-size: 11px; }
                      .avatar { width: 30px; height: 30px; flex: 0 0 30px; }
                      .metrics { grid-template-columns: 1fr; }
                      .metric, .metric:nth-child(3), .metric:nth-child(even) { border-right: 0; }
                      .metric:nth-child(n + 2) { border-top: 1px solid #243346; }
                      .category-row { grid-template-columns: 58px minmax(36px, 1fr) auto; gap: 7px; }
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
                        <div class="topbar-title"><span class="run-mark" aria-hidden="true"><svg viewBox="0 0 16 16" fill="none"><path d="M2 12.5h3V8h3v2h3V4h3" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg></span><span>QA Automation <span aria-hidden="true">/</span> Latest execution</span></div>
                        <div class="avatar" aria-label="QA Dashboard">QA</div>
                      </header>
                      <section id="overview" aria-labelledby="page-title">
                        <div class="heading">
                          <div><h1 id="page-title">QA Dashboard</h1><p class="subtitle">A focused view of API, web and end-to-end test quality.</p></div>
                          <div class="generated">Report generated<br><strong id="generated-at">—</strong><div class="run-state-badge" id="run-outcome">—</div><div class="run-state">Pass rate <strong id="pass-rate">—</strong></div><div>Run duration <strong id="run-duration">—</strong></div></div>
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
                            <thead><tr><th scope="col">Test</th><th scope="col">Class</th><th scope="col">Category</th><th scope="col">Status</th><th scope="col">Duration</th><th scope="col">Details</th></tr></thead>
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
