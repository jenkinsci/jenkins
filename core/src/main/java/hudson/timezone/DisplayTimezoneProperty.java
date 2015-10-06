/*
 * The MIT License
 * 
 * Copyright (c) 2015, Christian Meyer, Alexander Ost
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.timezone;

import hudson.Extension;
import hudson.model.User;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;

import java.util.List;
import java.util.Arrays;
import java.util.TimeZone;

public class DisplayTimezoneProperty extends hudson.model.UserProperty {
    
    private final boolean useTimezone;
    private final String  displayTimezone;

    public DisplayTimezoneProperty(boolean useTimezone, String displayTimezone) {
        this.useTimezone     = useTimezone;
        this.displayTimezone = displayTimezone;
    }

    /**
    * Will return the time zone as a string.
    */
    @Exported
    public String getDisplayTimezone() {
        return displayTimezone;
    }

    /**
    * Will return true if the custom user time zone is enabled.
    */
    @Exported
    public boolean isEnabled() {
        return useTimezone;
    }

    /**
    * Will be used in the jelly files to check if the user has customized the time zone.
    */
    public static boolean isCurrentUserUseDisplayTimezone(){
        User user = User.current();
        boolean result = false;

        if ( user!=null && user.getProperty(DisplayTimezoneProperty.class).isEnabled()) {
          result = true;
        }
        return result;
    }

    /**
    * Will be used in the jelly files to get the user time zone as a string.
    */
    public static String getCurrentUserDisplayTimezone(){
        User user = User.current();
        return user.getProperty(DisplayTimezoneProperty.class).getDisplayTimezone();
    }

    @Extension
    public static final class DescriptorImpl extends UserPropertyDescriptor {
        public List<String> TIMEZONES;

        public DescriptorImpl() {
            String[] timeZones = TimeZone.getAvailableIDs();
            Arrays.sort( timeZones );

            this.TIMEZONES = Arrays.asList( timeZones );
        }

        public String getDisplayName() {
            return Messages.DisplayTimezoneProperty_DisplayName();
        }

        public UserProperty newInstance(User user) {
            return new DisplayTimezoneProperty( false, TimeZone.getDefault().getID() ); //default setting is system time zone
        }

        @Override
        public UserProperty newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new DisplayTimezoneProperty(formData.optBoolean("useTimezone"),formData.optString("displayTimezone"));
        }

    }

}
