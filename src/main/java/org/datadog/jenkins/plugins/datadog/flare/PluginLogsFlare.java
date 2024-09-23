package org.datadog.jenkins.plugins.datadog.flare;

import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.logging.LogRecorder;
import hudson.logging.LogRecorderManager;
import io.jenkins.lib.support_log_formatter.SupportLogFormatter;
import jenkins.model.Jenkins;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

@Extension
public class PluginLogsFlare implements FlareContributor {

    private static final Logger LOGGER = Logger.getLogger(PluginLogsFlare.class.getName());

    private static final Formatter LOG_FORMATTER = new SupportLogFormatter();

    private static final String DATADOG_PLUGIN_LOGS = "Datadog Plugin Logs";

    private static final String PLUGIN_LOG_RECORDER_ENABLED_ENV_VAR = "DD_JENKINS_PLUGIN_LOG_RECORDER_ENABLED";

    @Initializer(after = InitMilestone.SYSTEM_CONFIG_LOADED)
    public void onStartup() {
        String pluginLogRecorderEnabled = System.getenv(PLUGIN_LOG_RECORDER_ENABLED_ENV_VAR);
        if (pluginLogRecorderEnabled != null && !Boolean.parseBoolean(pluginLogRecorderEnabled)) {
            return;
        }

        try {
            LogRecorderManager logRecorderManager = Jenkins.get().getLog();
            LogRecorder logRecorder = logRecorderManager.getLogRecorder(DATADOG_PLUGIN_LOGS);
            if (logRecorder == null) {
                logRecorderManager.doNewLogRecorder(DATADOG_PLUGIN_LOGS);
                logRecorder = logRecorderManager.getLogRecorder(DATADOG_PLUGIN_LOGS);
            }

            LogRecorder.Target target = new LogRecorder.Target("org.datadog", Level.INFO);
            logRecorder.setLoggers(Collections.singletonList(target));
            logRecorder.save();
        } catch (Exception e) {
            DatadogUtilities.severe(LOGGER, e, "Could not register Datadog plugin log recorder");
        }
    }

    @Override
    public String getFilename() {
        return "plugin.log";
    }

    @Override
    public void writeFileContents(OutputStream out) {
        // Print writer is not closed intentionally, to avoid closing out.
        // Auto-flush set to true ensures everything is witten
        PrintWriter printWriter = new PrintWriter(out, true);

        Jenkins jenkins = Jenkins.get();
        LogRecorderManager logRecorderManager = jenkins.getLog();
        LogRecorder logRecorder = logRecorderManager.getLogRecorder(DATADOG_PLUGIN_LOGS);
        if (logRecorder == null) {
            printWriter.println("Log recorder " + DATADOG_PLUGIN_LOGS + " not found.");
            return;
        }

        List<LogRecord> logRecords = logRecorder.getLogRecords();

        ListIterator<LogRecord> it = logRecords.listIterator(logRecords.size());
        while (it.hasPrevious()) {
            LogRecord logRecord = it.previous();
            String formatted = LOG_FORMATTER.format(logRecord);
            printWriter.print(formatted);
        }
    }
}
