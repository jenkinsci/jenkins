package hudson;

import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

/**
 * Extension point for displaying time.
 * 
 * Time in appropriate format is provided by page <tt>time.jelly</tt>.
 * 
 * @author Lucie Votypkova
 */
public abstract class TimeFormat implements Describable<TimeFormat>, ExtensionPoint{
    
    public static DescriptorExtensionList<TimeFormat,Descriptor<TimeFormat>> all() {
        return Jenkins.getInstance().<TimeFormat,Descriptor<TimeFormat>>getDescriptorList(TimeFormat.class);
    }
    
}
