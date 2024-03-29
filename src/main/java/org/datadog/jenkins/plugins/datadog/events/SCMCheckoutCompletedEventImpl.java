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

package org.datadog.jenkins.plugins.datadog.events;

import org.datadog.jenkins.plugins.datadog.model.BuildData;

public class SCMCheckoutCompletedEventImpl extends AbstractDatadogBuildEvent {

    public static final String SCM_CHECKOUT_COMPLETED_EVENT_NAME = "SCMCheckout";

    public SCMCheckoutCompletedEventImpl(BuildData buildData) {
        super(buildData);

        String buildNumber = buildData.getBuildNumber("unknown");
        String jobName = buildData.getJobName();
        String buildUrl = buildData.getBuildUrl("unknown");
        String branch = buildData.getBranch("unknown");
        String nodeName = buildData.getNodeName("unknown");

        // Build title
        // eg: `job_name build #1 checkout finished hostname`
        String title = "Job " + jobName + " build #" + buildNumber + " checkout finished on " + super.getHost();
        setTitle(title);

        // Build Text
        // eg: `[Job <jobName> with build number #<buildNumber>] checkout successfully (1sec)`
        String text = "%%% \n[Job " + jobName + " build #" + buildNumber + "](" + buildUrl +
                ") checkout (branch: " + branch + " ) finished successfully on host " + super.getHost() +
                " (node: " + nodeName + " )" + getFormattedDuration() +
                "\n" + super.getLocationDetails() + " \n%%%";
        setText(text);

        setPriority(Priority.LOW);
        setAlertType(AlertType.SUCCESS);
    }
}
