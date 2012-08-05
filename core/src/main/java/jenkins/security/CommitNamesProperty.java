/*
 * The MIT License
 *
 * Copyright (c) 2012, Daniel Khodaparast
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

package jenkins.security;

import hudson.Extension;
import hudson.Util;
import hudson.model.Descriptor.FormException;
import hudson.model.User;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Grants the ability to define an alternate list of commit names for a user.
 * 
 * @author Daniel Khodaparast
 * @since 1.477
 */
public class CommitNamesProperty extends UserProperty {

	private final String commitNames;

	public CommitNamesProperty(String commitNames) {
		if (commitNames != null) {
			commitNames = commitNames.replaceAll("\\s*,\\s*", ",").toLowerCase(Locale.ENGLISH);

			List<String> nameList = Arrays.asList(commitNames.split(","));
			List<String> filtered = new ArrayList<String>();

			for (String name : nameList) {
				if ((User.get(name, false) != null) || filtered.contains(name)) {
					continue;
				}
				filtered.add(name);
			}

			commitNames = StringUtils.join(filtered.toArray(), ",");
		}
		this.commitNames = commitNames;
	}

	@Exported
	public String getNames() {
		return Util.fixEmptyAndTrim(commitNames);
	}

	@Extension
	public static final class DescriptorImpl extends UserPropertyDescriptor {
		public String getDisplayName() {
			if (Jenkins.getInstance().isUseCommitNames()) {
				return Messages.CommitNamesProperty_DisplayName();
			}
			return null;
		}

		@Override
		public UserProperty newInstance(User user) {
			return new CommitNamesProperty(null);
		}

		@Override
		public UserProperty newInstance(StaplerRequest req, JSONObject formData) throws FormException {
			return new CommitNamesProperty(req.getParameter("commit.names"));
		}
	}
}