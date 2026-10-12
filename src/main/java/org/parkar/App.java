package org.parkar;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.parkar.config.AppConfig;
import org.parkar.domain.DateRange;
import org.parkar.domain.Task;
import org.parkar.excel.ExcelImportService;
import org.parkar.excel.ImportResult;
import org.parkar.llm.LlmClient;
import org.parkar.llm.LlmClientFactory;
import org.parkar.service.DeveloperReport;
import org.parkar.service.ProjectService;
import org.parkar.ui.LoadingOverlay;
import org.parkar.ui.MainView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Application entry point. Launches the JavaFX desktop UI by default, or runs a
 * headless one-shot export when invoked as {@code App --export <file.xlsx>}.
 */
public class App extends Application {

    private static final Logger log = LoggerFactory.getLogger(App.class);

    private AppConfig config;
    /**
     * Set once the user confirms exit, so the second close request passes through.
     */
    private boolean allowClose;

    @Override
    public void start(Stage stage) {
        log.info("Starting Task Manager UI");
        stage.setOnCloseRequest(e -> onCloseRequested(e, stage));

        LoadingOverlay splash = new LoadingOverlay();
        StackPane splashRoot = new StackPane(splash);
        splashRoot.setStyle("-fx-background-color: linear-gradient(to bottom right, #1565c0, #1e88e5);");
        splash.show("Starting Task Manager...");
        stage.setTitle("TaskManager - Developer Reporting");
        stage.setScene(new Scene(splashRoot, 460, 260));
        stage.show();

        PauseTransition startDelay = new PauseTransition(Duration.millis(400));
        startDelay.setOnFinished(e -> {
            splash.setProgress(0.2);
            splash.setMessage("Loading configuration...");
            Platform.runLater(() -> {
                config = AppConfig.load();
                log.info("Configuration loaded: provider={}, llmEnabled={}", config.provider().label(), config.llmEnabled);
                splash.setProgress(0.5);
                splash.setMessage("Initializing services...");
                Platform.runLater(() -> {
                    MainView view = new MainView(stage, config);
                    splash.setProgress(0.75);
                    splash.setMessage("Building interface...");
                    Platform.runLater(() -> {
                        Scene scene = new Scene(view.root(), 1240, 780);
                        splash.setProgress(1.0);
                        splash.setMessage("Ready");
                        log.info("Main interface built (1240x780), showing window");
                        PauseTransition finish = new PauseTransition(Duration.millis(250));
                        finish.setOnFinished(x -> stage.setScene(scene));
                        finish.play();
                    });
                });
            });
        });
        startDelay.play();
    }

    /**
     * Intercepts the window close request: shows an OK / Cancel confirmation.
     * OK saves the application state (config) and lets the window close; Cancel
     * dismisses the popup and returns control to the running application.
     */
    private void onCloseRequested(javafx.event.Event event, Stage stage) {
        if (allowClose) {
            return;
        }
        event.consume();
        log.info("Window close requested; showing confirmation dialog");

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Exit Task Manager");
        alert.setHeaderText(null);
        alert.setContentText("Save application state and exit?");
        alert.initOwner(stage);

        ButtonType ok = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(ok, cancel);

        ButtonType choice = alert.showAndWait().orElse(cancel);
        if (choice == ok) {
            log.info("User confirmed exit; saving state and closing");
            saveState();
            allowClose = true;
            stage.close();
        } else {
            log.info("User cancelled exit; returning to the application");
        }
    }

    /**
     * Persists the current application configuration.
     */
    private void saveState() {
        if (config == null) {
            config = new AppConfig();
        }
        config.save();
        log.info("Application state saved to {}", AppConfig.defaultLocation());
    }

    public static void main(String[] args) {
        if (args.length > 0 && "--export".equals(args[0])) {
            System.exit(runHeadless(args));
        }
        launch(args);
    }

    private static int runHeadless(String[] args) {
        try {
            String inputArg = null;
            String outputArg = null;
            String templateArg = null;
            boolean noLlm = false;
            for (int i = 1; i < args.length; i++) {
                switch (args[i]) {
                    case "--no-llm" -> noLlm = true;
                    case "--template" -> {
                        if (i + 1 < args.length) {
                            templateArg = args[++i];
                        }
                    }
                    default -> {
                        if (inputArg == null) {
                            inputArg = args[i];
                        } else if (outputArg == null) {
                            outputArg = args[i];
                        }
                    }
                }
            }
            if (inputArg == null) {
                log.error("Usage: --export <input.xlsx> [output.pptx] [--template template.pptx] [--no-llm]");
                return 2;
            }
            Path input = Path.of(inputArg);

            AppConfig config = AppConfig.load();
            config.llmEnabled = !noLlm && config.llmEnabled;
            if (templateArg != null) {
                config.pptTemplatePath = templateArg;
            }

            if (config.llmEnabled && (config.model == null || config.model.isBlank())) {
                try {
                    List<String> detected = LlmClientFactory.detectModels(config);
                    if (!detected.isEmpty()) {
                        config.model = detected.get(0);
                        log.info("Auto-detected LLM model '{}' from {}", config.model, config.provider().label());
                    }
                } catch (RuntimeException e) {
                    log.warn("LLM model auto-detection failed: {}", e.getMessage());
                }
            }

            Path output = outputArg != null ? Path.of(outputArg) : defaultOutput(config);
            log.info("Headless export: input={}, output={}, noLlm={}", input, output, !config.llmEnabled);

            ImportResult result = new ExcelImportService().importTasks(input);
            List<Task> tasks = result.tasks();
            DateRange range = DateRange.covering(tasks);
            log.info("Imported {} task(s) from {}, date range {} to {}",
                    tasks.size(), input, range.start(), range.end());

            LlmClient client = LlmClientFactory.create(config);
            ProjectService service = new ProjectService(client, config.pptTemplatePath);
            log.info("Initialized {} and ProjectService", client.provider());

            List<DeveloperReport> reports = new ArrayList<>();
            for (String dev : service.developers(tasks)) {
                log.debug("Building headless report for developer '{}'", dev);
                reports.add(service.buildReport(dev, service.tasksFor(tasks, dev), range, true));
            }
            Path written = service.export(output, "Developer Task Report", reports);
            log.info("Headless export complete: {} ({} tasks, {} developers, range {} to {})",
                    written, tasks.size(), reports.size(), range.start(), range.end());
            return 0;
        } catch (Exception e) {
            log.error("Headless export failed", e);
            return 1;
        }
    }

    private static Path defaultOutput(AppConfig config) {
        String dir = config.outputDirectory == null || config.outputDirectory.isBlank()
                ? "output" : config.outputDirectory;
        return Path.of(dir, "task-report.pptx");
    }
}
