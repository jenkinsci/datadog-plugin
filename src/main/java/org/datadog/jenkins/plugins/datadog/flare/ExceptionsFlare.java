package org.datadog.jenkins.plugins.datadog.flare;

import hudson.Extension;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.BlockingQueue;

@Extension
public class ExceptionsFlare implements FlareContributor {

    @Override
    public int order() {
        return 4;
    }

    @Override
    public String getDescription() {
        return "Recent exceptions";
    }

    @Override
    public String getFilename() {
        return "exceptions.txt";
    }

    @Override
    public void writeFileContents(OutputStream out) {
        // Print writer is not closed intentionally, to avoid closing out.
        // Auto-flush set to true ensures everything is witten
        PrintWriter printWriter = new PrintWriter(out, false, StandardCharsets.UTF_8);

        DateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z");
        BlockingQueue<Pair<Date, Throwable>> exceptionsBuffer = DatadogUtilities.getExceptionsBuffer();
        for (Pair<Date, Throwable> p : exceptionsBuffer) {
            Date date = p.getKey();
            Throwable exception = p.getValue();
            printWriter.println(dateFormatter.format(date) + ": " + ExceptionUtils.getStackTrace(exception));
        }

        printWriter.flush();
    }
}
