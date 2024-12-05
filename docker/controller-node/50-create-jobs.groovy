import hudson.model.FreeStyleProject
import hudson.model.TopLevelItem
import jenkins.model.Jenkins
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob

import javax.xml.transform.stream.StreamSource
import java.nio.file.Files
import java.nio.file.Paths

Jenkins jenkins = Jenkins.instance

Files.newDirectoryStream(Paths.get("/var/jenkins_home/sample-jobs")).forEach { path ->
    String fileName = path.getFileName().toString()
    String jobName = fileName.substring(0, fileName.lastIndexOf('.'))

    TopLevelItem project = jenkins.getItemByFullName(jobName)
    if (project != null) {
        println("Job already exists: $jobName")
        return

    } else {
        Class<? extends TopLevelItem> projectType
        if (jobName.contains("freestyle")) {
            projectType = FreeStyleProject.class
        } else {
            projectType = WorkflowJob.class
        }

        project = jenkins.createProject(projectType, jobName)
        println("Job created successfully: $jobName")
    }

    if (fileName.endsWith(".xml")) {
        InputStream stream = new BufferedInputStream(Files.newInputStream(path))
        try {
            project.updateByXml(new StreamSource(stream))
            project.save()
        } finally {
            stream.close()
        }
    } else if (fileName.endsWith(".cps")) {
        def pipelineScript = new File(path.toAbsolutePath().toString()).text
        project.setDefinition(new CpsFlowDefinition(pipelineScript, true))
    }

}
