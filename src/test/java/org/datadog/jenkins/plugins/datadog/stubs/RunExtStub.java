package org.datadog.jenkins.plugins.datadog.stubs;

import com.cloudbees.workflow.rest.external.RunExt;
import com.cloudbees.workflow.rest.external.StageNodeExt;
import java.util.ArrayList;
import java.util.List;

public class RunExtStub extends RunExt {

    private List<StageNodeExt> stages;

    public RunExtStub(){
        this.stages = new ArrayList<>();
    }
    public void addStage(StageNodeExt stage){
        this.stages.add(stage);
    }

    @Override
    public List<StageNodeExt> getStages() {
        return this.stages;
    }

}
