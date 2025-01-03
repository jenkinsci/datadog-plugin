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

import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.datadog.jenkins.plugins.datadog.util.AsyncWriter;
import org.datadog.jenkins.plugins.datadog.DatadogUtilities;
import org.datadog.jenkins.plugins.datadog.model.BuildData;
import org.datadog.jenkins.plugins.datadog.model.PipelineStepData;
import org.datadog.jenkins.plugins.datadog.traces.CITags;
import org.datadog.jenkins.plugins.datadog.util.TagsUtil;

import java.nio.charset.Charset;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

public class DatadogWriter {

    private static final Logger logger = Logger.getLogger(DatadogWriter.class.getName());

    private final Charset charset;
    private final BuildData buildData;

    public DatadogWriter(@Nonnull BuildData buildData) {
        this.charset = buildData.getCharset();
        this.buildData = buildData;
    }

    public Charset getCharset() {
        return charset;
    }

    public void write(String line) {
        try {
            if (!StringUtils.isNotEmpty(line)) {
                return;
            }

            JSONObject payload = this.buildData.addLogAttributes();

            Map<String, Set<String>> ddtags = this.buildData.getTags();
            TagsUtil.addTagToTags(ddtags, "datadog.product", "cipipeline");
            payload.put("ddtags", String.join(",", TagsUtil.convertTagsToArray(ddtags)));
            payload.put("message", line);
            payload.put("ddsource", "jenkins");
            payload.put("service", "jenkins");
            payload.put("timestamp", System.currentTimeMillis());
            payload.put(PipelineStepData.StepType.PIPELINE.getTagName() + CITags._NAME, this.buildData.getJobName());

            AsyncWriter<JSONObject> logWriter = LogWriterFactory.getLogWriter();
            if (logWriter != null) {
                logWriter.submit(payload);
            }

        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, "Failed to submit log payload");
        }
    }

}
