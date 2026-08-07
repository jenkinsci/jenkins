/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, Tom Huybrechts
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

import static hudson.Util.fixNull;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.Util;
import hudson.model.labels.LabelAtom;
import hudson.model.labels.LabelExpression;
import hudson.model.labels.LabelExpression.And;
import hudson.model.labels.LabelExpression.Binary;
import hudson.model.labels.LabelExpression.Iff;
import hudson.model.labels.LabelExpression.Implies;
import hudson.model.labels.LabelExpression.Not;
import hudson.model.labels.LabelExpression.Or;
import hudson.model.labels.LabelExpression.Paren;
import hudson.model.labels.LabelExpressionLexer;
import hudson.model.labels.LabelExpressionParser;
import hudson.model.labels.LabelOperatorPrecedence;
import hudson.model.labels.LabelVisitor;
import hudson.model.queue.SubTask;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import hudson.util.QuotedStringTokenizer;
import hudson.util.VariableResolver;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import jenkins.model.IComputer;
import jenkins.model.Jenkins;
import jenkins.model.ModelObjectWithChildren;
import jenkins.util.antlr.JenkinsANTLRErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Group of {@link Node}s.
 *
 * @author Kohsuke Kawaguchi
 * @see Jenkins#getLabels()
 * @see Jenkins#getLabel(String)
 */
@ExportedBean
public abstract class Label extends Actionable implements Comparable<Label>, ModelObjectWithChildren {
    /**
     * Display name of this label.
     */
    @NonNull
    protected final transient String name;
    private transient volatile Set<Node> nodes;
    private transient volatile Set<Cloud> clouds;
    private transient volatile int tiedJobsCount;

    @Exported
    @NonNull
    public final transient LoadStatistics loadStatistics;
    @NonNull
    public final transient NodeProvisioner nodeProvisioner;

    protected Label(@NonNull String name) {
        this.name = name;
         // passing these causes an infinite loop - getTotalExecutors(),getBusyExecutors());
        this.loadStatistics = new LoadStatistics(0, 0) {
            @Override
            protected Set<Node> getNodes() {
                return Label.this.getNodes();
            }

            @Override
            protected boolean matches(Queue.Item item, SubTask subTask) {
                final Label l = item.getAssignedLabelFor(subTask);
                return l != null && Label.this.matches(l.name);
            }
        };
        this.nodeProvisioner = new NodeProvisioner(this, loadStatistics);
    }

    /**
     * Alias for {@link #getDisplayName()}.
     */
    @NonNull
    @Exported(visibility = 2)
    public final String getName() {
        return getDisplayName();
    }

    /**
     * Returns a human-readable text that represents this label.
     */
    @Override
    @NonNull
    public String getDisplayName() {
        return name;
    }

    /**
     * Returns a label expression that represents this label.
     */
    public abstract String getExpression();

    /**
     * Relative URL from the context path, that ends with '/'.
     */
    public String getUrl() {
        return "label/" + Util.rawEncode(name) + '/';
    }

    @Override
    public String getSearchUrl() {
        return getUrl();
    }

    /**
     * Returns true iff this label is an atom.
     *
     * @since 1.580
     */
    public boolean isAtom() { return false; }

    /**
     * Evaluates whether the label expression is true given the specified value assignment.
     * IOW, returns true if the assignment provided by the resolver matches this label expression.
     */
    public abstract boolean matches(VariableResolver<Boolean> resolver);

    /**
     * Evaluates whether the label expression is true when an entity owns the given set of
     * {@link LabelAtom}s.
     */
    public final boolean matches(final Collection<LabelAtom> labels) {
        return matches(name -> {
            for (LabelAtom a : labels)
                if (a.getName().equals(name))
                    return true;
            return false;
        });
    }

    public final boolean matches(Node n) {
        return matches(n.getAssignedLabels());
    }

    /**
     * Returns true if this label is a "self label",
     * which means the label is the name of a {@link Node}.
     */
    public boolean isSelfLabel() {
        Set<Node> nodes = getNodes();
        return nodes.size() == 1 && nodes.iterator().next().getSelfLabel().equals(this);
    }

    /**
     * Gets all {@link Node}s that belong to this label.
     */
    @Exported
    public Set<Node> getNodes() {
        Set<Node> nodes = this.nodes;
        if (nodes != null) return nodes;

        Set<Node> r = new HashSet<>();
        Jenkins h = Jenkins.get();
        if (this.matches(h))
            r.add(h);
        for (Node n : h.getNodes()) {
            if (this.matches(n))
                r.add(n);
        }
        return this.nodes = Collections.unmodifiableSet(r);
    }

    @Restricted(DoNotUse.class) // Jelly
    @NonNull
    public Collection<? extends IComputer> getComputers() {
        return ExtensionList.lookupFirst(LabelComputerSource.class).get(this).stream().sorted(Comparator.comparing(IComputer::getName)).toList();
    }

    /**
     * Allows plugins to override the displayed list of computers per label.
     * @see ComputerSet.ComputerSource
     */
    @Restricted(Beta.class)
    public interface LabelComputerSource extends ExtensionPoint {
        @NonNull
        Collection<? extends IComputer> get(@NonNull Label label);
    }

    @Extension(ordinal = -1)
    @Restricted(DoNotUse.class)
    public static class LabelComputerSourceImpl implements LabelComputerSource {
        @Override
        @NonNull
        public Collection<? extends IComputer> get(@NonNull Label label) {
            return label.getNodes().stream().map(Node::toComputer).filter(Objects::nonNull).toList();
        }
    }

    /**
     * Gets all {@link Cloud}s that can launch for this label.
     */
    @Exported
    public Set<Cloud> getClouds() {
        if (clouds == null) {
            Set<Cloud> r = new HashSet<>();
            Jenkins h = Jenkins.get();
            for (Cloud c : h.clouds) {
                if (c.canProvision(this))
                    r.add(c);
            }
            clouds = Collections.unmodifiableSet(r);
        }
        return clouds;
    }

    /**
     * Can jobs be assigned to this label?
     * <p>
     * The answer is yes if there is a reasonable basis to believe that Hudson can have
     * an executor under this label, given the current configuration. This includes
     * situations such as (1) there are offline agents that have this label (2) clouds exist
     * that can provision agents that have this label.
     */
    public boolean isAssignable() {
        for (Node n : getNodes())
            if (n.getNumExecutors() > 0)
                return true;
        return !getClouds().isEmpty();
    }

    /**
     * Number of total {@link Executor}s that belong to this label.
     * <p>
     * This includes executors that belong to offline nodes, so the result
     * can be thought of as a potential capacity, whereas {@link #getTotalExecutors()}
     * is the currently functioning total number of executors.
     * <p>
     * This method doesn't take the dynamically allocatable nodes (via {@link Cloud})
     * into account. If you just want to test if there's some executors, use {@link #isAssignable()}.
     */
    public int getTotalConfiguredExecutors() {
        int r = 0;
        for (Node n : getNodes())
            r += n.getNumExecutors();
        return r;
    }

    /**
     * Number of total {@link Executor}s that belong to this label that are functioning.
     * <p>
     * This excludes executors that belong to offline nodes.
     */
    @Exported
    public int getTotalExecutors() {
        int r = 0;
        for (Node n : getNodes()) {
            Computer c = n.toComputer();
            if (c != null && c.isOnline())
                r += c.countExecutors();
        }
        return r;
    }

    /**
     * Number of busy {@link Executor}s that are carrying out some work right now.
     */
    @Exported
    public int getBusyExecutors() {
        int r = 0;
        for (Node n : getNodes()) {
            Computer c = n.toComputer();
            if (c != null && c.isOnline())
                r += c.countBusy();
        }
        return r;
    }

    /**
     * Number of idle {@link Executor}s that can start working immediately.
     */
    @Exported
    public int getIdleExecutors() {
        int r = 0;
        for (Node n : getNodes()) {
            Computer c = n.toComputer();
            if (c != null && (c.isOnline() || c.isConnecting()) && c.isAcceptingTasks())
                r += c.countIdle();
        }
        return r;
    }

    /**
     * Returns true if all the nodes of this label is offline.
     */
    @Exported
    public boolean isOffline() {
        for (Node n : getNodes()) {
            Computer c = n.toComputer();
            if (c != null && !c.isOffline())
                return false;
        }
        return true;
    }

    /**
     * Returns a human readable text that explains this label.
     */
    @Exported
    public String getDescription() {
        Set<Node> nodes = getNodes();
        if (nodes.isEmpty()) {
            Set<Cloud> clouds = getClouds();
            if (clouds.isEmpty())
                return Messages.Label_InvalidLabel();

            return Messages.Label_ProvisionedFrom(toString(clouds));
        }

        if (nodes.size() == 1)
            return nodes.iterator().next().getNodeDescription();

        return Messages.Label_GroupOf(toString(nodes));
    }

    private String toString(Collection<? extends ModelObject> model) {
        boolean first = true;
        StringBuilder buf = new StringBuilder();
        for (ModelObject c : model) {
            if (buf.length() > 80) {
                buf.append(",...");
                break;
            }
            if (!first)  buf.append(',');
            else        first = false;
            buf.append(c.getDisplayName());
        }
        return buf.toString();
    }

    /**
     * Returns projects that are tied on this node.
     */
    @Exported
    public List<AbstractProject> getTiedJobs() {
        return StreamSupport.stream(Jenkins.get().allItems(AbstractProject.class,
                i -> i instanceof TopLevelItem && this.equals(i.getAssignedLabel())).spliterator(),
                true)
                .sorted(Items.BY_FULL_NAME).collect(Collectors.toList());
    }

    /**
     * Returns an approximate count of projects that are tied on this node.
     *
     * In a system without security this should be the same
     * as {@code getTiedJobs().size()} but significantly faster as it involves fewer temporary objects and avoids
     * sorting the intermediary list. In a system with security, this will likely return a higher value as it counts
     * all jobs (mostly) irrespective of access.
     * @return a count of projects that are tied on this node.
     */
    public int getTiedJobCount() {
        if (tiedJobsCount != -1) return tiedJobsCount;

        // denormalize for performance
        // we don't need to respect security as much when returning a simple count
        try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
            int result = 0;
            for (AbstractProject ignored : Jenkins.get().allItems(AbstractProject.class, p -> matches(p.getAssignedLabelString()))) {
                ++result;
            }
            return tiedJobsCount = result;
        }
    }

    public boolean contains(Node node) {
        return getNodes().contains(node);
    }

    /**
     * If there's no such label defined in {@link Node} or {@link Cloud}.
     * This is usually used as a signal that this label is invalid.
     */
    public boolean isEmpty() {
        return getNodes().isEmpty() && getClouds().isEmpty();
    }

    /*package*/ void reset() {
        nodes = null;
        clouds = null;
        tiedJobsCount = -1;
    }

    /**
     * Expose this object to the remote API.
     */
    public Api getApi() {
        return new Api(this);
    }

    /**
     * Accepts a visitor and call its respective "onXYZ" method based no the actual type of 'this'.
     */
    public abstract <V, P> V accept(LabelVisitor<V, P> visitor, P param);

    /**
     * Lists all the atoms contained in this label.
     *
     * @since 1.420
     */
    public Set<LabelAtom> listAtoms() {
        Set<LabelAtom> r = new HashSet<>();
        accept(ATOM_COLLECTOR, r);
        return r;
    }

    /**
     * Returns the label that represents {@code this&&rhs}
     */
    public Label and(Label rhs) {
        return new LabelExpression.And(this, rhs);
    }

    /**
     * Returns the label that represents {@code this||rhs}
     */
    public Label or(Label rhs) {
        return new LabelExpression.Or(this, rhs);
    }

    /**
     * Returns the label that represents {@code this<->rhs}
     */
    public Label iff(Label rhs) {
        return new LabelExpression.Iff(this, rhs);
    }

    /**
     * Returns the label that represents {@code this->rhs}
     */
    public Label implies(Label rhs) {
        return new LabelExpression.Implies(this, rhs);
    }

    /**
     * Returns the label that represents {@code !this}
     */
    public Label not() {
        return new LabelExpression.Not(this);
    }

    /**
     * Returns the label that represents {@code (this)}
     * This is a pointless operation for machines, but useful
     * for humans who find the additional parenthesis often useful
     */
    public Label paren() {
        return new LabelExpression.Paren(this);
    }

    /**
     * Precedence of the top most operator.
     */
    public abstract LabelOperatorPrecedence precedence();


    @Override
    public final boolean equals(Object that) {
        if (this == that) return true;
        if (that == null || getClass() != that.getClass()) return false;

        return matches(((Label) that).name);

    }

    @Override
    public final int hashCode() {
        return name.hashCode();
    }

    @Override
    public final int compareTo(Label that) {
        return this.name.compareTo(that.name);
    }


    /**
     * Evaluates whether the current label name is equal to the name parameter.
     *
     */
    private boolean matches(String name) {
        return this.name.equals(name);
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public ContextMenu doChildrenContextMenu(StaplerRequest2 request, StaplerResponse2 response) throws Exception {
        ContextMenu menu = new ContextMenu();
        for (Node node : getNodes()) {
            menu.add(node);
        }
        return menu;
    }

    public static final class ConverterImpl implements Converter {
        public ConverterImpl() {
        }

        @Override
        public boolean canConvert(Class type) {
            return Label.class.isAssignableFrom(type);
        }

        @Override
        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            Label src = (Label) source;
            writer.setValue(src.getExpression());
        }

        @Override
        public Object unmarshal(HierarchicalStreamReader reader, final UnmarshallingContext context) {
            return Jenkins.get().getLabel(reader.getValue());
        }
    }

    /**
     * Convert a whitespace-separate list of tokens into a set of {@link Label}s.
     *
     * @param labels
     *      Strings like "abc def ghi". Can be empty or null.
     * @return
     *      Can be empty but never null. A new writable set is always returned,
     *      so that the caller can add more to the set.
     * @since 1.308
     */
    @NonNull
    public static Set<LabelAtom> parse(@CheckForNull String labels) {
        final Set<LabelAtom> r = new TreeSet<>();
        labels = fixNull(labels);
        if (!labels.isEmpty()) {
            Jenkins j = Jenkins.get();
            LabelAtom labelAtom = j.tryGetLabelAtom(labels);
            if (labelAtom == null) {
                final QuotedStringTokenizer tokenizer = new QuotedStringTokenizer(labels);
                while (tokenizer.hasMoreTokens())
                    r.add(j.getLabelAtom(tokenizer.nextToken()));
            } else {
                r.add(labelAtom);
            }
        }
        return r;
    }

    /**
     * Obtains a label by its {@linkplain #getName() name}.
     */
    @CheckForNull
    public static Label get(String l) {
        return Jenkins.get().getLabel(l);
    }

    /**
     * Parses the expression into a label expression tree.
     *
     * TODO: replace this with a real parser later
     *
     * @param labelExpression the label expression to be parsed
     * @throws IllegalArgumentException if the label expression cannot be parsed
     */
    public static Label parseExpression(@NonNull String labelExpression) {
        LabelExpressionLexer lexer = new LabelExpressionLexer(CharStreams.fromString(labelExpression));
        lexer.removeErrorListeners();
        lexer.addErrorListener(new JenkinsANTLRErrorListener());
        LabelExpressionParser parser = new LabelExpressionParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(new JenkinsANTLRErrorListener());
        return parser.expr().l;
    }

    /**
     * Collects all the atoms in the expression.
     */
    private static final LabelVisitor<Void, Set<LabelAtom>> ATOM_COLLECTOR = new LabelVisitor<>() {
        @Override
        public Void onAtom(LabelAtom a, Set<LabelAtom> param) {
            param.add(a);
            return null;
        }

        @Override
        public Void onParen(Paren p, Set<LabelAtom> param) {
            return p.base.accept(this, param);
        }

        @Override
        public Void onNot(Not p, Set<LabelAtom> param) {
            return p.base.accept(this, param);
        }

        @Override
        public Void onAnd(And p, Set<LabelAtom> param) {
            return onBinary(p, param);
        }

        @Override
        public Void onOr(Or p, Set<LabelAtom> param) {
            return onBinary(p, param);
        }

        @Override
        public Void onIff(Iff p, Set<LabelAtom> param) {
            return onBinary(p, param);
        }

        @Override
        public Void onImplies(Implies p, Set<LabelAtom> param) {
            return onBinary(p, param);
        }

        private Void onBinary(Binary b, Set<LabelAtom> param) {
            b.lhs.accept(this, param);
            b.rhs.accept(this, param);
            return null;
        }
    };
}
