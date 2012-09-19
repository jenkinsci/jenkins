package hudson;

import hudson.model.Descriptor;
import hudson.model.User;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Property for displaying time {@link TimeFormat}.
 *
 * @author Lucie Votypkova
 */
public class TimeFormatUserProperty extends UserProperty{
        
        private TimeFormat format;
        
        
        public TimeFormatUserProperty(TimeFormat formater){
            this.format = formater;
        }
        
        
        public TimeFormat getFormat(){
            return format;
        }
        
        /**
         * Get all class for displaying time  
         * 
         * @return all time formats
         */
        public DescriptorExtensionList<TimeFormat,Descriptor<TimeFormat>> getAllTimeformats(){
            return TimeFormat.all();
        }
        
        
        @Extension
        public static final class DescriptorImpl extends UserPropertyDescriptor {
            public String getDisplayName() {
                return "Format of time";
            }

            public UserProperty newInstance(User user) {
                return new TimeFormatUserProperty(null);
            }

            @Override
            public UserProperty newInstance(StaplerRequest req, JSONObject formData) throws FormException {
                JSONObject timeFormater= formData.getJSONObject("assignedFormat");
                if(timeFormater.getString("value").equals("none"))
                    return new TimeFormatUserProperty(null);
                Descriptor<TimeFormat> descriptor= Jenkins.getInstance().getDescriptor(timeFormater.getString("value"));
                return new TimeFormatUserProperty(descriptor.newInstance(req, timeFormater));
            }
        }
        
    }
