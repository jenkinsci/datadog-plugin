package org.datadog.jenkins.plugins.datadog;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.model.Run;
import java.io.IOException;
import java.util.logging.Logger;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Create a job property for use with DataDog plugin.
 * @param <T>
 */
public class DataDogJobProperty<T extends Job<?,?>> extends JobProperty<T> {

  private static final Logger LOGGER =  Logger.getLogger(DatadogBuildListener.class.getName());
  private static final String DISPLAY_NAME = "DataDog Tagging";
  private String tagging = null;
  private boolean on;
  private String tagFile = null;
  
  /**  
   * This is a list of tagging targets to be submitted with the Build to DataDog.
   * @return the tagging
   */
  public String getTagging() {
    return tagging;
  }

  @DataBoundConstructor
  public DataDogJobProperty( boolean on, String tagging, String tagFile ) { 
    this.on = on;
    this.tagFile = tagFile;
    this.tagging = tagging;
  }

  @DataBoundSetter
  public void setTagging(final String tagging) {
    this.tagging = tagging;
  }
  
  @Override
  public JobProperty<?> reconfigure(StaplerRequest req, JSONObject form) 
          throws Descriptor.FormException {
    DataDogJobProperty prop = (DataDogJobProperty) super.reconfigure(req, form);
    prop.tagFile = (String)form.getJSONObject("choice").get("tagFile");
    prop.tagging = (String)form.getJSONObject("choice").get("tagging");
    return prop;
  }
 
  /**
   * @return the enabled
   */
  public boolean isOn() {
    return on;
  }

  /**
   * @param on the enabled to set
   */
  @DataBoundSetter
  public void setOn(boolean on) {
    this.on = on;
  }

  /**
   * @return the tagFile
   */
  public String getTagFile() {
    return tagFile;
  }

  /**
   * @param tagFile the tagFile to set
   */
  @DataBoundSetter
  public void setTagFile(String tagFile) {
    this.tagFile = tagFile;
  }
  
  public boolean isTagFileEmpty() {
    return StringUtils.isBlank(tagFile);
  }

  public boolean isTaggingEmpty() {
    return StringUtils.isBlank(tagging);
  }
  
  public String readTagFile(Run r) {
    String s = null;
    try {      
      FilePath path = new FilePath(r.getExecutor().getCurrentWorkspace(), 
              tagFile);      
      if(path.exists()) {
        s = path.readToString();
      }
    } catch (IOException ex) {
      LOGGER.severe(ex.getMessage());
    } catch (InterruptedException ex) {
      LOGGER.severe(ex.getMessage());
    }
    return s;
  }

  @Extension
  public static final class DataDogJobPropertyDescriptorImpl 
    extends JobPropertyDescriptor {
    
    @Override
    public String getDisplayName() {
        return DISPLAY_NAME;
    }

    @Override
    public boolean isApplicable(Class<? extends Job> jobType) {
        return true;
    }

  }
}
