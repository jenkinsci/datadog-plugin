package org.datadog.jenkins.plugins.datadog.flare;

import hudson.Extension;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Extension
public class ThreadDumpFlare implements FlareContributor {

    @Override
    public String getFilename() {
        return "thread-dump.txt";
    }

    @Override
    public void writeFileContents(OutputStream out) {
        // Print writer is not closed intentionally, to avoid closing out.
        // Auto-flush set to true ensures everything is witten
        PrintWriter printWriter = new PrintWriter(out, true, StandardCharsets.UTF_8);

        Map<Thread, StackTraceElement[]> allStackTraces = Thread.getAllStackTraces();
        for (Map.Entry<Thread, StackTraceElement[]> entry : allStackTraces.entrySet()) {
            Thread thread = entry.getKey();
            StackTraceElement[] stackTrace = entry.getValue();

            printWriter.println("Thread: " + thread.getName());
            for (StackTraceElement element : stackTrace) {
                printWriter.println("    at " + element);
            }
            printWriter.println();
        }
    }
}
