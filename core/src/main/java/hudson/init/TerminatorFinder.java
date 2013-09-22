package hudson.init;

import org.jvnet.hudson.reactor.Milestone;

/**
 * @author Kohsuke Kawaguchi
 */
public class TerminatorFinder extends TaskMethodFinder<Terminator> {
    public TerminatorFinder(ClassLoader cl) {
        super(Terminator.class, TermMilestone.class, cl);
    }
    
    @Override
    protected String displayNameOf(Terminator i) {
        return i.displayName();
    }

    @Override
    protected String[] requiresOf(Terminator i) {
        return i.requires();
    }

    @Override
    protected String[] attainsOf(Terminator i) {
        return i.attains();
    }

    @Override
    protected Milestone afterOf(Terminator i) {
        return i.after();
    }

    @Override
    protected Milestone beforeOf(Terminator i) {
        return i.before();
    }

    /**
     * Termination code is never fatal.
     */
    @Override
    protected boolean fatalOf(Terminator i) {
        return false;
    }
}
