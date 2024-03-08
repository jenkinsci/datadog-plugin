import hudson.model.Node
import hudson.slaves.DumbSlave
import hudson.slaves.JNLPLauncher
import jenkins.model.Jenkins

def agentNames = System.getenv("JENKINS_AGENT_NAMES")
for (String agentName : agentNames.split(" ")) {
    def executors = 2
    def remoteFS = "/jenkins/agent"
    def labels = "linux"

    DumbSlave agent = new DumbSlave(
            agentName,
            remoteFS,
            new JNLPLauncher(true)
    )
    agent.setNumExecutors(executors)
    agent.setLabelString(labels)
    agent.setMode(Node.Mode.NORMAL)

    Jenkins.instance.addNode(agent)

    def computer = agent.toComputer()
    if (computer != null) {
        String secret = computer.getJnlpMac()
        new File("/var/jenkins_home/shared/${agentName}.secret").text = secret
    } else {
        println("Failed to retrieve JNLP secret for ${agentName}.")
    }
}


