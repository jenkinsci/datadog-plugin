import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval

// needed by the stress-test-traces-submit pipeline
def scriptApproval = ScriptApproval.get()
def signatures = [
        "field org.datadog.jenkins.plugins.datadog.traces.write.TraceWriter asyncWriter",
        "field org.datadog.jenkins.plugins.datadog.util.AsyncWriter queue",
        "method java.util.concurrent.BlockingQueue remainingCapacity",
        "new java.util.concurrent.atomic.AtomicBoolean boolean",
        "method java.util.concurrent.atomic.AtomicBoolean get",
        "method java.util.concurrent.atomic.AtomicBoolean set boolean",
        "staticMethod org.datadog.jenkins.plugins.datadog.traces.write.TraceWriterFactory getTraceWriter",
]

signatures.each { signature ->
    if (!scriptApproval.getPendingSignatures().any { it.signature == signature }) {
        scriptApproval.approveSignature(signature)
        println "Approved signature: $signature"
    } else {
        println "Signature already pending or approved: $signature"
    }
}
