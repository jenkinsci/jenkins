/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

import org.kohsuke.stapler.DataBoundConstructor;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItem;
import hudson.tasks.BuildWrapper;
import hudson.Launcher;

import java.io.IOException;

/**
 * {@link ParameterValue} for {@link FileParameterDefinition}.
 *
 * <h2>Persistence</h2>
 * <p>
 * {@link DiskFileItem} is persistable via serialization,
 * (although the data may get very large in XML) so this object
 * as a whole is persistable.
 *
 * @author Kohsuke Kawaguchi
 */
public class FileParameterValue extends ParameterValue {
    private FileItem file;

    private String location;

    @DataBoundConstructor
    public FileParameterValue(String name, FileItem file) {
        this(name, file, null);
    }
    public FileParameterValue(String name, FileItem file, String description) {
        super(name, description);
        assert file!=null;
        this.file = file;
    }

    // post initialization hook
    /*package*/ void setLocation(String location) {
        this.location = location;
    }

    public BuildWrapper createBuildWrapper(AbstractBuild<?,?> build) {
        return new BuildWrapper() {
            public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
                listener.getLogger().println("Copying file to "+location);
                build.getProject().getWorkspace().child(location).copyFrom(file);
                file = null;
                return new Environment() {};
            }
        };
    }

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result
				+ ((location == null) ? 0 : location.hashCode());
		return result;
	}

	/**
	 * In practice this will always be false, since location should be unique.
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		FileParameterValue other = (FileParameterValue) obj;
		if (location == null) {
			if (other.location != null)
				return false;
		} else if (!location.equals(other.location))
			return false;
		return true;
	}
    
}
