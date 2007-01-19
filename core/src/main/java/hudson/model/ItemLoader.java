package hudson.model;

import com.thoughtworks.xstream.XStream;
import hudson.util.XStream2;
import hudson.XmlFile;

import java.io.File;
import java.io.IOException;

/**
 * Used to load {@link Item} implementation.
 *
 *
 * TODO: move this to {@link ItemGroup}?
 *
 * @author Kohsuke Kawaguchi
 */
public final class ItemLoader {
    /**
     * Loads a {@link Item} from a config file.
     *
     * @param dir
     *      The directory that contains the config file, not the config file itself.
     */
    public static Item load(File dir) throws IOException {
        Item item = (Item)getConfigFile(dir).read();
        item.onLoad(dir.getName());
        return item;
    }

    /**
     * The file we save our configuration.
     */
    static XmlFile getConfigFile(File dir) {
        return new XmlFile(XSTREAM,new File(dir,"config.xml"));
    }

    /**
     * The file we save our configuration.
     */
    public static XmlFile getConfigFile(Item item) {
        return getConfigFile(item.getRootDir());
    }

    /**
     * Used to load/save job configuration.
     *
     * When you extend {@link Job} in a plugin, try to put the alias so
     * that it produces a reasonable XML.
     */
    public static final XStream XSTREAM = new XStream2();

    static {
        XSTREAM.alias("project",Project.class);
    }
}
