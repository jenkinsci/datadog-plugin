package org.datadog.jenkins.plugins.datadog.flare;

import hudson.Extension;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.BlockingQueue;

@Extension
public class ExceptionsFlare implements FlareContributor {

    public static final DateFormat DATE_FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z");

    @Override
    public String getFilename() {
        return "exceptions.txt";
    }

    @Override
    public void writeFileContents(OutputStream out) {
        // Print writer is not closed intentionally, to avoid closing out.
        // Auto-flush set to true ensures everything is witten
        PrintWriter printWriter = new PrintWriter(out, true);

        BlockingQueue<Pair<Date, Throwable>> exceptionsBuffer = DatadogUtilities.getExceptionsBuffer();
        for (Pair<Date, Throwable> p : exceptionsBuffer) {
            Date date = p.getKey();
            Throwable exception = p.getValue();
            printWriter.println(DATE_FORMATTER.format(date) + ": " + ExceptionUtils.getStackTrace(exception));
        }
    }
}
