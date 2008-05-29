package hudson.model;

import hudson.util.TextFile;
import static hudson.util.TimeUnit2.DAYS;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

/**
 * Controls update center capability.
 *
 * @author Kohsuke Kawaguchi
 */
public class UpdateCenter {
    private long dataTimestamp = -1;

    /**
     * When was the last time we asked a browser to check the data for us?
     *
     * <p>
     * There's normally some delay between when we send HTML that includes the check code,
     * until we get the data back, so this variable is used to avoid asking too many browseres
     * all at once.
     */
    private volatile long lastAttempt = -1;

    /**
     * Returns true if it's time for us to check for new version.
     */
    public boolean isDue() {
        long now = System.currentTimeMillis();
        boolean due = now - dataTimestamp > DAY && now - lastAttempt > 15000;
        if(due)     lastAttempt = now;
        return due;
    }

    /**
     * This is the endpoint that receives the update center data file from the browser.
     */
    public void doPostBack(StaplerRequest req) throws IOException {
        dataTimestamp = System.currentTimeMillis();
        String p = req.getParameter("json");
        JSONObject o = JSONObject.fromObject(p);
                
        int v = o.getInt("updateCenterVersion");
        if(v !=1) {
            LOGGER.warning("Unrecognized update center version: "+v);
            return;
        }

        LOGGER.info("Obtained the latest update center data file");
        getDataFile().write(p);
    }

    /**
     * Loads the update center data, if any.
     *
     * @return  null if no data is available.
     */
    public Data getData() throws IOException {
        TextFile df = getDataFile();
        if(df.exists()) {
            return new Data(JSONObject.fromObject(df.read()));
        } else {
            return null;
        }
    }

    /**
     * This is where we store the update center data.
     */
    private TextFile getDataFile() {
        return new TextFile(new File(Hudson.getInstance().root,"update-center.json"));
    }

    /**
     * In-memory representation of the update center data.
     */
    public static final class Data {
        /**
         * The latest hudson.war.
         */
        public final Entry core;
        /**
         * Plugins in the official repository, keyed by their artifact IDs.
         */
        public final Map<String,Plugin> plugins = new HashMap<String,Plugin>();
        
        Data(JSONObject o) {
            core = new Entry(o.getJSONObject("core"));
            for(Map.Entry<String,JSONObject> e : (Set<Map.Entry<String,JSONObject>>)o.getJSONObject("plugins").entrySet()) {
                plugins.put(e.getKey(),new Plugin(e.getValue()));
            }
        }
    }

    public static class Entry {
        /**
         * Artifact ID.
         */
        public final String name;
        /**
         * The version.
         */
        public final String version;
        /**
         * Download URL.
         */
        public final String url;

        public Entry(JSONObject o) {
            this.name = o.getString("name");
            this.version = o.getString("version");
            this.url = o.getString("url");
        }
    }

    public static final class Plugin extends Entry {
        /**
         * Optional URL to the Wiki page that discusses this plugin.
         */
        public final String wiki;
        /**
         * Human readable title of the plugin, taken from Wiki page.
         */
        public final String title;

        @DataBoundConstructor
        public Plugin(JSONObject o) {
            super(o);
            this.wiki = get(o,"wiki");
            this.title = get(o,"title");
        }

        private String get(JSONObject o, String prop) {
            if(o.has(prop))
                return o.getString(prop);
            else
                return null;
        }
    }

    private static final long DAY = DAYS.toMillis(1);

    private static final Logger LOGGER = Logger.getLogger(UpdateCenter.class.getName());
}
