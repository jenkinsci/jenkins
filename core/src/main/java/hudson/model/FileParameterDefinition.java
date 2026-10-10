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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.cli.CLICommand;
import jakarta.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;
import jenkins.model.EnvironmentVariableParameterNameFormValidation;
import net.sf.json.JSONObject;
import org.apache.commons.fileupload2.core.FileItem;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * {@link ParameterDefinition} for doing file upload.
 *
 * <p>
 * The file will be then placed into the workspace at the beginning of a build.
 *
 * @author Kohsuke Kawaguchi
 */
public class FileParameterDefinition extends ParameterDefinition {

    /**
     * @since 2.281
     */
    @DataBoundConstructor
    public FileParameterDefinition(@NonNull String name) {
        super(name);
    }

    public FileParameterDefinition(@NonNull String name, @CheckForNull String description) {
        this(name);
        setDescription(description);
    }

    @Override
    public FileParameterValue createValue(StaplerRequest2 req, JSONObject jo) {
        FileParameterValue p = req.bindJSON(FileParameterValue.class, jo);
        p.setLocation(getName());
        p.setDescription(getDescription());
        return p;
    }

    @Extension @Symbol({"file", "fileParam"})
    public static class DescriptorImpl extends ParameterDescriptor implements EnvironmentVariableParameterNameFormValidation {
        @NonNull
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
    public ParameterValue createValue(StaplerRequest2 req) {
        FileItem src;
        try {
            src = req.getFileItem2(getName());
        } catch (ServletException | IOException e) {
            // Not sure what to do here. We might want to raise this
            // but that would involve changing the throws for this call
            return null;
        }
        if (src == null) {
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
        possiblyPathName = possiblyPathName.substring(possiblyPathName.lastIndexOf('/') + 1);
        possiblyPathName = possiblyPathName.substring(possiblyPathName.lastIndexOf('\\') + 1);
        return possiblyPathName;
    }

    @SuppressWarnings("deprecation")
    @Override
    public ParameterValue createValue(CLICommand command, String value) throws IOException, InterruptedException {
        // capture the file to the server
        File local = Files.createTempFile("jenkins", "parameter").toFile();
        String name;
        if (value.isEmpty()) {
            FileUtils.copyInputStreamToFile(command.stdin, local);
            name = "stdin";
        } else {
            command.checkChannel();
            return null; // never called
        }

        FileParameterValue p = new FileParameterValue(getName(), local, name);
        p.setDescription(getDescription());
        p.setLocation(getName());
        return p;
    }

    @Override
    public int hashCode() {
        if (FileParameterDefinition.class != getClass()) {
            return super.hashCode();
        }
        return Objects.hash(getName(), getDescription());
    }

    @Override
    @SuppressFBWarnings(value = "EQ_GETCLASS_AND_CLASS_CONSTANT", justification = "ParameterDefinitionTest tests that subclasses are not equal to their parent classes, so the behavior appears to be intentional")
    public boolean equals(Object obj) {
        if (FileParameterDefinition.class != getClass())
            return super.equals(obj);
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        FileParameterDefinition other = (FileParameterDefinition) obj;
        if (!Objects.equals(getName(), other.getName()))
            return false;
        return Objects.equals(getDescription(), other.getDescription());
    }
}
