/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Seiji Sogabe, Tom Huybrechts
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

import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import hudson.Extension;
import hudson.FilePath;
import hudson.cli.CLICommand;
import org.apache.commons.fileupload.FileItem;

import java.io.IOException;
import java.io.File;
import javax.servlet.ServletException;

/**
 * {@link ParameterDefinition} for doing file upload.
 *
 * <p>
 * The file will be then placed into the workspace at the beginning of a build.
 *
 * @author Kohsuke Kawaguchi
 */
public class FileParameterDefinition extends ParameterDefinition {
    @DataBoundConstructor
    public FileParameterDefinition(String name, String description) {
        super(name, description);
    }

    public FileParameterValue createValue(StaplerRequest req, JSONObject jo) {
        FileParameterValue p = req.bindJSON(FileParameterValue.class, jo);
        p.setLocation(getName());
        p.setDescription(getDescription());
        return p;
    }

    @Extension @Symbol({"file","fileParam"})
    public static class DescriptorImpl extends ParameterDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.FileParameterDefinition_DisplayName();
        }

        @Override
        public String getHelpFile() {
            return "/help/parameter/file.html";
        }
    }

	@Override
	public ParameterValue createValue(StaplerRequest req) {
        FileItem src;
        try {
            src = req.getFileItem( getName() );
        } catch (ServletException e) {
            // Not sure what to do here. We might want to raise this
            // but that would involve changing the throws for this call
            return null;
        } catch (IOException e) {
            // ditto above
            return null;
        }
        if ( src == null ) {
            // the requested file parameter wasn't uploaded
            return null;
        }
        FileParameterValue p = new FileParameterValue(getName(), src, getFileName(src.getName()));
        p.setDescription(getDescription());
        p.setLocation(getName());
        return p;
	}

    /**
     * Strip off the path portion if the given path contains it.
     */
    private String getFileName(String possiblyPathName) {
        possiblyPathName = possiblyPathName.substring(possiblyPathName.lastIndexOf('/')+1);
        possiblyPathName = possiblyPathName.substring(possiblyPathName.lastIndexOf('\\')+1);
        return possiblyPathName;
    }

    @Override
    public ParameterValue createValue(CLICommand command, String value) throws IOException, InterruptedException {
        // capture the file to the server
        FilePath src = new FilePath(command.checkChannel(),value);
        File local = File.createTempFile("jenkins","parameter");
        src.copyTo(new FilePath(local));

        FileParameterValue p = new FileParameterValue(getName(), local, src.getName());
        p.setDescription(getDescription());
        p.setLocation(getName());
        return p;
    }
}
