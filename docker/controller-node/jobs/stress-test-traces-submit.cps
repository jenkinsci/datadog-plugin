import java.util.concurrent.atomic.AtomicBoolean

node {
    def numBuilds = params.NUM_BUILDS ?: 10
    def numSteps = params.NUM_STEPS ?: 100
    def jobComplete = new AtomicBoolean(false)

    def parallelStages = [:]

    parallelStages['Generate jobs'] = {
        stage('Generate jobs') {
            def builds = [:]

            // Loop to create parallel jobs
            for (int i = 1; i <= numBuilds; i++) {
                def jobIndex = i
                builds["Job-${jobIndex}"] = {
                    echo "Starting Job ${jobIndex}"

                    // Inner loop to create steps within each job
                    for (int j = 1; j <= numSteps; j++) {
                        echo "Executing step ${j} in Job ${jobIndex}"

                        // Execute a shell command to echo random characters
                        sh "echo ${UUID.randomUUID()}"
                    }

                    echo "Finished Load Job ${jobIndex}"
                }
            }

            // Execute all jobs in parallel
            parallel builds
            jobComplete.set(true)
        }
    }

    parallelStages['Print traces queue capacity'] = {
        stage('Print traces queue capacity') {
            script {
                waitUntil {
                    echo "Remaining traces queue capacity ${org.datadog.jenkins.plugins.datadog.traces.write.TraceWriterFactory.getTraceWriter().asyncWriter.queue.remainingCapacity()}"
                    sleep time: 1, unit: 'SECONDS'
                    return jobComplete.get()
                }
            }
        }
    }

    parallel parallelStages
}
