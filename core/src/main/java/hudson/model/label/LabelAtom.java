/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
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
package hudson.model.label;

import hudson.BulkChange;
import hudson.XmlFile;
import hudson.model.Descriptor.FormException;
import hudson.model.Failure;
import hudson.model.Hudson;
import hudson.model.Label;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;
import hudson.util.DescribableList;
import hudson.util.VariableResolver;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Atomic single token label, like "foo" or "bar".
 * 
 * @author Kohsuke Kawaguchi
 * @since  1.372
 */
public class LabelAtom extends Label implements Saveable {
    private DescribableList<LabelAtomProperty,LabelAtomPropertyDescriptor> properties =
            new DescribableList<LabelAtomProperty,LabelAtomPropertyDescriptor>(this);

    public LabelAtom(String name) {
        super(name);
        load();
    }

    /**
     * Properties associated with this label.
     */
    @Exported
    public DescribableList<LabelAtomProperty, LabelAtomPropertyDescriptor> getProperties() {
        return properties;
    }

    @Override
    public boolean matches(VariableResolver<Boolean> resolver) {
        return resolver.resolve(name);
    }

    @Override
    public LabelOperatorPrecedence precedence() {
        return LabelOperatorPrecedence.ATOM;
    }

    /*package*/ XmlFile getConfigFile() {
        return new XmlFile(Hudson.XSTREAM,new File(Hudson.getInstance().root,"labels/"+name+".xml"));
    }

    public void save() throws IOException {
        if(BulkChange.contains(this))   return;
        try {
            getConfigFile().write(this);
            SaveableListener.fireOnChange(this, getConfigFile());
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to save "+getConfigFile(),e);
        }
    }

    public void load() {
        XmlFile file = getConfigFile();
        if(file.exists()) {
            try {
                file.unmarshal(this);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to load "+file, e);
            }
        }
        properties.setOwner(this);
    }

    /**
     * Returns all the {@link LabelAtomPropertyDescriptor}s that can be potentially configured
     * on this label.
     */
    public List<LabelAtomPropertyDescriptor> getApplicablePropertyDescriptors() {
        return LabelAtomProperty.all();
    }

    /**
     * Accepts the update to the node configuration.
     */
    public void doConfigSubmit( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException, FormException {
        final Hudson app = Hudson.getInstance();

        app.checkPermission(Hudson.ADMINISTER);

        properties.rebuildHetero(req, req.getSubmittedForm(), getApplicablePropertyDescriptors(), "properties");
        save();

        // take the user back to the label top page.
        rsp.sendRedirect2(".");
    }

    /**
     * Obtains an atom by its {@linkplain #getName() name}.
     */
    public static LabelAtom get(String l) {
        return Hudson.getInstance().getLabelAtom(l);
    }

    public static boolean isValidName(String name) {
        try {
            Hudson.checkGoodName(name);
            return true;
        } catch (Failure failure) {
            return false;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(LabelAtom.class.getName());
}
