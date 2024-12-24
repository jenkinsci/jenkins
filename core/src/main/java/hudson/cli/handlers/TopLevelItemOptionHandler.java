package hudson.cli.handlers;

import hudson.cli.declarative.OptionHandlerExtension;
import hudson.model.TopLevelItem;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.Setter;

/**
 * Refers to {@link TopLevelItem} by its name.
 *
 * @author Kohsuke Kawaguchi
 */
@OptionHandlerExtension
public class TopLevelItemOptionHandler extends GenericItemOptionHandler<TopLevelItem> {
    public TopLevelItemOptionHandler(CmdLineParser parser, OptionDef option, Setter<TopLevelItem> setter) {
        super(parser, option, setter);
    }

    @Override protected Class<TopLevelItem> type() {
        return TopLevelItem.class;
    }

    @Override
    public String getDefaultMetaVariable() {
        return "JOB"; // TODO or should we pick up default value, ITEM?
    }
}
