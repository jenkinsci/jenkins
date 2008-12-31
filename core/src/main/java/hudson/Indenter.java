package hudson;

import hudson.model.Job;

/**
 * Used by <tt>projectView.jelly</tt> to indent modules.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class Indenter<J extends Job> {
    protected abstract int getNestLevel(J job);

    public final String getCss(J job) {
        return "padding-left: "+getNestLevel(job)*2+"em";
    }

    public final String getRelativeShift(J job) {
        int i = getNestLevel(job);
        if(i==0)    return null;
        return "position:relative; left: "+ i *2+"em";
    }
}
