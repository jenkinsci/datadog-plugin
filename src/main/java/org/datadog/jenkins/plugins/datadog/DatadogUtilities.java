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

package org.datadog.jenkins.plugins.datadog;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.*;
import hudson.model.*;
import hudson.model.labels.LabelAtom;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.datadog.jenkins.plugins.datadog.apm.ShellCommandCallable;
import org.datadog.jenkins.plugins.datadog.clients.HttpClient;
import org.datadog.jenkins.plugins.datadog.model.DatadogPluginAction;
import org.datadog.jenkins.plugins.datadog.steps.DatadogPipelineAction;
import org.datadog.jenkins.plugins.datadog.traces.CITags;
import org.datadog.jenkins.plugins.datadog.util.SuppressFBWarnings;
import org.datadog.jenkins.plugins.datadog.util.TagsUtil;
import org.jenkinsci.plugins.pipeline.StageStatus;
import org.jenkinsci.plugins.workflow.actions.*;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.*;
import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DatadogUtilities {

    private static final Logger logger = Logger.getLogger(DatadogUtilities.class.getName());

    private static final Integer MAX_HOSTNAME_LEN = 255;
    private static final int HOSTNAME_CMD_TIMEOUT_MILLIS = 3_000;
    private static final String DATE_FORMAT_ISO8601 = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";
    private static final List<String> UNIX_OS = Arrays.asList("mac", "linux", "freebsd", "sunos");

    /**
     * @return - The descriptor for the Datadog plugin. In this case the global configuration.
     */
    public static DatadogGlobalConfiguration getDatadogGlobalDescriptor() {
        try {
            return ExtensionList.lookupSingleton(DatadogGlobalConfiguration.class);
        } catch (Exception e) {
            // It can only throw such an exception when running tests
            return null;
        }
    }

    /**
     * @param r - Current build.
     * @return - The configured {@link DatadogJobProperty}. Null if not there
     */
    public static DatadogJobProperty getDatadogJobProperties(@Nonnull Run r) {
        return (DatadogJobProperty) r.getParent().getProperty(DatadogJobProperty.class);
    }

    /**
     * Builds extraTags if any are configured in the Job.
     *
     * @param run     - Current build
     * @param envVars - Environment Variables
     * @return A {@link HashMap} containing the key,value pairs of tags if any.
     */
    public static Map<String, Set<String>> getBuildTags(Run run, EnvVars envVars) {
        Map<String, Set<String>> result = new HashMap<>();
        if (run == null) {
            return result;
        }
        String jobName;
        jobName = run.getParent().getFullName();
        final DatadogGlobalConfiguration datadogGlobalConfig = getDatadogGlobalDescriptor();
        if (datadogGlobalConfig == null) {
            return result;
        }
        final String globalJobTags = datadogGlobalConfig.getGlobalJobTags();
        String workspaceTagFile = null;
        String tagProperties = null;
        final DatadogJobProperty property = DatadogUtilities.getDatadogJobProperties(run);
        if (property != null) {
            workspaceTagFile = property.readTagFile(run);
            tagProperties = property.getTagProperties();
        }

        // If job doesn't have a workspace Tag File set we check if one has been defined globally
        if (workspaceTagFile == null) {
            workspaceTagFile = datadogGlobalConfig.getGlobalTagFile();
        }
        if (workspaceTagFile != null) {
            result = TagsUtil.merge(result, computeTagListFromVarList(envVars, workspaceTagFile));
        }
        result = TagsUtil.merge(result, computeTagListFromVarList(envVars, tagProperties));

        result = TagsUtil.merge(result, getTagsFromGlobalJobTags(jobName, globalJobTags));

        result = TagsUtil.merge(result, getTagsFromPipelineAction(run));

        return result;
    }

    /**
     * Pipeline extraTags if any are configured in the Job from DatadogPipelineAction.
     *
     * @param run - Current build
     * @return A {@link Map} containing the key,value pairs of tags if any.
     */
    public static Map<String, Set<String>> getTagsFromPipelineAction(Run run) {
        // pipeline defined tags
        final Map<String, Set<String>> result = new HashMap<>();
        DatadogPipelineAction action = run.getAction(DatadogPipelineAction.class);
        if (action != null) {
            List<String> pipelineTags = action.getTags();
            for (String pipelineTag : pipelineTags) {
                String[] tagItem = pipelineTag.replaceAll(" ", "").split(":", 2);
                if (tagItem.length == 2) {
                    String tagName = tagItem[0];
                    String tagValue = tagItem[1];
                    Set<String> tagValues = result.computeIfAbsent(tagName, k -> new HashSet<>());
                    tagValues.add(tagValue.toLowerCase());
                } else if (tagItem.length == 1) {
                    String tagName = tagItem[0];
                    Set<String> tagValues = result.computeIfAbsent(tagName, k -> new HashSet<>());
                    tagValues.add(""); // no values
                } else {
                    logger.fine(String.format("Ignoring the tag %s. It is empty.", tagItem));
                }
            }
        }
        return result;
    }

    /**
     * Checks inclusion/exclusion filter settings to see if a run should be tracked by the plugin
     *
     * @param run The run to be checked
     * @return {@code true} if the run should be tracked by the plugin, {@code false} otherwise
     */
    public static boolean isJobTracked(Run<?,?> run) {
        return run != null && isJobTracked(run.getParent().getFullName());
    }

    /**
     * Checks if a jobName is excluded, included, or neither.
     *
     * @param jobName - A String containing the name of some job.
     * @return a boolean to signify if the jobName is or is not excluded or included.
     */
    public static boolean isJobTracked(final String jobName) {
        return jobName != null && !isJobExcluded(jobName) && isJobIncluded(jobName);
    }

    /**
     * Human-friendly OS name. Commons return values are windows, linux, mac, sunos, freebsd
     *
     * @return a String with a human-friendly OS name
     */
    private static String getOS() {
        String out = System.getProperty("os.name");
        String os = out.split(" ")[0];
        return os.toLowerCase();
    }

    /**
     * Retrieve the list of tags from the globalJobTagsLines param for jobName
     *
     * @param jobName       - JobName to retrieve and process tags from.
     * @param globalJobTags - globalJobTags string
     * @return - A Map of values containing the key and values of each Datadog tag to apply to the metric/event
     */
    private static Map<String, Set<String>> getTagsFromGlobalJobTags(String jobName, final String globalJobTags) {
        Map<String, Set<String>> tags = new HashMap<>();
        List<String> globalJobTagsLines = linesToList(globalJobTags);
        logger.fine(String.format("The list of Global Job Tags are: %s", globalJobTagsLines));

        // Each jobInfo is a list containing one regex, and a variable number of tags
        for (String globalTagsLine : globalJobTagsLines) {
            List<String> jobInfo = cstrToList(globalTagsLine);
            if (jobInfo.isEmpty()) {
                continue;
            }
            Pattern jobNamePattern = Pattern.compile(jobInfo.get(0));
            Matcher jobNameMatcher = jobNamePattern.matcher(jobName);
            if (jobNameMatcher.matches()) {
                for (int i = 1; i < jobInfo.size(); i++) {
                    String[] tagItem = jobInfo.get(i).replaceAll(" ", "").split(":", 2);
                    if (tagItem.length == 2) {
                        String tagName = tagItem[0];
                        String tagValue = tagItem[1];
                        // Fills regex group values from the regex job name to tag values
                        // eg: (.*?)-job, owner:$1 or (.*?)-job
                        // Also fills environment variables defined in the tag value.
                        // eg: (.*?)-job, custom_tag:$ENV_VAR
                        if (tagValue.startsWith("$")) {
                            try {
                                tagValue = jobNameMatcher.group(Character.getNumericValue(tagValue.charAt(1)));
                            } catch (IndexOutOfBoundsException e) {

                                String tagNameEnvVar = tagValue.substring(1);
                                if (EnvVars.masterEnvVars.containsKey(tagNameEnvVar)) {
                                    tagValue = EnvVars.masterEnvVars.get(tagNameEnvVar);
                                } else {
                                    logger.fine(String.format(
                                            "Specified a capture group or environment variable that doesn't exist, not applying tag: %s Exception: %s",
                                            Arrays.toString(tagItem), e));
                                }
                            }
                        }
                        Set<String> tagValues = tags.containsKey(tagName) ? tags.get(tagName) : new HashSet<String>();
                        tagValues.add(tagValue.toLowerCase());
                        tags.put(tagName, tagValues);
                    } else if (tagItem.length == 1) {
                        String tagName = tagItem[0];
                        Set<String> tagValues = tags.containsKey(tagName) ? tags.get(tagName) : new HashSet<String>();
                        tagValues.add(""); // no values
                        tags.put(tagName, tagValues);
                    } else {
                        logger.fine(String.format("Ignoring the tag %s. It is empty.", tagItem));
                    }
                }
            }
        }

        return tags;
    }

    /**
     * Getter function for the globalTags global configuration, containing
     * a comma-separated list of tags that should be applied everywhere.
     *
     * @return a map containing the globalTags global configuration.
     */
    public static Map<String, Set<String>> getTagsFromGlobalTags() {
        Map<String, Set<String>> tags = new HashMap<>();

        final DatadogGlobalConfiguration datadogGlobalConfig = getDatadogGlobalDescriptor();
        if (datadogGlobalConfig == null) {
            return tags;
        }

        final String globalTags = datadogGlobalConfig.getGlobalTags();
        List<String> globalTagsLines = DatadogUtilities.linesToList(globalTags);

        for (String globalTagsLine : globalTagsLines) {
            List<String> tagList = DatadogUtilities.cstrToList(globalTagsLine);
            if (tagList.isEmpty()) {
                continue;
            }

            for (int i = 0; i < tagList.size(); i++) {
                String[] tagItem = tagList.get(i).replaceAll(" ", "").split(":", 2);
                if (tagItem.length == 2) {
                    String tagName = tagItem[0];
                    String tagValue = tagItem[1];
                    Set<String> tagValues = tags.containsKey(tagName) ? tags.get(tagName) : new HashSet<String>();
                    // Apply environment variables if specified. ie (custom_tag:$ENV_VAR)
                    if (tagValue.startsWith("$") && EnvVars.masterEnvVars.containsKey(tagValue.substring(1))) {
                        tagValue = EnvVars.masterEnvVars.get(tagValue.substring(1));
                    } else {
                        logger.fine(String.format(
                                "Specified an environment variable that doesn't exist, not applying tag: %s",
                                Arrays.toString(tagItem)));
                    }
                    tagValues.add(tagValue.toLowerCase());
                    tags.put(tagName, tagValues);
                } else if (tagItem.length == 1) {
                    String tagName = tagItem[0];
                    Set<String> tagValues = tags.containsKey(tagName) ? tags.get(tagName) : new HashSet<String>();
                    tagValues.add(""); // no values
                    tags.put(tagName, tagValues);
                } else {
                    logger.fine(String.format("Ignoring the tag %s. It is empty.", tagItem));
                }
            }
        }

        return tags;
    }

    /**
     * Checks if a jobName is excluded.
     *
     * @param jobName - A String containing the name of some job.
     * @return a boolean to signify if the jobName is or is not excluded.
     */
    private static boolean isJobExcluded(@Nonnull final String jobName) {
        final DatadogGlobalConfiguration datadogGlobalConfig = getDatadogGlobalDescriptor();
        if (datadogGlobalConfig == null) {
            return false;
        }
        final String excludedProp = datadogGlobalConfig.getExcluded();
        List<String> excluded = cstrToList(excludedProp);
        for (String excludedJob : excluded) {
            Pattern excludedJobPattern = Pattern.compile(excludedJob);
            Matcher jobNameMatcher = excludedJobPattern.matcher(jobName);
            if (jobNameMatcher.matches()) {
                return true;
            }
        }
        return false;

    }

    /**
     * Checks if a jobName is included.
     *
     * @param jobName - A String containing the name of some job.
     * @return a boolean to signify if the jobName is or is not included.
     */
    private static boolean isJobIncluded(@Nonnull final String jobName) {
        final DatadogGlobalConfiguration datadogGlobalConfig = getDatadogGlobalDescriptor();
        if (datadogGlobalConfig == null) {
            return true;
        }
        final String includedProp = datadogGlobalConfig.getIncluded();
        final List<String> included = cstrToList(includedProp);
        for (String includedJob : included) {
            Pattern includedJobPattern = Pattern.compile(includedJob);
            Matcher jobNameMatcher = includedJobPattern.matcher(jobName);
            if (jobNameMatcher.matches()) {
                return true;
            }
        }
        return included.isEmpty();
    }

    /**
     * Converts a Comma Separated List into a List Object
     *
     * @param str - A String containing a comma separated list of items.
     * @return a String List with all items transform with trim and lower case
     */
    public static List<String> cstrToList(final String str) {
        return convertRegexStringToList(str, ",");
    }

    /**
     * Converts a string List into a List Object
     *
     * @param str - A String containing a comma separated list of items.
     * @return a String List with all items
     */
    public static List<String> linesToList(final String str) {
        return convertRegexStringToList(str, "\\r?\\n");
    }

    /**
     * Converts a string List into a List Object
     *
     * @param str   - A String containing a comma separated list of items.
     * @param regex - Regex to use to split the string list
     * @return a String List with all items
     */
    private static List<String> convertRegexStringToList(final String str, String regex) {
        List<String> result = new ArrayList<>();
        if (str != null && str.length() != 0) {
            for (String item : str.trim().split(regex)) {
                if (!item.isEmpty()) {
                    result.add(item.trim());
                }
            }
        }
        return result;
    }

    public static Map<String, Set<String>> computeTagListFromVarList(EnvVars envVars, final String varList) {
        HashMap<String, Set<String>> result = new HashMap<>();
        List<String> rawTagList = linesToList(varList);
        for (String tagLine : rawTagList) {
            List<String> tagList = DatadogUtilities.cstrToList(tagLine);
            if (tagList.isEmpty()) {
                continue;
            }
            for (int i = 0; i < tagList.size(); i++) {
                String tag = tagList.get(i).replaceAll(" ", "");
                String[] expanded = envVars.expand(tag).split("=", 2);
                if (expanded.length == 2) {
                    String name = expanded[0];
                    String value = expanded[1];
                    Set<String> values = result.containsKey(name) ? result.get(name) : new HashSet<String>();
                    values.add(value);
                    result.put(name, values);
                    logger.fine(String.format("Emitted tag %s:%s", name, value));
                } else if (expanded.length == 1) {
                    String name = expanded[0];
                    Set<String> values = result.containsKey(name) ? result.get(name) : new HashSet<String>();
                    values.add(""); // no values
                    result.put(name, values);
                } else {
                    logger.fine(String.format("Ignoring the tag %s. It is empty.", tag));
                }
            }
        }
        return result;
    }

    public static String getAwsInstanceID() throws IOException {
        String metadataUrl = "http://169.254.169.254/latest/meta-data/instance-id";
        HttpClient client = null;
        // Make request
        try {
            client = new HttpClient(60_000);
            String instanceId = client.get(metadataUrl, Collections.emptyMap(), Function.identity());
            logger.fine("Instance ID detected: " + instanceId);
            return instanceId;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            DatadogUtilities.severe(logger, e, "Could not retrieve the AWS instance ID");
            return null;
        } catch (Exception e) {
            DatadogUtilities.severe(logger, e, "Could not retrieve the AWS instance ID");
            return null;
        }
    }

    /**
     * Getter function to return either the saved hostname global configuration,
     * or the hostname that is set in the Jenkins host itself. Returns null if no
     * valid hostname is found.
     * <p>
     * Tries, in order:
     * Jenkins configuration
     * Jenkins hostname environment variable
     * AWS instance ID, if enabled
     * System hostname environment variable
     * Unix hostname via `/bin/hostname -f`
     * Localhost hostname
     *
     * @param envVars - The Jenkins environment variables
     * @return a human readable String for the hostname.
     */
    public static String getHostname(EnvVars envVars) {
        // Check hostname configuration from Jenkins
        String hostname = null;
        DatadogGlobalConfiguration datadogConfiguration = getDatadogGlobalDescriptor();
        if (datadogConfiguration != null) {
            hostname = datadogConfiguration.getHostname();
        }

        if (isValidHostname(hostname)) {
            logger.fine("Using hostname set in 'Manage Plugins'. Hostname: " + hostname);
            return hostname;
        }

        final DatadogGlobalConfiguration datadogGlobalConfig = getDatadogGlobalDescriptor();
        if (datadogGlobalConfig != null) {
            if (datadogGlobalConfig.isUseAwsInstanceHostname()) {
                try {
                    logger.fine("Attempting to resolve AWS instance ID for hostname");
                    hostname = getAwsInstanceID();
                } catch (IOException e) {
                    logger.fine("Error retrieving AWS hostname: " + e);
                }
                if (hostname != null) {
                    logger.fine("Using AWS instance ID as hostname. Hostname: " + hostname);
                    return hostname;
                }
            }
        }

        // Check hostname using jenkins env variables
        if (envVars != null) {
            hostname = envVars.get("HOSTNAME");
            if (isValidHostname(hostname)) {
                logger.fine("Using hostname found in $HOSTNAME agent environment variable. Hostname: " + hostname);
                return hostname;
            }
        }

        hostname = System.getenv("HOSTNAME");
        if (isValidHostname(hostname)) {
            logger.fine("Using hostname found in $HOSTNAME controller environment variable. Hostname: " + hostname);
            return hostname;
        }

        // Check OS specific unix commands
        String os = getOS();
        if (UNIX_OS.contains(os)) {
            // Attempt to grab unix hostname
            try {
                String[] cmd = {"/bin/hostname", "-f"};
                Process proc = Runtime.getRuntime().exec(cmd);
                InputStream in = proc.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, Charset.forName("UTF-8")));
                StringBuilder out = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    out.append(line);
                }
                reader.close();

                hostname = out.toString();
            } catch (Exception e) {
                logger.fine(String.format("Could not obtain UNIX hostname via /bin/hostname -f. Error: %s", e));
            }

            // Check hostname
            if (isValidHostname(hostname)) {
                logger.fine(String.format("Using unix hostname found via `/bin/hostname -f`. Hostname: %s",
                        hostname));
                return hostname;
            }
        }

        // Check localhost hostname
        try {
            hostname = Inet4Address.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            logger.fine(String.format("Unknown hostname error received for localhost. Error: %s", e));
        }
        if (isValidHostname(hostname)) {
            logger.fine(String.format("Using hostname found via "
                    + "Inet4Address.getLocalHost().getHostName()."
                    + " Hostname: %s", hostname));
            return hostname;
        }

        // Never found the hostname
        if (hostname == null || "".equals(hostname)) {
            logger.warning("Unable to reliably determine host name. You can define one in "
                    + "the 'Manage Plugins' section under the 'Datadog Plugin' section.");
        }

        return null;
    }

    private static final Pattern VALID_HOSTNAME_RFC_1123_PATTERN = Pattern.compile("^(([a-zA-Z0-9]|"
            + "[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*"
            + "([A-Za-z0-9]|"
            + "[A-Za-z0-9][A-Za-z0-9\\-]*[A-Za-z0-9])$");

    private static final Collection<String> LOCAL_HOSTS = Arrays.asList("localhost", "localhost.localdomain",
            "localhost6.localdomain6", "ip6-localhost");

    /**
     * Validator function to ensure that the hostname is valid. Also, fails on
     * empty String.
     *
     * @param hostname - A String object containing the name of a host.
     * @return a boolean representing the validity of the hostname
     */
    public static Boolean isValidHostname(String hostname) {
        if (hostname == null) {
            return false;
        }

        // Check if hostname is local
        if (LOCAL_HOSTS.contains(hostname.toLowerCase())) {
            logger.fine(String.format("Hostname: %s is local", hostname));
            return false;
        }

        if (isPrivateIPv4Address(hostname)) {
            logger.fine(String.format("Hostname: %s is a private IPv4 address", hostname));
            return false;
        }

        // Ensure proper length
        if (hostname.length() > MAX_HOSTNAME_LEN) {
            logger.fine(String.format("Hostname: %s is too long (max length is %s characters)",
                    hostname, MAX_HOSTNAME_LEN));
            return false;
        }

        // Final check: Hostname matches RFC1123?
        Matcher m = VALID_HOSTNAME_RFC_1123_PATTERN.matcher(hostname);
        return m.find();
    }

    static boolean isPrivateIPv4Address(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            return false;
        }

        String[] parts = ipAddress.split("\\.");
        if (parts.length != 4) {
            return false;
        }

        try {
            int firstOctet = Integer.parseInt(parts[0]);
            if (!isWithinIPv4OctetRange(firstOctet)) {
                return false;
            }
            int secondOctet = Integer.parseInt(parts[1]);
            if (!isWithinIPv4OctetRange(secondOctet)) {
                return false;
            }
            int thirdOctet = Integer.parseInt(parts[2]);
            if (!isWithinIPv4OctetRange(thirdOctet)) {
                return false;
            }
            int fourthOctet = Integer.parseInt(parts[3]);
            if (!isWithinIPv4OctetRange(fourthOctet)) {
                return false;
            }

            if (firstOctet == 10) {
                return true;
            } else if (firstOctet == 172 && (secondOctet >= 16 && secondOctet <= 31)) {
                return true;
            } else if (firstOctet == 192 && secondOctet == 168) {
                return true;
            }
            return false;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isWithinIPv4OctetRange(int number) {
        return number >= 0 && number <= 255;
    }

    public static Map<String, Set<String>> getComputerTags(Computer computer) {
        Set<LabelAtom> labels = null;
        if (computer == null) {
            logger.fine("Could not retrieve computer tags because computer is null");
            return Collections.emptyMap();
        }
        Node node = computer.getNode();
        if (node != null) {
            Set<LabelAtom> assignedLabels = node.getAssignedLabels();
            if (assignedLabels != null) {
                labels = node.getAssignedLabels();
            } else {
                logger.fine("Could not retrieve labels");
            }
        } else {
            logger.fine("Could not retrieve labels");
        }
        String nodeHostname = null;
        try {
            nodeHostname = computer.getHostName();
        } catch (IOException | InterruptedException e) {
            logger.fine("Could not retrieve hostname");
        }
        String nodeName = getNodeName(computer);
        Map<String, Set<String>> result = new HashMap<>();
        Set<String> nodeNameValues = new HashSet<>();
        nodeNameValues.add(nodeName);
        result.put("node_name", nodeNameValues);
        if (nodeHostname != null) {
            Set<String> nodeHostnameValues = new HashSet<>();
            nodeHostnameValues.add(nodeHostname);
            result.put("node_hostname", nodeHostnameValues);
        }
        if (labels != null) {
            Set<String> nodeLabelsValues = new HashSet<>();
            for (LabelAtom label : labels) {
                nodeLabelsValues.add(label.getName());
            }
            result.put("node_label", nodeLabelsValues);
        }

        return result;
    }

    public static String getNodeName(Computer computer) {
        if (computer == null) {
            return null;
        }
        if (computer instanceof Jenkins.MasterComputer) {
            return "master";
        } else {
            return computer.getName();
        }
    }

    public static boolean isMainNode(String nodeName) {
        return "master".equalsIgnoreCase(nodeName) || "built-in".equalsIgnoreCase(nodeName);
    }

    public static String getNodeHostname(@Nullable EnvVars envVars, @Nullable Computer computer) {
        EnvVars computerEnv = getEnvironment(computer);

        String computerDDHostname = computerEnv != null ? computerEnv.get(DatadogGlobalConfiguration.DD_CI_HOSTNAME) : null;
        if (DatadogUtilities.isValidHostname(computerDDHostname)) {
            return computerDDHostname;
        }

        String ddHostname = envVars != null ? envVars.get(DatadogGlobalConfiguration.DD_CI_HOSTNAME) : null;
        if (DatadogUtilities.isValidHostname(ddHostname)) {
            return ddHostname;
        }

        String computerHostname = computerEnv != null ? computerEnv.get("HOSTNAME") : null;
        if (DatadogUtilities.isValidHostname(computerHostname)) {
            return computerHostname;
        }

        String hostname = envVars != null ? envVars.get("HOSTNAME") : null;
        if (DatadogUtilities.isValidHostname(hostname)) {
            return hostname;
        }

        try {
            if (computer != null) {
                String computerNodeName = DatadogUtilities.getNodeName(computer);
                if (DatadogUtilities.isMainNode(computerNodeName)) {
                    String masterHostname = DatadogUtilities.getHostname(null);
                    if (DatadogUtilities.isValidHostname(masterHostname)) {
                        return masterHostname;
                    }
                }

                String computerHostName = computer.getHostName();
                if (DatadogUtilities.isValidHostname(computerHostName)) {
                    return computerHostName;
                }

                Node node = computer.getNode();
                if (node != null) {
                    FilePath rootPath = node.getRootPath();
                    if (rootPath != null) {
                        String[] command;
                        if (isUnix(rootPath)) {
                            command = new String[]{"hostname", "-f"};
                        } else {
                            command = new String[]{"hostname"};
                        }

                        ShellCommandCallable hostnameCommand = new ShellCommandCallable(Collections.emptyMap(), HOSTNAME_CMD_TIMEOUT_MILLIS, command);
                        String shellHostname = rootPath.act(hostnameCommand).trim();
                        if (DatadogUtilities.isValidHostname(shellHostname)) {
                            return shellHostname;
                        }
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logException(logger, Level.FINE, "Interrupted while trying to extract hostname from StepContext.", e);

        } catch (Exception e) {
            logException(logger, Level.FINE, "Unable to get hostname for node " + computer.getName(), e);
        }
        return null;
    }

    private static EnvVars getEnvironment(Computer computer) {
        if (computer != null) {
            try {
                return computer.getEnvironment();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logException(logger, Level.FINE, "Interrupted while trying to get computer env vars", e);
            } catch (Exception e) {
                logException(logger, Level.FINE, "Error getting computer env vars", e);
            }
        }
        return null;
    }

    private static boolean isUnix(FilePath filePath) throws IOException, InterruptedException {
        return filePath != null && filePath.act(new IsUnix());
    }

    // copied from hudson.FilePath.IsUnix
    private static final class IsUnix extends MasterToSlaveCallable<Boolean, IOException> {
        private static final long serialVersionUID = 1L;

        @Override
        @NonNull
        public Boolean call() {
            return File.pathSeparatorChar == ':';
        }
    }

    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    public static Set<String> getNodeLabels(Computer computer) {
        Set<LabelAtom> labels;
        try {
            labels = computer.getNode().getAssignedLabels();
        } catch (Exception e) {
            logger.fine("Could not retrieve labels: " + e.getMessage());
            return Collections.emptySet();
        }

        final Set<String> labelsStr = new HashSet<>();
        for (final LabelAtom label : labels) {
            labelsStr.add(label.getName());
        }

        return labelsStr;
    }

    public static String getUserId() {
        User user = User.current();
        if (user == null) {
            return "anonymous";
        } else {
            return user.getId();
        }
    }

    public static String getItemName(Item item) {
        if (item == null) {
            return "unknown";
        }
        return item.getName();
    }

    public static Long getRunStartTimeInMillis(Run run) {
        // getStartTimeInMillis wrapper in order to mock it in unit tests
        return run.getStartTimeInMillis();
    }

    public static long currentTimeMillis() {
        // This method exist so we can mock System.currentTimeMillis in unit tests
        return System.currentTimeMillis();
    }

    public static String getJenkinsUrl() {
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins == null) {
            return "unknown";
        } else {
            try {
                return jenkins.getRootUrl();
            } catch (Exception e) {
                return "unknown";
            }
        }
    }

    public static String getResultTag(@Nonnull FlowNode node) {
        if (StageStatus.isSkippedStage(node)) {
            return "SKIPPED";
        }
        if (node instanceof BlockEndNode) {
            BlockStartNode startNode = ((BlockEndNode) node).getStartNode();
            if (StageStatus.isSkippedStage(startNode)) {
                return "SKIPPED";
            }
        }
        ErrorAction error = node.getError();
        if (error != null) {
            return "ERROR";
        }
        WarningAction warningAction = node.getPersistentAction(WarningAction.class);
        if (warningAction != null) {
            Result result = warningAction.getResult();
            // Result could be SUCCESS, NOT_BUILT, FAILURE, etc https://javadoc.jenkins-ci.org/hudson/model/Result.html
            return result.toString();
        }
        // Other possibilities are queued, launched, unknown: https://javadoc.jenkins.io/plugin/workflow-api/org/jenkinsci/plugins/workflow/actions/QueueItemAction.QueueState.html
        if (QueueItemAction.getNodeState(node) == QueueItemAction.QueueState.CANCELLED) {
            return "CANCELED";
        }
        FlowExecution exec = node.getExecution();
        if ((exec != null && exec.isComplete()) || NotExecutedNodeAction.isExecuted(node)) {
            return "SUCCESS";
        }
        return "UNKNOWN";
    }

    /**
     * Returns true if a {@code FlowNode} is a Stage node.
     *
     * @param flowNode the flow node to evaluate
     * @return flag indicating if a flowNode is a Stage node.
     */
    public static boolean isStageNode(FlowNode flowNode) {
        if (!(flowNode instanceof BlockStartNode)) {
            return false;
        }
        if (flowNode.getAction(StageAction.class) != null) {
            // Legacy style stage block without a body
            // https://groups.google.com/g/jenkinsci-users/c/MIVk-44cUcA
            return true;
        }
        if (flowNode.getAction(ThreadNameAction.class) != null) {
            // TODO comment
            return false;
        }
        return flowNode.getAction(LabelAction.class) != null;
    }

    /**
     * Returns enclosing stage node for the given node.
     * Never returns the node itself.
     */
    public static BlockStartNode getEnclosingStageNode(FlowNode node) {
        for (BlockStartNode block : node.iterateEnclosingBlocks()) {
            if (DatadogUtilities.isStageNode(block)) {
                return block;
            }
        }
        return null;
    }

    /**
     * Returns a normalized result for traces.
     *
     * @param result (success, failure, error, aborted, not_build, canceled, skipped, unstable, unknown)
     * @return the normalized result for the traces based on the jenkins result
     */
    public static String statusFromResult(String result) {
        String resultLowercase = result == null ? "error" : result.toLowerCase();
        switch (resultLowercase) {
            case "failure":
                return "error";
            case "aborted":
                return "canceled";
            case "not_built":
                return "skipped";
            default:
                return resultLowercase;
        }
    }

    public static void severe(Logger logger, Throwable e, String message) {
        logException(logger, Level.SEVERE, message, e);
    }

    public static void logException(Logger logger, Level logLevel, String message, Throwable e) {
        if (e != null) {
            addExceptionToBuffer(e);

            String stackTrace = ExceptionUtils.getStackTrace(e);
            message = (message != null ? message + " " : "An unexpected error occurred: ") + stackTrace;
        }
        if (StringUtils.isNotEmpty(message)) {
            logger.log(logLevel, message);
        }
    }

    private static final String EXCEPTIONS_BUFFER_CAPACITY_ENV_VAR = "DD_JENKINS_EXCEPTIONS_BUFFER_CAPACITY";
    private static final int DEFAULT_EXCEPTIONS_BUFFER_CAPACITY = 100;
    private static final BlockingQueue<Pair<Date, Throwable>> EXCEPTIONS_BUFFER;

    static {
        int bufferCapacity = getExceptionsBufferCapacity();
        if (bufferCapacity > 0) {
            EXCEPTIONS_BUFFER = new ArrayBlockingQueue<>(bufferCapacity);
        } else {
            EXCEPTIONS_BUFFER = null;
        }
    }

    private static int getExceptionsBufferCapacity() {
        String bufferCapacityString = System.getenv("EXCEPTIONS_BUFFER_CAPACITY_ENV_VAR");
        if (bufferCapacityString == null) {
            return DEFAULT_EXCEPTIONS_BUFFER_CAPACITY;
        } else {
            try {
                return Integer.parseInt(bufferCapacityString);
            } catch (NumberFormatException e) {
                severe(logger, e, EXCEPTIONS_BUFFER_CAPACITY_ENV_VAR + " environment variable has invalid value");
                return DEFAULT_EXCEPTIONS_BUFFER_CAPACITY;
            }
        }
    }

    private static void addExceptionToBuffer(Throwable e) {
        if (EXCEPTIONS_BUFFER == null) {
            return;
        }
        Pair<Date, Throwable> p = Pair.of(new Date(), e);
        while (!EXCEPTIONS_BUFFER.offer(p)) {
            // rather than popping elements one by one, we drain several with one operation to reduce lock contention
            int drainSize = Math.max(DEFAULT_EXCEPTIONS_BUFFER_CAPACITY / 10, 1);
            EXCEPTIONS_BUFFER.drainTo(new ArrayList<>(drainSize), drainSize);
        }
    }

    public static BlockingQueue<Pair<Date, Throwable>> getExceptionsBuffer() {
        return EXCEPTIONS_BUFFER;
    }

    public static int toInt(boolean b) {
        return b ? 1 : 0;
    }


    /**
     * Returns a date as String in the ISO8601 format
     *
     * @param date the date object to transform
     * @return date as String in the ISO8601 format
     */
    public static String toISO8601(Date date) {
        if (date == null) {
            return null;
        }

        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT_ISO8601);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(date);
    }

    public static boolean isValidISO8601Date(String date) {
        if (StringUtils.isBlank(date)) {
            return false;
        }
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT_ISO8601);
        try {
            sdf.parse(date);
            return true;
        } catch (ParseException e) {
            return false;
        }
    }

    /**
     * Returns a JSON array string based on the set.
     *
     * @param set the set to transform into a JSON
     * @return json array string
     */
    public static String toJson(final Set<String> set) {
        if (set == null || set.isEmpty()) {
            return null;
        }

        // We want to avoid using Json libraries cause
        // may cause incompatibilities on different Jenkins versions.
        final StringBuilder sb = new StringBuilder();
        sb.append("[");
        int index = 1;
        for (String val : set) {
            final String escapedValue = StringEscapeUtils.escapeJavaScript(val);
            sb.append("\"").append(escapedValue).append("\"");
            if (index < set.size()) {
                sb.append(",");
            }
            index += 1;
        }
        sb.append("]");

        return sb.toString();
    }

    /**
     * Returns a JSON object string based on the map.
     *
     * @param map the map to transform into a JSON
     * @return json object string
     */
    public static String toJson(final Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }

        // We want to avoid using Json libraries cause
        // may cause incompatibilities on different Jenkins versions.
        final StringBuilder sb = new StringBuilder();
        sb.append("{");
        int index = 1;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            final String escapedKey = StringEscapeUtils.escapeJavaScript(entry.getKey());
            final String escapedValue = StringEscapeUtils.escapeJavaScript(entry.getValue());
            sb.append(String.format("\"%s\":\"%s\"", escapedKey, escapedValue));
            if (index < map.size()) {
                sb.append(",");
            }
            index += 1;
        }
        sb.append("}");

        return sb.toString();
    }

    /**
     * Removes all actions related to traces for Jenkins pipelines.
     *
     * @param actionable a domain object that can contain actions, such as run or flow node.
     */
    public static void cleanUpTraceActions(final Actionable actionable) {
        if (actionable != null) {
            // Each call to removeActions triggers persisting data to disc.
            // To avoid writing to disc multiple times, we only call removeActions once with the marker interface as the argument.
            actionable.removeActions(DatadogPluginAction.class);
        }
    }

    public static String getCatchErrorResult(BlockStartNode startNode) {
        String displayFunctionName = startNode.getDisplayFunctionName();
        if ("warnError".equals(displayFunctionName)) {
            return CITags.STATUS_UNSTABLE;
        }
        if ("catchError".equals(displayFunctionName)) {
            ArgumentsAction argumentsAction = startNode.getAction(ArgumentsAction.class);
            if (argumentsAction != null) {
                Map<String, Object> arguments = argumentsAction.getArguments();
                Object stageResult = arguments.get("stageResult");
                return stageResult != null
                        ? statusFromResult(stageResult.toString())
                        : CITags.STATUS_SUCCESS;
            }
        }
        return null;
    }

    /**
     * Checks to see if event should be sent to client
     *
     * @param eventName - the event to check
     * @return true if event should be sent to client
     */
    public static boolean shouldSendEvent(String eventName) {
        if (getDatadogGlobalDescriptor() == null) { // sometimes null for tests, so default is to send all events
            return true;
        }

        return createIncludeLists().contains(eventName);
    }

    /**
     * Creates inclusion list for events by looking at toggles and inclusion/exclusion string lists
     *
     * @return list of event name strings that can be sent
     */
    private static List<String> createIncludeLists() {
        List<String> includedEvents = new ArrayList<String>(Arrays.asList(
                DatadogGlobalConfiguration.DEFAULT_EVENTS.split(",")));

        DatadogGlobalConfiguration cfg = getDatadogGlobalDescriptor();
        String includeEvents = cfg.getIncludeEvents();
        String excludeEvents = cfg.getExcludeEvents();

        if (includeEvents != null && !includeEvents.isEmpty()) {
            includedEvents.addAll(Arrays.asList(includeEvents.split(",")));
        }

        if (cfg.isEmitSystemEvents()) {
            includedEvents.addAll(new ArrayList<String>(
                    Arrays.asList(DatadogGlobalConfiguration.SYSTEM_EVENTS.split(","))
            ));
        }

        if (cfg.isEmitSecurityEvents()) {
            includedEvents.addAll(new ArrayList<String>(
                    Arrays.asList(DatadogGlobalConfiguration.SECURITY_EVENTS.split(","))
            ));
        }

        includedEvents = includedEvents.stream().distinct().collect(Collectors.toList());

        if (excludeEvents != null && !excludeEvents.isEmpty())
            includedEvents.removeIf(excludeEvents::contains);

        return includedEvents;
    }

    /**
     * Returns the {@code Throwable} of a certain {@code FlowNode}, if it has errors.
     *
     * @return throwable associated with a certain flowNode.
     */
    public static Throwable getErrorObj(FlowNode flowNode) {
        final ErrorAction errorAction = flowNode.getAction(ErrorAction.class);
        return (errorAction != null) ? errorAction.getError() : null;
    }

    @Nullable
    public static TaskListener getTaskListener(Run run) throws IOException {
        if (run instanceof WorkflowRun) {
            WorkflowRun workflowRun = (WorkflowRun) run;
            FlowExecution execution = workflowRun.getExecution();
            if (execution != null) {
                FlowExecutionOwner owner = execution.getOwner();
                return owner.getListener();
            }
        }
        return null;
    }

    /**
     * Returns the startTime of a certain {@code FlowNode}, if it has time information.
     * @return startTime of the flowNode in milliseconds.
     */
    public static long getTimeMillis(FlowNode flowNode) {
        if (flowNode != null) {
            TimingAction time = flowNode.getAction(TimingAction.class);
            if(time != null) {
                return time.getStartTime();
            }
        }
        return -1L;
    }

    @Nullable
    public static String getDatadogPluginVersion() {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            return null;
        }
        PluginManager pluginManager = jenkins.getPluginManager();
        PluginWrapper datadogPlugin = pluginManager.getPlugin("datadog");
        if (datadogPlugin == null) {
            return null;
        }
        return datadogPlugin.getVersion();
    }

}

