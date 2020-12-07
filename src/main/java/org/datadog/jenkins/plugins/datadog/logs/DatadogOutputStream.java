/*
The MIT License

Copyright (c) 2015-Present Datadog, Inc <opensource@datadoghq.com>
All rights reserved.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
 */

package org.datadog.jenkins.plugins.datadog.logs;

import hudson.console.ConsoleNote;
import hudson.console.LineTransformationOutputStream;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import org.jenkinsci.plugins.credentialsbinding.masking.SecretPatterns;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DatadogOutputStream extends LineTransformationOutputStream {
    private OutputStream delegate;
    private DatadogWriter writer;
    private Run<?, ?> run;
    private Pattern p;

    private final static Map<AbstractBuild<?, ?>, Collection<String>> secretsForBuild = new WeakHashMap<>();

    public DatadogOutputStream(OutputStream delegate, DatadogWriter writer) {
        super();
        this.delegate = delegate;
        this.writer = writer;
    }

    public DatadogOutputStream(OutputStream delegate, DatadogWriter writer, Run<?, ?> run) {
        super();
        this.delegate = delegate;
        this.writer = writer;
        this.run = run;
    }

    public static Pattern getPatternForBuild(AbstractBuild<?, ?> build) {
        if (secretsForBuild.containsKey(build)) {
            return SecretPatterns.getAggregateSecretPattern(secretsForBuild.get(build));
        } else {
            return null;
        }
    }

    @Override
    protected void eol(byte[] b, int len) throws IOException {
        delegate.write(b, 0, len);
        this.flush();

        if (p == null) {
            p = getPatternForBuild((AbstractBuild<?, ?>) run);
        }

        String line = new String(b, 0, len, writer.getCharset());
        line = ConsoleNote.removeNotes(line).trim();

        String filteredLine = filterCredentials(line);
        writer.write(filteredLine);
    }

    private String filterCredentials(String line) {
        Matcher m = p.matcher(line);

        if (m.find()) {
            line = m.replaceAll("****");
        }

        return line;
    }

    @Override
    public void flush() throws IOException {
        delegate.flush();
        super.flush();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
        super.close();
    }
}
