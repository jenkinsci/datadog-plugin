package org.datadog.jenkins.plugins.datadog;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.ManagementLink;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.datadog.jenkins.plugins.datadog.flare.FlareContributor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Extension
public class DatadogPluginManagement extends ManagementLink {

    private static final Logger LOGGER = Logger.getLogger(DatadogPluginManagement.class.getName());

    @CheckForNull
    @Override
    public String getIconFileName() {
        return "/plugin/datadog/icons/dd_icon_rgb.svg";
    }

    @CheckForNull
    @Override
    public String getDisplayName() {
        return "Datadog";
    }

    @CheckForNull
    @Override
    public String getUrlName() {
        return "datadog";
    }

    @Override
    public String getDescription() {
        return "Datadog Plugin Troubleshooting";
    }

    @NonNull
    @Override
    public Category getCategory() {
        return Category.TROUBLESHOOTING;
    }

    @NonNull
    @Override
    public Permission getRequiredPermission() {
        return Jenkins.MANAGE;
    }

    @RequirePOST
    public void doDownloadDiagnosticFlare(StaplerRequest request, StaplerResponse response) throws Exception {
        if (!Jenkins.get().hasPermission(Jenkins.MANAGE)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        try {
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
            String formattedTimestamp = now.format(formatter);

            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition", String.format("attachment; filename=dd-jenkins-plugin-flare-%s.zip", formattedTimestamp));
            try (OutputStream out = response.getOutputStream()) {
                writeDiagnosticFlare(out);
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to generate Datadog plugin flare", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private void writeDiagnosticFlare(OutputStream out) throws IOException {
        ExtensionList<FlareContributor> contributors = ExtensionList.lookup(FlareContributor.class);
        try (ZipOutputStream zipOut = new ZipOutputStream(out)) {
            for (FlareContributor contributor : contributors) {
                zipOut.putNextEntry(new ZipEntry(contributor.getFilename()));
                try {
                    contributor.writeFileContents(zipOut);
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Datadog plugin flare contributor failed: " + contributor.getClass(), e);

                    zipOut.closeEntry();
                    zipOut.putNextEntry(new ZipEntry(contributor.getFilename() + ".error"));
                    zipOut.write(ExceptionUtils.getStackTrace(e).getBytes(StandardCharsets.UTF_8));
                } finally {
                    zipOut.closeEntry();
                }
            }
        }
    }
}