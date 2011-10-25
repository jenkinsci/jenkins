package hudson.cli.handlers;

import hudson.model.AbstractProject;
import jenkins.model.Jenkins;
import hudson.model.TopLevelItem;
import org.kohsuke.MetaInfServices;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;

/**
 * Refers to {@link TopLevelItem} by its name.
 *
 * @author Kohsuke Kawaguchi
 */
@MetaInfServices
public class TopLevelItemOptionHandler extends OptionHandler<TopLevelItem> {
    public TopLevelItemOptionHandler(CmdLineParser parser, OptionDef option, Setter<TopLevelItem> setter) {
        super(parser, option, setter);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public int parseArguments(Parameters params) throws CmdLineException {
        Jenkins h = Jenkins.getInstance();
        String src = params.getParameter(0);

        TopLevelItem s = h.getItem(src);
        if (s==null) {
            AbstractProject nearest = AbstractProject.findNearest(src);
            if (nearest!=null)
                throw new CmdLineException(owner, "No such job '"+src+"' perhaps you meant '"+ nearest.getFullName() +"'?");
            else
                throw new CmdLineException(owner, "No such job '"+src+"'");
        }
            
        setter.addValue(s);
        return 1;
    }

    @Override
    public String getDefaultMetaVariable() {
        return "JOB";
    }
}
