package org.parkar.ui;

import javafx.concurrent.WorkerStateEvent;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.beans.property.SimpleStringProperty;
import org.parkar.chart.BurndownChartRenderer;
import org.parkar.config.AppConfig;
import org.parkar.domain.DateRange;
import org.parkar.domain.Task;
import org.parkar.excel.ExcelImportService;
import org.parkar.excel.ImportResult;
import org.parkar.llm.LlmClientFactory;
import org.parkar.llm.LlmProvider;
import org.parkar.service.BurndownSeries;
import org.parkar.service.DeveloperReport;
import org.parkar.service.ProjectService;
import org.parkar.util.ProgressListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Assembled programmatically; hosts the whole desktop workflow. */
public class MainView {

    private static final Logger log = LoggerFactory.getLogger(MainView.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

    static final String APP_NAME = "Task Manager";
    static final String APP_VERSION = "1.0";
    static final String DEVELOPER_NAME = "Arpit Sengar";
    static final String ABOUT_TEXT =
            "An Excel-driven developer reporting tool. Import a task workbook to review each "
            + "developer's work, generate burndown charts, summarize progress with an LLM and "
            + "export polished PowerPoint decks.";
    static final String ABOUT_TECH =
            "Built with Java 21, JavaFX, Apache POI, JFreeChart and Jackson.";

    private final Stage stage;
    private final AppConfig config;
    private final BurndownChartRenderer chartRenderer = new BurndownChartRenderer();

    private ProjectService service;
    private ImportResult importResult;
    private List<Task> allTasks = List.of();

    private final ListView<String> developerList = new ListView<>();
    private final DatePicker startPicker = new DatePicker();
    private final DatePicker endPicker = new DatePicker();
    private final TableView<Task> table = new TableView<>();
    private final ImageView chartView = new ImageView();
    private final TextArea summaryArea = new TextArea();
    private final Label status = new Label("Open an Excel file to begin.");
    private final LoadingOverlay loader = new LoadingOverlay();

    public MainView(Stage stage, AppConfig config) {
        this.stage = stage;
        this.config = config;
        this.service = new ProjectService(LlmClientFactory.create(config), config.pptTemplatePath);
        log.info("MainView constructed: provider={}, template={}",
                config.provider().label(), config.pptTemplatePath == null || config.pptTemplatePath.isBlank()
                        ? "(built-in)" : config.pptTemplatePath);
    }

    public Parent root() {
        BorderPane border = new BorderPane();
        border.setTop(buildToolBar());
        border.setCenter(buildMainArea());
        border.setBottom(buildStatusBar());
        border.setPadding(new Insets(4));

        developerList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        developerList.getSelectionModel().getSelectedItems()
                .addListener((javafx.collections.ListChangeListener<String>) c -> refreshTable());
        buildTable();

        StackPane root = new StackPane(border, loader);
        return root;
    }

    private ToolBar buildToolBar() {
        Button open = new Button("Open Excel...");
        open.setOnAction(e -> openExcel());
        Button settings = new Button("Settings");
        settings.setOnAction(e -> showSettings());
        Button burndown = new Button("Generate Burndown");
        burndown.setOnAction(e -> generateBurndown());
        Button summarize = new Button("Summarize Selected");
        summarize.setOnAction(e -> summarizeSelected());
        Button export = new Button("Export PPT");
        export.setOnAction(e -> exportPpt());
        Button about = new Button("About");
        about.setOnAction(e -> showAbout());
        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return new ToolBar(open, settings, new Label("  |  "), burndown, summarize, export, spacer, about);
    }

    private SplitPane buildMainArea() {
        VBox left = new VBox(8,
                new Label("Developers"),
                developerList,
                new Label("Date range"),
                new HBox(6, startPicker, new Label("to"), endPicker),
                chartView);
        left.setPadding(new Insets(6));
        left.setPrefWidth(300);
        VBox.setVgrow(developerList, Priority.ALWAYS);

        chartView.setPreserveRatio(true);
        chartView.setFitWidth(280);

        TabPane tabs = new TabPane();
        Tab tasksTab = new Tab("Tasks", table);
        tasksTab.setClosable(false);
        Tab summaryTab = new Tab("Summary", summaryArea);
        summaryTab.setClosable(false);
        tabs.getTabs().addAll(tasksTab, summaryTab);

        SplitPane split = new SplitPane(left, tabs);
        split.setDividerPositions(0.28);
        return split;
    }

    private HBox buildStatusBar() {
        HBox box = new HBox(8, status);
        box.setPadding(new Insets(4));
        return box;
    }

    private void buildTable() {
        table.getColumns().addAll(List.of(
                column("Developer", t -> t.developer()),
                column("Task", t -> t.name()),
                column("Sprint", t -> t.sprint()),
                column("Status", t -> t.status().label()),
                column("Assigned", t -> fmt(t.assignedDate())),
                column("Start", t -> fmt(t.startDate())),
                column("End", t -> fmt(t.endDate())),
                column("Hours", t -> String.format(Locale.ENGLISH, "%.1f", t.totalHours()))));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private TableColumn<Task, String> column(String title, java.util.function.Function<Task, String> extractor) {
        TableColumn<Task, String> col = new TableColumn<>(title);
        col.setCellValueFactory(cd -> new SimpleStringProperty(extractor.apply(cd.getValue())));
        col.setPrefWidth(110);
        return col;
    }

    private static String fmt(LocalDate date) {
        return date == null ? "-" : date.format(DATE);
    }

    // ---------------------------------------------------------------- actions

    private void openExcel() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select task workbook");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel workbook", "*.xlsx", "*.xls"));
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }
        log.info("Open Excel requested: {}", file);
        runBackground("Importing " + file.getName() + "...",
                progress -> new ExcelImportService().importTasks(Path.of(file.toURI()), 0, progress),
                result -> {
                    this.importResult = result;
                    this.allTasks = result.tasks();
                    developerList.getItems().setAll(service.developers(allTasks));
                    DateRange range = DateRange.covering(allTasks);
                    startPicker.setValue(range.start());
                    endPicker.setValue(range.end());
                    refreshTable();
                    String msg = "Imported " + allTasks.size() + " tasks, " + developerList.getItems().size()
                            + " developers";
                    if (!result.warnings().isEmpty()) {
                        msg += " (" + result.warnings().size() + " warning(s))";
                    }
                    status.setText(msg);
                    showImportNotes();
                    log.info("Imported {} task(s), {} developer(s), {} warning(s) from {}",
                            allTasks.size(), developerList.getItems().size(), result.warnings().size(), file);
                });
    }

    private void showImportNotes() {
        if (importResult == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Imported ").append(importResult.tasks().size()).append(" task(s) for ")
                .append(developerList.getItems().size()).append(" developer(s).\n");
        sb.append("Day-wise time columns detected: ")
                .append(importResult.timeColumns().isEmpty() ? "none" : String.join(", ", importResult.timeColumns()))
                .append("\n\n");
        if (importResult.warnings().isEmpty()) {
            sb.append("No import warnings.");
        } else {
            sb.append("Warnings:\n");
            importResult.warnings().forEach(w -> sb.append("- ").append(w).append('\n'));
        }
        summaryArea.setText(sb.toString());
    }

    private void refreshTable() {
        List<String> selected = developerList.getSelectionModel().getSelectedItems();
        if (selected == null || selected.isEmpty()) {
            table.getItems().setAll(allTasks);
            return;
        }
        List<Task> filtered = new ArrayList<>();
        for (String dev : selected) {
            filtered.addAll(service.tasksFor(allTasks, dev));
        }
        table.getItems().setAll(filtered);
    }

    private List<String> selectedDevelopers() {
        List<String> selected = new ArrayList<>(developerList.getSelectionModel().getSelectedItems());
        if (selected.isEmpty() && !developerList.getItems().isEmpty()) {
            selected.addAll(developerList.getItems());
        }
        return selected;
    }

    private List<Task> selectedTasks(List<String> developers) {
        List<Task> tasks = new ArrayList<>();
        for (String dev : developers) {
            tasks.addAll(service.tasksFor(allTasks, dev));
        }
        return tasks;
    }

    private DateRange currentRange() {
        LocalDate start = startPicker.getValue();
        LocalDate end = endPicker.getValue();
        if (start == null || end == null) {
            return DateRange.covering(allTasks);
        }
        if (end.isBefore(start)) {
            LocalDate tmp = start;
            start = end;
            end = tmp;
        }
        return new DateRange(start, end);
    }

    private void generateBurndown() {
        if (allTasks.isEmpty()) {
            warn("Import an Excel file first.");
            return;
        }
        List<String> developers = selectedDevelopers();
        if (developers.isEmpty()) {
            warn("No developer selected.");
            return;
        }
        List<Task> tasks = selectedTasks(developers);
        String owner = developers.size() == 1 ? developers.get(0) : developers.size() + " developers";
        DateRange range = currentRange();
        log.info("Generate burndown requested for {} (range {} to {})", owner, range.start(), range.end());
        runBackground("Generating burndown for " + owner + "...", progress -> {
            progress.onProgress(0.1, "Computing burndown series...");
            BurndownSeries series = service.burndown(owner, tasks, range);
            progress.onProgress(0.6, "Rendering chart...");
            BufferedImage image = chartRenderer.render(series);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", bos);
            return new Image(new ByteArrayInputStream(bos.toByteArray()));
        }, image -> {
            chartView.setImage(image);
            status.setText("Burndown: " + owner + " (" + range.start() + " to " + range.end() + ")");
            log.info("Burndown chart rendered for {}", owner);
        });
    }

    private void summarizeSelected() {
        List<String> developers = selectedDevelopers();
        if (developers.isEmpty()) {
            warn("No developer selected.");
            return;
        }
        DateRange range = currentRange();
        log.info("Summarize requested for {} developer(s) via {}", developers.size(), llmDescription());
        runBackground("Generating summary via " + llmDescription() + "...", progress -> {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < developers.size(); i++) {
                String dev = developers.get(i);
                progress.onProgress((double) i / developers.size(), "Summarizing " + dev + "...");
                sb.append(service.buildReport(dev, service.tasksFor(allTasks, dev), range, true).summary());
                if (i < developers.size() - 1) {
                    sb.append("\n\n");
                }
            }
            progress.onProgress(1.0, "Summary complete");
            return sb.toString();
        }, summary -> summaryArea.setText(summary));
    }

    private void exportPpt() {
        if (allTasks.isEmpty()) {
            warn("Import an Excel file first.");
            return;
        }
        List<String> developers = service.developers(allTasks);
        if (developers.isEmpty()) {
            warn("No tasks to export.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save PowerPoint");
        chooser.setInitialFileName("task-report.pptx");
        File outputDir = new File(config.outputDirectory == null || config.outputDirectory.isBlank()
                ? "output" : config.outputDirectory);
        if (outputDir.isDirectory()) {
            chooser.setInitialDirectory(outputDir);
        }
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PowerPoint", "*.pptx"));
        File out = chooser.showSaveDialog(stage);
        if (out == null) {
            return;
        }
        DateRange range = currentRange();
        log.info("Export PPT requested: {} developer(s) to {}, range {} to {}",
                developers.size(), out, range.start(), range.end());
        runBackground("Building PowerPoint for " + developers.size() + " developer(s)...", progress -> {
            List<DeveloperReport> reports = new ArrayList<>();
            for (int i = 0; i < developers.size(); i++) {
                String dev = developers.get(i);
                progress.onProgress(0.05 + 0.8 * (double) i / developers.size(), "Building report for " + dev + "...");
                reports.add(service.buildReport(dev, service.tasksFor(allTasks, dev), range, true));
            }
            progress.onProgress(0.9, "Writing " + out.getName() + "...");
            Path written = service.export(out.toPath(), "Developer Task Report", reports);
            progress.onProgress(1.0, "Export complete");
            return written;
        }, path -> {
            status.setText("Exported: " + path);
            summaryArea.setText("Exported report to:\n" + path);
            log.info("PPT exported successfully to {}", path);
        });
    }

    private void showAbout() {
        Label appName = new Label(APP_NAME);
        appName.setFont(javafx.scene.text.Font.font("System", javafx.scene.text.FontWeight.BOLD, 22));

        Label version = new Label("Version " + APP_VERSION);
        version.setStyle("-fx-text-fill: #6b7280;");

        Label byLine = new Label("Developed by");
        byLine.setStyle("-fx-text-fill: #6b7280;");

        Label developer = new Label(DEVELOPER_NAME);
        developer.setFont(javafx.scene.text.Font.font("System", javafx.scene.text.FontWeight.BOLD, 18));
        developer.setStyle("-fx-text-fill: #1565c0;");

        Label about = new Label(ABOUT_TEXT);
        about.setWrapText(true);
        about.setMaxWidth(380);

        Label tech = new Label(ABOUT_TECH);
        tech.setWrapText(true);
        tech.setMaxWidth(380);
        tech.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 11px;");

        VBox content = new VBox(8, appName, version, new javafx.scene.control.Separator(),
                byLine, developer, new javafx.scene.control.Separator(), about, tech);
        content.setPadding(new Insets(16));

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("About " + APP_NAME);
        dialog.initOwner(stage);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
        dialog.showAndWait();
    }

    private void showSettings() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Settings");
        dialog.initOwner(stage);

        ComboBox<LlmProvider> provider = new ComboBox<>(javafx.collections.FXCollections.observableArrayList(LlmProvider.values()));
        provider.setValue(config.provider());
        TextField baseUrl = new TextField(config.resolvedBaseUrl());
        ComboBox<String> model = new ComboBox<>();
        model.setEditable(true);
        model.setPrefWidth(240);
        model.setValue(config.resolvedModel());
        PasswordField apiKey = new PasswordField();
        apiKey.setText(config.apiKey == null ? "" : config.apiKey);
        apiKey.setPromptText("Read from env if left blank");
        TextField apiVersion = new TextField(config.apiVersion);
        TextField timeout = new TextField(String.valueOf(config.requestTimeoutSeconds));
        TextField temperature = new TextField(String.valueOf(config.temperature));
        TextField maxTokens = new TextField(String.valueOf(config.maxTokens));
        CheckBox enabled = new CheckBox("Enable LLM summaries (falls back to a template when unavailable)");
        enabled.setSelected(config.llmEnabled);
        Label testResult = new Label();
        Button test = new Button("Test connection");
        Label modelResult = new Label();
        Button detectButton = new Button("Detect");
        TextField templatePath = new TextField(config.pptTemplatePath == null ? "" : config.pptTemplatePath);
        templatePath.setPromptText("Optional .pptx with {{title}}, {{summary}}, {{chart}}");
        templatePath.setPrefColumnCount(26);
        Button browseTemplate = new Button("Browse...");
        browseTemplate.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select PowerPoint template");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PowerPoint", "*.pptx"));
            File chosen = chooser.showOpenDialog(stage);
            if (chosen != null) {
                templatePath.setText(chosen.getAbsolutePath());
            }
        });

        provider.valueProperty().addListener((obs, old, val) -> {
            boolean azure = val == LlmProvider.AZURE_OPENAI;
            apiVersion.setDisable(!azure);
            model.setPromptText(azure ? "Deployment name" : "Model");
            boolean keyNeeded = val.requiresApiKey();
            apiKey.setDisable(!keyNeeded);
            apiKey.setPromptText(keyNeeded ? "Required (or set an env var)" : "Not required for local Ollama");
        });
        apiVersion.setDisable(config.provider() != LlmProvider.AZURE_OPENAI);
        apiKey.setDisable(!config.provider().requiresApiKey());
        apiKey.setPromptText(config.provider().requiresApiKey()
                ? "Required (or set an env var)" : "Not required for local Ollama");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(14));
        int r = 0;
        grid.add(enabled, 1, r++);
        grid.addRow(r++, new Label("Provider"), provider);
        grid.addRow(r++, new Label("Base URL"), baseUrl);
        grid.addRow(r++, new Label("Model / Deployment"), new HBox(6, model, detectButton, modelResult));
        grid.addRow(r++, new Label("API Key"), apiKey);
        grid.addRow(r++, new Label("API version (Azure)"), apiVersion);
        grid.addRow(r++, new Label("Timeout (s)"), timeout);
        grid.addRow(r++, new Label("Temperature"), temperature);
        grid.addRow(r++, new Label("Max tokens"), maxTokens);
        grid.addRow(r++, new Label("PPT template"), new HBox(6, templatePath, browseTemplate));
        grid.add(new HBox(10, test, testResult), 1, r);
        grid.add(new Label("Tip: keys can be set via env vars (OPENAI_API_KEY, ANTHROPIC_API_KEY, ...)."),
                1, ++r);

        test.setOnAction(e -> {
            AppConfig probe = readConfigFromForm(provider, baseUrl, model, apiKey, apiVersion,
                    timeout, temperature, maxTokens, enabled, config);
            testResult.setText("Testing...");
            Thread thread = new Thread(() -> {
                String message;
                try {
                    String reply = LlmClientFactory.create(probe).complete(
                            "You are a connectivity test.", "Reply with the single word: OK");
                    message = "OK: " + (reply.length() > 40 ? reply.substring(0, 40) + "..." : reply);
                } catch (RuntimeException ex) {
                    message = "Failed: " + ex.getMessage();
                }
                String finalMessage = message;
                javafx.application.Platform.runLater(() -> testResult.setText(finalMessage));
            }, "llm-test");
            thread.setDaemon(true);
            thread.start();
        });

        detectButton.setOnAction(e -> detectModels(
                readConfigFromForm(provider, baseUrl, model, apiKey, apiVersion,
                        timeout, temperature, maxTokens, enabled, config),
                model, modelResult, detectButton, false));

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Auto-detect available models (local or external) when the dialog opens.
        detectModels(readConfigFromForm(provider, baseUrl, model, apiKey, apiVersion,
                timeout, temperature, maxTokens, enabled, config),
                model, modelResult, detectButton, true);
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            AppConfig updated = readConfigFromForm(provider, baseUrl, model, apiKey, apiVersion,
                    timeout, temperature, maxTokens, enabled, config);
            config.llmProvider = updated.llmProvider;
            config.llmEnabled = updated.llmEnabled;
            config.baseUrl = updated.baseUrl;
            config.model = updated.model;
            config.apiKey = updated.apiKey;
            config.apiVersion = updated.apiVersion;
            config.requestTimeoutSeconds = updated.requestTimeoutSeconds;
            config.temperature = updated.temperature;
            config.maxTokens = updated.maxTokens;
            config.pptTemplatePath = templatePath.getText().trim();
            config.save();
            this.service = new ProjectService(LlmClientFactory.create(config), config.pptTemplatePath);
            status.setText("Settings saved. LLM: " + llmDescription());
            log.info("Settings saved: provider={}, model={}, timeoutS={}, llmEnabled={}",
                    config.provider().label(), config.resolvedModel(),
                    config.requestTimeoutSeconds, config.llmEnabled);
        }
    }

    private static AppConfig readConfigFromForm(ComboBox<LlmProvider> provider, TextField baseUrl,
                                                ComboBox<String> model, PasswordField apiKey, TextField apiVersion,
                                                TextField timeout, TextField temperature, TextField maxTokens,
                                                CheckBox enabled, AppConfig template) {
        AppConfig cfg = new AppConfig();
        cfg.llmProvider = provider.getValue() == null ? "ollama" : provider.getValue().id();
        cfg.llmEnabled = enabled.isSelected();
        cfg.baseUrl = baseUrl.getText().trim();
        cfg.model = comboText(model);
        cfg.apiKey = apiKey.getText().trim();
        cfg.apiVersion = apiVersion.getText().trim();
        cfg.requestTimeoutSeconds = parseInt(timeout.getText(), template.requestTimeoutSeconds);
        cfg.temperature = parseDouble(temperature.getText(), template.temperature);
        cfg.maxTokens = parseInt(maxTokens.getText(), template.maxTokens);
        return cfg;
    }

    private void detectModels(AppConfig probe, ComboBox<String> model, Label result,
                              Button trigger, boolean silent) {
        trigger.setDisable(true);
        if (!silent) {
            result.setText("Detecting...");
        }
        Thread thread = new Thread(() -> {
            List<String> found = List.of();
            String message = null;
            try {
                found = LlmClientFactory.detectModels(probe);
                message = found.isEmpty() ? (silent ? null : "No models reported.")
                        : found.size() + " model(s) found";
            } catch (RuntimeException ex) {
                message = silent ? null : "Failed: " + ex.getMessage();
            }
            List<String> models = found;
            String finalMessage = message;
            javafx.application.Platform.runLater(() -> {
                trigger.setDisable(false);
                if (!models.isEmpty()) {
                    String current = comboText(model);
                    model.getItems().setAll(models);
                    model.setValue(models.contains(current) ? current : models.get(0));
                }
                if (finalMessage != null) {
                    result.setText(finalMessage);
                }
            });
        }, "llm-detect");
        thread.setDaemon(true);
        thread.start();
    }

    private static String comboText(ComboBox<String> combo) {
        String editor = combo.getEditor().getText();
        if (editor != null && !editor.isBlank()) {
            return editor.trim();
        }
        return combo.getValue() == null ? "" : combo.getValue().trim();
    }

    private String llmDescription() {
        if (!config.llmEnabled) {
            return "disabled (template fallback)";
        }
        String model = config.resolvedModel();
        return config.provider().label() + (model == null || model.isBlank() ? "" : " - " + model);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value.trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    // ------------------------------------------------------------- utilities

    private <T> void runBackground(String message, ProgressWork<T> work, Consumer<T> onSuccess) {
        loader.show(message);
        status.setText(message);
        javafx.concurrent.Task<T> task = new javafx.concurrent.Task<>() {
            @Override
            protected T call() throws Exception {
                updateProgress(0, 100);
                updateMessage(message);
                return work.call((fraction, msg) -> {
                    updateProgress((long) Math.round(clamp(fraction) * 100), 100);
                    if (msg != null) {
                        updateMessage(msg);
                    }
                });
            }
        };
        task.progressProperty().addListener((obs, old, val) -> loader.setProgress(val.doubleValue()));
        task.messageProperty().addListener((obs, old, val) -> {
            loader.setMessage(val);
            status.setText(val);
        });
        task.setOnSucceeded((WorkerStateEvent e) -> {
            loader.hide();
            onSuccess.accept(task.getValue());
            log.debug("Background task '{}' completed successfully", message);
        });
        task.setOnFailed((WorkerStateEvent e) -> {
            loader.hide();
            Throwable ex = task.getException();
            log.error("Background task failed", ex);
            status.setText("Error: " + (ex == null ? "unknown" : ex.getMessage()));
            warn(ex == null ? "Unknown error" : ex.getMessage());
        });
        Thread thread = new Thread(task, "taskmanager-worker");
        thread.setDaemon(true);
        thread.start();
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    @FunctionalInterface
    private interface ProgressWork<T> {
        T call(ProgressListener progress) throws Exception;
    }

    private void warn(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message, ButtonType.OK);
        alert.initOwner(stage);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
