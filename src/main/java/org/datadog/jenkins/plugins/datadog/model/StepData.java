package org.datadog.jenkins.plugins.datadog.model;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Computer;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class StepData {

    private final StepEnvVars envVars;
    private final StepFilePath filePath;
    private final StepComputer computer;

    public StepData(final StepContext stepContext){
        this.envVars = new StepEnvVars(stepContext);
        this.filePath = new StepFilePath(stepContext);
        this.computer = new StepComputer(stepContext);
    }

    public StepEnvVars getEnvVars() {
        return envVars;
    }

    public StepFilePath getFilePath() {
        return filePath;
    }

    public StepComputer getComputer() {
        return computer;
    }

    public static class StepEnvVars {
        private final Map<String, String> envVars = new HashMap<>();

        public StepEnvVars(final StepContext stepContext) {
            EnvVars envVarsObj = null;
            try {
                envVarsObj = stepContext.get(EnvVars.class);
            } catch (Exception e){
                System.out.println("---- StepEnvVars Error: " + e);
            }

            if(envVarsObj != null) {
                envVars.putAll(envVarsObj.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
            }
        }

        public Map<String, String> getEnvVars() {
            return envVars;
        }

        public String get(final String key) {
            return getEnvVars().get(key);
        }

        public Set<Map.Entry<String, String>> entrySet() {
            return getEnvVars().entrySet();
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("StepEnvVars{");
            sb.append("envVars=").append(envVars);
            sb.append('}');
            return sb.toString();
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("StepData{");
        sb.append("envVars=").append(envVars);
        sb.append(", filePath=").append(filePath);
        sb.append(", computer=").append(computer);
        sb.append('}');
        return sb.toString();
    }

    public static class StepFilePath {
        private final String name;
        private final String baseName;
        private final String remote;

        public StepFilePath(StepContext stepContext) {

            FilePath filePath = null;
            try {
                filePath = stepContext.get(FilePath.class);
            } catch (Exception e){
                System.out.println("---- StepFilePath Error: " + e);
            }

            if(filePath != null){
                this.name = filePath.getName();
                this.baseName = filePath.getBaseName();
                this.remote = filePath.getRemote();
            } else {
                this.name = null;
                this.baseName = null;
                this.remote = null;
            }
        }

        public String getName() {
            return name;
        }

        public String getBaseName() {
            return baseName;
        }

        public String getRemote() {
            return remote;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("StepFilePath{");
            sb.append("name='").append(name).append('\'');
            sb.append(", baseName='").append(baseName).append('\'');
            sb.append(", remote='").append(remote).append('\'');
            sb.append('}');
            return sb.toString();
        }
    }

    public static class StepComputer {

        private final String name;
        private final String hostName;
        private final String nodeName;
        private final String displayName;

        public StepComputer(StepContext stepContext) {
            Computer computer = null;
            try {
                computer = stepContext.get(Computer.class);
            } catch (Exception e){
                System.out.println("---- StepComputer Error: " + e);
            }

            if(computer != null) {
                this.name = computer.getName();

                String hostName = null;
                try {
                    hostName = computer.getHostName();
                } catch (Exception e){
                    System.out.println("---- StepComputer HostName Error: " + e);
                }

                this.hostName = hostName;
                this.nodeName = computer.getNode().getNodeName();
                this.displayName = computer.getDisplayName();
            } else{
                this.name = null;
                this.hostName = null;
                this.nodeName = null;
                this.displayName = null;
            }
        }

        public String getName() {
            return name;
        }

        public String getHostName() {
            return hostName;
        }

        public String getNodeName() {
            return nodeName;
        }

        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("StepComputer{");
            sb.append("name='").append(name).append('\'');
            sb.append(", hostName='").append(hostName).append('\'');
            sb.append(", nodeName='").append(nodeName).append('\'');
            sb.append(", displayName='").append(displayName).append('\'');
            sb.append('}');
            return sb.toString();
        }
    }
}
