package org.datadog.jenkins.plugins.datadog.flare;

import hudson.Extension;
import io.jenkins.lib.support_log_formatter.SupportLogFormatter;
import jenkins.model.Jenkins;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.List;
import java.util.ListIterator;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * This flare writes only the last small part of the Jenkins controller logs
 * that is still available in the in-memory ring-buffer.
 * The full logs that are stored on disk are not written intentionally.
 * They're available in the support bundles generated with Cloudbees or Support Core plugins.
 */
@Extension
public class JenkinsLogsFlare implements FlareContributor {

    private static final Formatter LOG_FORMATTER = new SupportLogFormatter();

    @Override
    public String getFilename() {
        return "jenkins.log";
    }

    @Override
    public void writeFileContents(OutputStream out) {
        // Print writer is not closed intentionally, to avoid closing out.
        // Auto-flush set to true ensures everything is witten
        PrintWriter printWriter = new PrintWriter(out, true);

        List<LogRecord> logRecords = Jenkins.logRecords;
        ListIterator<LogRecord> it = logRecords.listIterator(logRecords.size());
        while (it.hasPrevious()) {
            LogRecord logRecord = it.previous();
            String formatted = LOG_FORMATTER.format(logRecord);
            printWriter.print(formatted);
        }
    }
}
