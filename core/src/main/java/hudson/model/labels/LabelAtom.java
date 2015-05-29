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
package hudson.model.labels;

import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import hudson.BulkChange;
import hudson.CopyOnWrite;
import hudson.XmlFile;
import hudson.model.Action;
import hudson.model.Descriptor.FormException;
import hudson.model.Failure;
import hudson.util.*;
import jenkins.model.Jenkins;
import hudson.model.Label;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

/**
 * Atomic single token label, like "foo" or "bar".
 * 
 * @author Kohsuke Kawaguchi
 * @since  1.372
 */
public class LabelAtom extends Label implements Saveable {
    private DescribableList<LabelAtomProperty,LabelAtomPropertyDescriptor> properties =
            new DescribableList<LabelAtomProperty,LabelAtomPropertyDescriptor>(this);

    @CopyOnWrite
    protected transient volatile List<Action> transientActions = new Vector<Action>();

    private String description;

    public LabelAtom(String name) {
        super(name);
    }

    /**
     * If the label contains 'unsafe' chars, escape them.
     */
    @Override
    public String getExpression() {
        return escape(name);
    }

    @Override
    public boolean isAtom() { return true; }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Note that this method returns a read-only view of {@link Action}s.
     * {@link LabelAtomProperty}s who want to add a project action
     * should do so by implementing {@link LabelAtomProperty#getActions(LabelAtom)}.
     */
    @SuppressWarnings("deprecation")
    @Override
    public List<Action> getActions() {
        // add all the transient actions, too
        List<Action> actions = new Vector<Action>(super.getActions());
        actions.addAll(transientActions);
        // return the read only list to cause a failure on plugins who try to add an action here
        return Collections.unmodifiableList(actions);
    }

    protected void updateTransientActions() {
        Vector<Action> ta = new Vector<Action>();

        for (LabelAtomProperty p : properties)
            ta.addAll(p.getActions(this));

        transientActions = ta;
    }

    /**
     * @since 1.580
     */
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) throws IOException {
        this.description = description;
        save();
    }

    /**
     * Properties associated with this label.
     */
    public DescribableList<LabelAtomProperty, LabelAtomPropertyDescriptor> getProperties() {
        return properties;
    }

    @Exported
    public List<LabelAtomProperty> getPropertiesList() {
        return properties.toList();
    }

    @Override
    public boolean matches(VariableResolver<Boolean> resolver) {
        return resolver.resolve(name);
    }

    @Override
    public <V, P> V accept(LabelVisitor<V, P> visitor, P param) {
        return visitor.onAtom(this,param);
    }

    @Override
    public Set<LabelAtom> listAtoms() {
        return Collections.singleton(this);
    }

    @Override
    public LabelOperatorPrecedence precedence() {
        return LabelOperatorPrecedence.ATOM;
    }

    /*package*/ XmlFile getConfigFile() {
        return new XmlFile(XSTREAM, new File(Jenkins.getInstance().root, "labels/"+name+".xml"));
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
        updateTransientActions();
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
    @RequirePOST
    public void doConfigSubmit( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException, FormException {
        final Jenkins app = Jenkins.getInstance();

        app.checkPermission(Jenkins.ADMINISTER);

        properties.rebuild(req, req.getSubmittedForm(), getApplicablePropertyDescriptors());

        this.description = req.getSubmittedForm().getString("description");

        updateTransientActions();
        save();

        FormApply.success(".").generateResponse(req, rsp, null);
    }

    /**
     * Accepts the new description.
     */
    @RequirePOST
    @Restricted(DoNotUse.class)
    public synchronized void doSubmitDescription( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);

        setDescription(req.getParameter("description"));
        rsp.sendRedirect(".");  // go to the top page
    }

    /**
     * Obtains an atom by its {@linkplain #getName() name}.
     * @see Jenkins#getLabelAtom
     */
    public static @Nullable LabelAtom get(@CheckForNull String l) {
        return Jenkins.getInstance().getLabelAtom(l);
    }

    public static LabelAtom findNearest(String name) {
        List<String> candidates = new ArrayList<String>();
        for (LabelAtom a : Jenkins.getInstance().getLabelAtoms()) {
            candidates.add(a.getName());
        }
        return get(EditDistance.findNearest(name, candidates));
    }

    public static boolean needsEscape(String name) {
        try {
            Jenkins.checkGoodName(name);
            // additional restricted chars
            for( int i=0; i<name.length(); i++ ) {
                char ch = name.charAt(i);
                if(" ()\t\n".indexOf(ch)!=-1)
                    return true;
            }
            return false;
        } catch (Failure failure) {
            return true;
        }
    }

    public static String escape(String name) {
        if (needsEscape(name))
            return QuotedStringTokenizer.quote(name);
        return name;
    }

    private static final Logger LOGGER = Logger.getLogger(LabelAtom.class.getName());

    private static final XStream2 XSTREAM = new XStream2();

    static {
        // Don't want Label.ConverterImpl to be used:
        XSTREAM.registerConverter(new LabelAtomConverter(), 100);
    }

    // class name is not ConverterImpl, to avoid getting picked up by AssociatedConverterImpl
    private static class LabelAtomConverter extends XStream2.PassthruConverter<LabelAtom> {
        private Label.ConverterImpl leafLabelConverter = new Label.ConverterImpl();

        private LabelAtomConverter() {
            super(XSTREAM);
        }

        public boolean canConvert(Class type) {
            return LabelAtom.class.isAssignableFrom(type);
        }

        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            if (context.get(IN_NESTED)==null) {
                context.put(IN_NESTED,true);
                try {
                    super.marshal(source,writer,context);
                } finally {
                    context.put(IN_NESTED,false);
                }
            } else
                leafLabelConverter.marshal(source,writer,context);
        }

        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            if (context.get(IN_NESTED)==null) {
                context.put(IN_NESTED,true);
                try {
                    return super.unmarshal(reader,context);
                } finally {
                    context.put(IN_NESTED,false);
                }
            } else
                return leafLabelConverter.unmarshal(reader,context);
        }

        @Override
        protected void callback(LabelAtom obj, UnmarshallingContext context) {
            // noop
        }

        private static final Object IN_NESTED = "VisitingInnerLabelAtom";
    }
}
