package org.datadog.jenkins.plugins.datadog.model.git;

import java.io.Serializable;

public enum Source implements Serializable {

  /** *
   * !!!!!IMPORTANT!!!!! Order matters: latter sources have higher priority
   */
  JENKINS_ENV_VARS, GIT_CLIENT_PIPELINE_DEFINITION, GIT_CLIENT, USER_SUPPLIED_ENV_VARS

}
