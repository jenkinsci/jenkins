package hudson.cli.handlers;

import hudson.model.AbstractProject;
import hudson.model.Hudson;
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
    public int parseArguments(Parameters params) throws CmdLineException {
        Hudson h = Hudson.getInstance();
        String src = params.getParameter(0);

        TopLevelItem s = h.getItem(src);
        if (s==null)
            throw new CmdLineException(owner, "No such job '"+src+"' perhaps you meant "+ AbstractProject.findNearest(src)+"?");
        setter.addValue(s);
        return 1;
    }

    @Override
    public String getDefaultMetaVariable() {
        return "JOB";
    }
}
