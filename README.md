# Task Manager

Excel-driven developer reporting desktop application: import a task workbook,
review per-developer work, generate burndown charts, summarize progress with a
configurable LLM and export polished per-developer PowerPoint decks.

**Author / Developer:** Arpit Sengar

---

## 1. What has been used (technology stack)

| Area | Technology | Version | Purpose |
|------|-----------|---------|---------|
| Language | Java | 21 (compiled with `--release 21`) | Core language, records, switch expressions, text blocks |
| Build | Maven | 3.x | Dependency management, build, test, run, packaging |
| Desktop UI | JavaFX (`javafx-controls`, `javafx-fxml`) | 21.0.4 | Toolbar, tables, dialogs, chart display |
| Excel I/O | Apache POI (`poi-ooxml`) | 5.3.0 | Reading `.xlsx`/`.xls` workbooks |
| PowerPoint I/O | Apache POI (`poi-ooxml` XSLF) | 5.3.0 | Reading templates and writing `.pptx` decks |
| Charts | JFreeChart | 1.5.4 | Rendering burndown time-series charts to images |
| JSON | Jackson (`jackson-databind`) | 2.17.2 | Config persistence + LLM request/response handling |
| HTTP | Java `java.net.http.HttpClient` | JDK built-in | Calls to the LLM providers |
| Logging | SLF4J + Logback | 2.0.13 / 1.5.6 | Application logging |
| Testing | JUnit 5 (Jupiter) | 5.10.2 | Unit tests |
| Packaging | `maven-shade-plugin` | 3.5.3 | Produces a runnable fat jar |
| Runner | `javafx-maven-plugin` | 0.0.8 | `mvn javafx:run` for the UI |

---

## 2. How to execute the application

### 2.1 Prerequisites
- JDK 21 or newer (`java -version`).
- Maven 3.8+ (`mvn -version`).
- Optional: a local [Ollama](https://ollama.com) server, or an API key for an
  external provider, if you want LLM-generated summaries.

### 2.2 Build
```bash
mvn -B clean package
```
This compiles, runs the tests and produces a fat jar at
`target/TaskManager-1.0-SNAPSHOT.jar`.

### 2.3 Run the desktop UI
```bash
mvn -B javafx:run
```
or run the shaded jar (JavaFX modules must be on the module path if you run the
jar directly; `mvn javafx:run` is the simplest way to launch the UI):
```bash
java -jar target/TaskManager-1.0-SNAPSHOT.jar
```

### 2.4 Run headless (one-shot PPTX export)
```bash
java -jar target/TaskManager-1.0-SNAPSHOT.jar --export <input.xlsx> [output.pptx] [--template template.pptx] [--no-llm]
```
Examples:
```bash
# Export next to the default output dir (output/task-report.pptx)
java -jar target/TaskManager-1.0-SNAPSHOT.jar --export tasks.xlsx --no-llm

# Explicit output file
java -jar target/TaskManager-1.0-SNAPSHOT.jar --export tasks.xlsx report.pptx

# Use a PPTX template
java -jar target/TaskManager-1.0-SNAPSHOT.jar --export tasks.xlsx report.pptx --template my-template.pptx --no-llm
```
| Flag | Meaning |
|------|---------|
| `--export <file.xlsx>` | **Required.** Input workbook. |
| `[output.pptx]` | Optional output path. Defaults to `<outputDirectory>/task-report.pptx`. |
| `--template <file.pptx>` | Optional reference deck; overrides the configured template for this run. |
| `--no-llm` | Disable LLM summaries; use the deterministic fallback text. |

Exit codes: `0` success, `1` failure, `2` bad usage.

### 2.5 Run the tests
```bash
mvn -B test
```

---

## 3. Configuration

Configuration is a JSON file persisted at `~/.taskmanager/config.json` and is
edited from the in-app **Settings** dialog.

| Field | Default | Description |
|-------|---------|-------------|
| `llmProvider` | `ollama` | One of `ollama`, `openai`, `azure_openai`, `anthropic`, `custom`. |
| `llmEnabled` | `true` | Enables LLM summaries; falls back to a template when off. |
| `baseUrl` | *(blank)* | Endpoint override; blank = provider default / env. |
| `model` | *(blank)* | Model or Azure deployment name; blank = provider default / env. |
| `apiKey` | *(blank)* | Secret; prefer environment variables. |
| `apiVersion` | `2024-06-01` | Azure OpenAI API version. |
| `temperature` | `0.3` | Sampling temperature. |
| `maxTokens` | `700` | Max response tokens. |
| `requestTimeoutSeconds` | `120` | HTTP timeout. |
| `outputDirectory` | `output` | Default folder for headless exports. |
| `pptTemplatePath` | *(blank)* | Optional PPTX template. |

### 3.1 Environment variable fallbacks
- Generic: `LLM_BASE_URL`, `LLM_MODEL`, `LLM_API_KEY`.
- Ollama: `OLLAMA_BASE_URL`, `OLLAMA_MODEL`.
- OpenAI: `OPENAI_BASE_URL`, `OPENAI_MODEL`, `OPENAI_API_KEY`.
- Azure OpenAI: `AZURE_OPENAI_ENDPOINT`, `AZURE_OPENAI_DEPLOYMENT`, `AZURE_OPENAI_API_KEY`.
- Anthropic: `ANTHROPIC_BASE_URL`, `ANTHROPIC_MODEL`, `ANTHROPIC_API_KEY`.

---

## 4. Input workbook format

The first row is a header. Required columns are **Developer** and **Task**.
Header matching is case- and space-insensitive (e.g. `Start Date` == `start_date`).

| Column | Required | Notes |
|--------|----------|-------|
| `Developer` | yes | Task owner. |
| `Task` | yes | Task name. |
| `Sprint` | no | Free text. |
| `Status` | no | Mapped to `To Do`, `In-progress`, `Done`, `Blocked`. Unknown values warn and default to `To Do`. |
| `Assigned Date` | no | Date. |
| `Start Date` | no | Date. |
| `End Date` | no | Date; also the day a `Done` task counts as complete in the burndown. |
| *(date-headed columns)* | no | Any extra columns whose **headers are dates** are treated as day-wise **Time Spent (hours)**. |

Dates accept real Excel dates, serial numbers, or common text formats
(`dd-MM-yyyy`, `d MMM yyyy`, `yyyy/M/d`, ...). Rows missing Developer/Task are
skipped with a warning.

---

## 5. PowerPoint template contract

An optional reference `.pptx` can replace the built-in layout. It must contain:

| Slide | Markers | Behaviour |
|-------|---------|-----------|
| Title (first slide) | `{{title}}`, `{{date}}` | Filled in place. |
| Summary | `{{developer}}`, `{{summary}}` and a **table** | Cloned once per developer; tokens filled; task rows appended to the table. |
| Burndown | `{{developer}}`, `{{chart}}` | Cloned once per developer; the `{{chart}}` shape position is the image anchor. |

If the template is missing or invalid, the exporter logs a warning and silently
falls back to the built-in layout, so export never fails.

---

## 6. End-to-end flow

### 6.1 Desktop UI flow
1. **Startup** — `App.start` shows a splash **loading overlay with a percentage**
   (`0% → 100%`), loads `AppConfig`, builds `MainView.root()` (toolbar, split pane,
   status bar) and swaps in the 1240×780 main window.
2. **Open Excel…** — a file chooser selects a workbook; import runs on a background
   worker and the overlay shows real progress (`10–100%` by row); developers, date
   range and the task table are populated; import warnings/notes appear on the
   **Summary** tab.
3. **Select developers** — selecting rows in the developer list filters the table
   (used by Burndown and Summarize; Export always uses **all** developers).
4. **Generate Burndown** — computes a `BurndownSeries` and renders a chart image
   into the left panel (`10%` compute → `60%` render → `100%`).
5. **Summarize Selected** — builds a per-developer status narrative via the LLM
   (falls back to a deterministic template when unavailable); the overlay tracks
   `i/n` per developer.
6. **Export PPT** — builds a `DeveloperReport` for **every developer in the
   workbook** (selection is ignored) and writes a `.pptx` (template or built-in);
   the overlay tracks per-developer builds then the deck write.
7. **Settings** — edit provider/model/key/timeouts/template, optionally
   *Test connection* / *Detect models*, and save.
8. **About** — shows the app name, version and developer.
9. **Close (✕)** — a confirmation popup asks *"Save application state and exit?"*;
   **OK** saves the config and closes, **Cancel** returns to the app.

### 6.2 Headless flow (`--export`)
```
args → parse flags → AppConfig.load()
     → (optional) LlmClientFactory.detectModels() auto-model detection
     → ExcelImportService.importTasks(input)
     → DateRange.covering(tasks)
     → LlmClientFactory.create(config) → ProjectService
     → per developer: buildReport(developer, tasks, range, withSummary)
     → ProjectService.export(output, title, reports)
     → log result, return exit code (0 ok / 1 error / 2 bad usage)
```

### 6.3 Flow diagrams

**1) Startup (desktop UI)**

```
                    ┌──────────────────────────────────────────┐
                    │             Launcher.main                │
                    │  log args, then App.main(args)           │
                    └───────────────────┬──────────────────────┘
                                        │
                                        ▼
                               App.main(args)
                                        │
                       ┌────────────────┴─────────────────┐
                       │                                  │
                args[0] == "--export"              otherwise (UI)
                       │                                  │
                       ▼                                  ▼
        ┌───────────────────────────┐     ┌─────────────────────────────┐
        │  runHeadless() (see #3)   │     │  App.start(stage)            │
        └───────────────────────────┘     │  setOnCloseRequest → #4      │
                                          │                             │
                                          │  show splash loader 0%       │
                                          │    ┌─────────────────────────┤
                                          │    │ LoadingOverlay          │
                                          │    │  20% Loading config...  │
                                          │    │  50% init services      │
                                          │    │  75% building UI        │
                                          │    │ 100% Ready              │
                                          │    └─────────────────────────┤
                                          │                             │
                                          │  build MainView.root()       │
                                          │  swap scene → 1240x780       │
                                          └─────────────────────────────┘
```

**2) User actions + loading overlay**

```
   Main window  (MainView.root = StackPane { border, LoadingOverlay })
                                  │
                 runBackground() drives LoadingOverlay.setProgress(n%)
                                  │
   ┌───────────────┬──────────────┴──────────────┬───────────────────────┐
   ▼               ▼                             ▼                       ▼
  Open Excel      Generate Burndown          Summarize Selected      Export PPT
   │                │                           │                       │
   ▼                ▼                           │                       │
ExcelImportService BurndownService +          LlmSummaryService      ProjectService
   10→100% by row   ChartRenderer               i/n per dev            per developer
   │                10% compute │                │        │            5–85% build
   │                60% render  │          LLM call /        │         90% write deck
   │                100% done   │          fallback text     ▼
   ▼                        ▼              per developer  PptExportService
Task list +             chart image →      → summaryArea   (ALL developers)
import notes →          chartView                           │
table + Summary tab                                      100% → output .pptx
```

**3) Headless one-shot export**

```
java -jar TaskManager.jar --export input.xlsx [out.pptx] [--template t.pptx] [--no-llm]
   │
   ▼
App.runHeadless(args)
   │
   ├─ parse flags .......................... (missing input → exit 2)
   ├─ AppConfig.load() → llmEnabled = !no-llm && config.llmEnabled
   ├─ if model blank: LlmClientFactory.detectModels(config) auto-pick
   ├─ ExcelImportService.importTasks(input) → task list
   ├─ DateRange.covering(tasks)
   ├─ LlmClientFactory.create(config) → new ProjectService(client, template)
   ├─ for each developer (all): buildReport(dev, tasksFor(...), range, summary)
   ├─ ProjectService.export(output, "Developer Task Report", reports)
   └─ log summary → exit 0            (on exception → log error → exit 1)
```

**4) Close on exit**

```
User clicks window close (✕)
        │
        ▼
stage.setOnCloseRequest → App.onCloseRequested(event, stage)
        │
        ├─ consume the close event (window stays open)
        ▼
Confirmation popup  "Save application state and exit?"
        │
        ├─ [OK]     → AppConfig.save() → allowClose = true
        │              → stage.close()  → app exits
        │
        └─ [Cancel] → popup closes → control returns to the application
```

---

## 7. Role of each method (by class)

### `org.parkar.Launcher`
| Method | Role |
|--------|------|
| `main(String[])` | Delegates to `App.main`; being a non-`Application` class avoids the JavaFX launcher check on the classpath. |

### `org.parkar.App` (extends `javafx.application.Application`)
| Method | Role |
|--------|------|
| `start(Stage)` | Shows a splash **loading overlay** (0→100%), loads config, builds `MainView`, sets the scene (1240×780) and titles/shows the window. |
| `main(String[])` | Dispatch: runs headless export when the first arg is `--export`, otherwise launches the JavaFX UI. |
| `runHeadless(String[])` | Parses CLI flags, optionally auto-detects the model, imports the workbook, builds a report per developer, exports the deck and returns the process exit code. |
| `onCloseRequested(WindowEvent, Stage)` | Intercepts the window close: asks the user to confirm; **OK** saves state + closes, **Cancel** resumes the app (uses an `allowClose` guard). |
| `saveState()` | Persists the config to `~/.taskmanager/config.json` (called on confirmed exit). |
| `defaultOutput(AppConfig)` | Resolves the default output path `<outputDirectory>/task-report.pptx`. |

### `org.parkar.config.AppConfig`
| Method | Role |
|--------|------|
| `defaultLocation()` | Returns `~/.taskmanager/config.json`. |
| `load()` / `load(Path)` | Reads JSON config; returns defaults if missing/unreadable. |
| `save()` / `save(Path)` | Creates parent dirs and writes the config as indented JSON. |
| `provider()` | Maps the `llmProvider` string to the `LlmProvider` enum. |
| `resolvedBaseUrl()` | Resolves base URL: explicit config → env → provider default (trailing slashes stripped). |
| `resolvedModel()` | Resolves model/deployment: explicit config → env → provider default. |
| `resolvedApiKey()` | Resolves API key: explicit config → `LLM_API_KEY` → provider-specific env. |
| `providerEnv(String)` | Returns the provider-prefixed env var (e.g. `OPENAI_` + suffix). |
| `providerApiKeyEnv()` | Returns the provider-specific API key env var name. |
| `providerDefaultBaseUrl()` | Provider default endpoint when nothing else is set. |
| `providerDefaultModel()` | Provider default model when nothing else is set. |
| `firstNonBlank(String...)` | Returns the first non-blank, trimmed value or `null`. |
| `env(String)` | Reads an environment variable, treating blank as absent. |

### `org.parkar.domain.Task` (record)
| Method | Role |
|--------|------|
| *(canonical constructor)* | Defensively copies the `timeSpent` list (null → empty). |
| `totalHours()` | Sums all day-wise time entries. |
| `completedOn()` | Returns the `End Date` when the status is `Done`, else `null`. |
| `isRemainingOn(LocalDate)` | True when the task is still open (not done or done after the date); drives the burndown. |

### `org.parkar.domain.TaskStatus` (enum)
| Method | Role |
|--------|------|
| `label()` | Human-readable label (`To Do`, `In-progress`, `Done`, `Blocked`). |
| `fromCell(String)` | Leniently maps free-form status text to an enum value (or `null` if unknown). |

### `org.parkar.domain.TimeEntry` (record)
| Method | Role |
|--------|------|
| *(canonical constructor)* | Validates that `date` is non-null and `hours` is non-negative. |

### `org.parkar.domain.DateRange` (record)
| Method | Role |
|--------|------|
| *(canonical constructor)* | Validates non-null, ordered bounds. |
| `days()` | Returns the inclusive list of days in the range. |
| `covering(List<Task>)` | Derives the overall range from tasks' start/assigned and end dates. |
| `earlier` / `later` | Null-safe min/max helpers. |

### `org.parkar.excel.ExcelImportService`
| Method | Role |
|--------|------|
| `importTasks(Path)` | Imports from the first sheet (delegates to the progressive variant). |
| `importTasks(Path, int)` | Imports a given sheet index (delegates to the progressive variant). |
| `importTasks(Path, int, ProgressListener)` | Main parse: maps headers, detects date-headed time columns, builds `Task`s, collects warnings and reports row-based progress for the loading overlay. |
| `cell(Row, Integer)` | Null-safe cell lookup by column index. |
| `mapHeaders(Row)` | Normalised header text → column index map. |
| `isKnownColumn(String)` | True for the fixed columns so they aren't mistaken for time columns. |
| `normalize(String)` | Lower-cases and strips non-alphanumerics from headers. |
| `text(Cell, FormulaEvaluator)` | Reads a cell as trimmed text, honouring formulas. |
| `number(Cell, FormulaEvaluator)` | Reads a cell as a number (numeric, formula or text). |
| `parseNumber(String)` | Parses a numeric string, stripping `hr`/`hrs` suffixes. |
| `parseStatus(String, int, List)` | Maps status text, warning on unknown values. |
| `isBlank(String)` / `trimOrEmpty(String)` | Small string helpers. |

### `org.parkar.excel.ImportResult` (record)
| Method | Role |
|--------|------|
| *(canonical constructor)* | Defensive copies of tasks/warnings/time columns. |

### `org.parkar.service.BurndownService`
| Method | Role |
|--------|------|
| `compute(String, List<Task>, DateRange)` | Task-count burndown: actual = tasks remaining per day; ideal = linear decrease from total to zero. |
| `box(double[])` | Converts a primitive array to `List<Double>`. |

### `org.parkar.service.BurndownSeries` (record)
| Method | Role |
|--------|------|
| *(canonical constructor)* | Defensive copies of the series lists. |

### `org.parkar.service.DeveloperReport` (record)
| Method | Role |
|--------|------|
| *(canonical constructor)* | Defensive copy of the task list. |

### `org.parkar.service.ProjectService`
| Method | Role |
|--------|------|
| `ProjectService(LlmClient)` / `ProjectService(LlmClient, String)` | Constructs the summary service and stores the PPT template path. |
| `setPptTemplatePath(String)` / `pptTemplatePath()` | Get/set the template path. |
| `developers(List<Task>)` | Distinct developer names, case-insensitive, sorted. |
| `tasksFor(List<Task>, String)` | Tasks belonging to one developer. |
| `burndown(String, List<Task>, DateRange)` | Delegates to `BurndownService`. |
| `buildReport(String, List<Task>, DateRange, boolean)` | Bundles tasks + optional summary + optional burndown into a `DeveloperReport`. |
| `export(Path, String, List<DeveloperReport>)` | Delegates deck generation to `PptExportService`. |

### `org.parkar.chart.BurndownChartRenderer`
| Method | Role |
|--------|------|
| `render(BurndownSeries)` | Renders at the default 900×480 size. |
| `render(BurndownSeries, int, int)` | Builds the JFreeChart time-series (ideal dashed grey, actual blue) and returns a `BufferedImage`. |
| `toDate(LocalDate)` | Converts a `LocalDate` to `java.util.Date`. |

### `org.parkar.llm.LlmClient` (interface)
| Method | Role |
|--------|------|
| `complete(String, String)` | Sends a single-turn request and returns the assistant text. |
| `availableModels()` | Lists the models the backend offers (used for auto-detection / *Detect models*); defaults to an empty list. |
| `provider()` | Default human-readable provider name. |

### `org.parkar.llm.LlmProvider` (enum)
| Method | Role |
|--------|------|
| `label()` / `id()` | Display label / lowercase id. |
| `requiresApiKey()` | Whether the provider needs an API key. |
| `from(String)` | Maps a config string (incl. aliases like `groq`) to a provider. |
| `toString()` | Returns `label()`. |

### `org.parkar.llm.LlmClientFactory`
| Method | Role |
|--------|------|
| `create(AppConfig)` | Returns `NoOpClient` when disabled, otherwise the matching HTTP client for the provider. |
| `detectModels(AppConfig)` | Queries the backend for available models (used for auto-detection and the Settings *Detect models* button). |

### `org.parkar.llm.JsonSupport` (abstract)
| Method | Role |
|--------|------|
| `chatMessages(String, String)` | Builds an OpenAI-style messages array (optional system prompt). |
| `object()` | Creates a new Jackson `ObjectNode`. |
| `readPath(JsonNode, String...)` | Safely reads a nested JSON path, supporting array indices. |

### `org.parkar.llm.AbstractHttpLlmClient` (abstract)
| Method | Role |
|--------|------|
| `complete(String, String)` | Template method: build body, POST, validate status, parse, trim; wraps errors in `LlmException`. |
| `endpoint()` *(abstract)* | Provider request URL. |
| `applyHeaders(HttpRequest.Builder)` *(abstract)* | Adds auth headers. |
| `buildBody(String, String)` *(abstract)* | Builds the JSON payload. |
| `parseResponse(String)` *(abstract)* | Extracts assistant text from the response. |
| `requireApiKey()` | Throws if the API key is missing. |
| `snippet(String)` | Truncates response bodies for error messages. |

### Provider clients
| Class | Notables |
|-------|----------|
| `OllamaClient` | `endpoint()` → `/api/chat`; no auth; parses `message.content`; `provider()` includes model. |
| `OpenAiCompatibleClient` | `/chat/completions`; `Authorization: Bearer`; parses `choices[0].message.content`. |
| `AzureOpenAiClient` | Deployment URL with `api-version`; `api-key` header; parses `choices[0].message.content`. |
| `AnthropicClient` | `/v1/messages`; `x-api-key` + `anthropic-version`; concatenates `content[].text`. |
| `NoOpClient` | Always throws `LlmException` so the summary service uses its fallback. |

### `org.parkar.llm.LlmSummaryService`
| Method | Role |
|--------|------|
| `summarize(String, List<Task>)` | Calls the LLM; on failure logs and returns the deterministic fallback. |
| `buildPrompt(String, List<Task>)` | Builds the per-developer task listing prompt. |
| `fallback(String, List<Task>)` | Counts done/in-progress/todo/blocked, completion %, hours and blockers. |
| `safe(String)` / `trim(double)` | Formatting helpers. |

### `org.parkar.llm.LlmException`
| Method | Role |
|--------|------|
| *(constructors)* | `RuntimeException` subclasses carrying the failure reason and cause. |

### `org.parkar.ppt.PptExportService`
| Method | Role |
|--------|------|
| `export(Path, String, List)` | Built-in-layout export (no template). |
| `export(Path, String, List, String)` | Chooses template export when a valid path exists, else built-in. |
| `exportProgrammatic(Path, String, List)` | Builds the deck from scratch (title + per-developer + burndown slides). |
| `exportFromTemplate(Path, String, List, Path)` | Loads the template, fills/clones slides, removes template slides, writes output. |
| `clone(XMLSlideShow, XSLFSlide)` | Deep-copies a slide per developer. |
| `collectText(XSLFSlide)` | Concatenates all text-shape text (used to find marker slides). |
| `replaceTokens(XSLFSlide, Map)` | Replaces `{{token}}` markers inside text runs. |
| `findChartAnchor(XSLFSlide)` | Finds the `{{chart}}` shape's bounds (or a default rectangle). |
| `insertChart(XMLSlideShow, XSLFSlide, BurndownSeries, Rectangle2D)` | Renders the chart PNG and inserts it at the anchor. |
| `fillTaskTable(XSLFSlide, List<Task>)` | Appends task rows (capped at `MAX_TABLE_ROWS`) to a template table. |
| `findTable(XSLFSlide)` | Returns the first table shape, if any. |
| `write(XMLSlideShow, Path)` | Creates parent dirs and writes the `.pptx`. |
| `addTitleSlide` / `addDeveloperSlide` / `addBurndownSlide` | Built-in slide builders. |
| `buildTaskTable(XSLFSlide, List<Task>)` | Builds the built-in task table. |
| `addCell` / `boldCell` | Cell text + font styling. |
| `addText(XSLFSlide, ...)` | Adds a styled text box. |

### `org.parkar.ui.MainView`
| Method | Role |
|--------|------|
| `MainView(Stage, AppConfig)` | Stores the stage/config, creates the `ProjectService` and attaches the close-confirmation handler (OK → save + close, Cancel → resume). |
| `root()` | Assembles the `BorderPane` into a `StackPane` with a `LoadingOverlay` on top and wires selection changes. |
| `buildToolBar()` | Creates Open/Settings/Burndown/Summarize/Export and the right-aligned About button. |
| `buildMainArea()` | Left panel (developers, date range, chart) + tabbed Tasks/Summary area. |
| `buildStatusBar()` | Progress state text + status label (the overlay owns the percentage indicator). |
| `buildTable()` | Defines the task table columns. |
| `column(String, Function)` | Column factory reading a value from a `Task`. |
| `fmt(LocalDate)` | Formats a date or `-`. |
| `openExcel()` | File chooser + background import (row-based progress) + UI population + import notes. |
| `showImportNotes()` | Writes import summary/warnings to the Summary tab. |
| `refreshTable()` | Filters the table by selected developers. |
| `selectedDevelopers()` | Selected developers, or all when none selected. |
| `selectedTasks(List)` | Collects tasks for the given developers. |
| `currentRange()` | Date range from the pickers (swaps if reversed; falls back to tasks). |
| `generateBurndown()` | Computes + renders the burndown image for the selection. |
| `summarizeSelected()` | Builds a per-developer LLM (or fallback) summary for the selection, appending each `i/n` result. |
| `exportPpt()` | File chooser + background build of `DeveloperReport`s for **all** developers (selection ignored) + deck export. |
| `showAbout()` | About dialog highlighting the developer name. |
| `showSettings()` | Settings dialog (provider/key/timeouts/template, test connection, detect models, save). |
| `readConfigFromForm(...)` *(static)* | Reads an `AppConfig` from the settings form. |
| `llmDescription()` | Human-readable LLM description for the status bar. |
| `parseInt` / `parseDouble` *(static)* | Tolerant numeric parsing with fallback. |
| `clamp(double)` *(static)* | Bounds a progress fraction to `[0, 1]`. |
| `runBackground(String, ProgressWork<T>, Consumer<T>)` | Runs progress-aware work on a daemon thread; drives the `LoadingOverlay` percentage + status bar and shows errors on the UI thread. |
| `warn(String)` | Shows a warning alert. |

### `org.parkar.ui.LoadingOverlay`
| Member | Role |
|--------|------|
| `LoadingOverlay()` | A semi-transparent full-window overlay with a progress bar and percentage label, initially hidden. |
| `show(String)` | Shows the overlay with an initial message. |
| `setProgress(double)` | Updates the bar and the `NN%` label (clamped to 0–100). |
| `setMessage(String)` | Updates the status text under the bar. |
| `hide()` | Hides the overlay once work completes. |
| `VisibleProperty` | Bound by `MainView.runBackground` so concurrent actions stay consistent. |

### `org.parkar.util.ProgressListener`
| Method | Role |
|--------|------|
| `onProgress(double, String)` | Called with a fraction `0..1` and a human-readable message while a task executes (import, build, export). |

---

## 8. Output artifacts
- **UI:** burndown chart shown in-app; PPTX written wherever the save dialog points.
- **Headless:** `output/task-report.pptx` by default (or the path you pass).
- Charts are embedded as PNG images in the deck.

## 9. Tests
`mvn -B test` runs 22 tests covering date parsing, status mapping, Excel import,
burndown computation, config resolution, LLM summary fallback, OpenAI client
(mock HTTP), and both programmatic and template PPTX export.

## 10. Project structure
```
src/main/java/org/parkar/
├── App.java                 # JavaFX entry + headless --export + close/save-on-exit
├── Launcher.java            # plain main (avoids JavaFX launcher check)
├── chart/                   # JFreeChart burndown renderer
├── config/                  # AppConfig (JSON persistence + env resolution)
├── domain/                  # Task, TaskStatus, TimeEntry, DateRange
├── excel/                   # ExcelImportService, ImportResult
├── llm/                     # provider abstraction + clients + summary service
├── ppt/                     # PptExportService (built-in + template)
├── service/                 # ProjectService, BurndownService, DTOs
├── ui/                      # MainView, LoadingOverlay (JavaFX UI + progress loader)
└── util/                    # DateParser, ProgressListener
src/test/java/org/parkar/    # JUnit 5 tests
```

## 11. Runtime notes
- **Logging** — SLF4J + Logback writes INFO-formatted logs to the console
  (`src/main/resources/logback.xml`); step-level loggers (`Launcher`, `App`,
  `MainView`, services, clients, `PptExportService`, `ExcelImportService`) trace
  every flow step and error.
- **Loading overlay** — the splash and all long actions (import, burndown,
  summarize, export) report `n%` progress through a `ProgressListener` /
  `LoadingOverlay`; the window stays responsive while work runs on daemon threads.
- **Exit safety** — closing the window never loses settings silently: a
  confirmation popup asks to save state, and OK persists `~/.taskmanager/config.json`.
