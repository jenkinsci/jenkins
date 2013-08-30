/*
 * The MIT License
 *
 * Copyright 2013 Jesse Glick.
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

package jenkins.plugins.ui_samples;

import hudson.Extension;
import hudson.XmlFile;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormApply;
import hudson.util.ListBoxModel;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;

@Extension public final class HeteroList extends UISample {

    @Override public String getDescription() {
        return "Show a heterogeneous list of subitems with different data bindings for radio buttons and checkboxes";
    }

    @Override public List<SourceFile> getSourceFiles() {
        return super.getSourceFiles();
        // TODO add others
    }

    @Extension public static final class DescriptorImpl extends UISampleDescriptor {}

    public XmlFile getConfigFile() {
        return new XmlFile(new File(Jenkins.getInstance().getRootDir(), "stuff.xml"));
    }

    private Config config;

    public HeteroList() throws IOException {
        XmlFile xml = getConfigFile();
        if (xml.exists()) {
            xml.unmarshal(this);
        }
    }

    public Config getConfig() {
        return config;
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    public HttpResponse doConfigSubmit(StaplerRequest req) throws ServletException, IOException {
        config = null; // otherwise bindJSON will never clear it once set
        req.bindJSON(this, req.getSubmittedForm());
        getConfigFile().write(this);
        return FormApply.success(".");
    }

    public static final class Config extends AbstractDescribableImpl<Config> {

        private final List<Entry> entries;

        @DataBoundConstructor public Config(List<Entry> entries) {
            this.entries = entries != null ? new ArrayList<Entry>(entries) : Collections.<Entry>emptyList();
        }

        public List<Entry> getEntries() {
            return Collections.unmodifiableList(entries);
        }

        @Extension public static class DescriptorImpl extends Descriptor<Config> {
            @Override public String getDisplayName() {return "";}
        }

    }

    public static abstract class Entry extends AbstractDescribableImpl<Entry> {}

    public static final class SimpleEntry extends Entry {

        private final String text;

        @DataBoundConstructor public SimpleEntry(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }

        @Extension public static class DescriptorImpl extends Descriptor<Entry> {
            @Override public String getDisplayName() {
                return "Simple Entry";
            }
        }

    }

    public static final class ChoiceEntry extends Entry {

        private final String choice;

        @DataBoundConstructor public ChoiceEntry(String choice) {
            this.choice = choice;
        }

        public String getChoice() {
            return choice;
        }

        @Extension public static class DescriptorImpl extends Descriptor<Entry> {

            @Override public String getDisplayName() {
                return "Choice Entry";
            }

            public ListBoxModel doFillChoiceItems() {
                return new ListBoxModel().add("good").add("bad").add("ugly");
            }

        }
    }

}
