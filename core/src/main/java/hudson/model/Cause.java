/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Michael B. Donohue, Seiji Sogabe
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
package hudson.model;

import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Cause object base class.  This class hierarchy is used to keep track of why 
 * a given build was started.   The Cause object is connected to a build via the
 * CauseAction object.
 *
 * @author Michael Donohue
 */
@ExportedBean
public abstract class Cause {
	@Exported(visibility=3)
	abstract public String getShortDescription();

        public String getHTMLDescription() {
            // Default to same as ShortDescription; subclasses can override
            return getShortDescription();
        }

	public static class LegacyCodeCause extends Cause {
		private StackTraceElement [] stackTrace;
		public LegacyCodeCause() {
			stackTrace = new Exception().getStackTrace();
		}
		
		@Override
		public String getShortDescription() {
			return Messages.Cause_LegacyCodeCause_ShortDescription();
		}
	}
	
	public static class UpstreamCause extends Cause {
		private String upstreamProject, upstreamUrl;
		private int upstreamBuild;
		private Cause upstreamCause;
		
		public UpstreamCause(AbstractBuild<?, ?> up) {
			upstreamBuild = up.getNumber();
			upstreamProject = up.getProject().getName();
			upstreamUrl = up.getProject().getUrl();
			CauseAction ca = up.getAction(CauseAction.class);
			upstreamCause = ca == null ? null : ca.getCause();
		}
		
		@Override
		public String getShortDescription() {
			return Messages.Cause_UpstreamCause_ShortDescription(upstreamProject, upstreamBuild);
		}
		
		@Override
		public String getHTMLDescription() {
                        // upstreamUrl added in 1.284, so handle missing value
			return upstreamUrl==null ? getShortDescription() : Messages.Cause_UpstreamCause_HTMLDescription(upstreamProject, upstreamBuild, upstreamUrl, Stapler.getCurrentRequest().getContextPath());
		}
	}

	public static class UserCause extends Cause {
		private String authenticationName;
		public UserCause() {
			this.authenticationName = Hudson.getAuthentication().getName();
		}
		
		@Override
		public String getShortDescription() {
			return Messages.Cause_UserCause_ShortDescription(authenticationName);
		}
		
		@Override
		public String getHTMLDescription() {
			return Messages.Cause_UserCause_HTMLDescription(authenticationName, Stapler.getCurrentRequest().getContextPath());
		}
	}
}
