/*
 * The MIT License
 *
 * Copyright (c) 2015 Oleg Nenashev.
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
package org.jvnet.hudson.test;

import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.tasks.Builder;
import java.io.IOException;
import javax.annotation.Nonnull;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Creates a test builder, which creates a file in the workspace.
 * @author Oleg Nenashev
 * @since TODO
 */
public class CreateFileBuilder extends Builder {
    
    @Nonnull
    private final String fileName;
    
    @Nonnull
    private final String fileContent;

    public CreateFileBuilder(@Nonnull String fileName, @Nonnull String fileContent) {
        this.fileName = fileName;
        this.fileContent = fileContent;
    }

    @Nonnull 
    public String getFileName() {
        return fileName;
    }

    @Nonnull 
    public String getFileContent() {
        return fileContent;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        listener.getLogger().println("Creating a file " + fileName);
        
        FilePath workspace = build.getWorkspace();
        if (workspace == null) {
            throw new AbortException("Cannot get the workspace of the build");
        }
        workspace.child(fileName).write(fileContent, "UTF-8");
        
        return true;
    }
    
    @Override
    public Descriptor<Builder> getDescriptor() {
        return new DescriptorImpl();
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<Builder> {
        
        @Override
        public Builder newInstance(StaplerRequest req, JSONObject data) {
            throw new UnsupportedOperationException("This is a temporarytest class, "
                    + "which should not be configured from UI");
        }

        @Override
        public String getDisplayName() {
            return "Create a file";
        }
    }
}
