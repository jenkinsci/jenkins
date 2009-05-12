package hudson.model;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.util.QuotedStringTokenizer;
import hudson.util.TextFile;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

import net.sf.json.JSONObject;

/**
 * Service for plugins to periodically retrieve update data files
 * (like the one in the update center) through browsers.
 *
 * <p>
 * Because the retrieval of the file goes through XmlHttpRequest,
 * we cannot reliably pass around binary.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class DownloadService extends PageDecorator {
    public DownloadService() {
        super(DownloadService.class);
    }

    /**
     * Builds up an HTML fragment that starts all the download jobs.
     */
    public String generateFragment() {
        StringBuilder buf = new StringBuilder();
        long now = System.currentTimeMillis();
        for (Downloadable d : Downloadable.all()) {
            if(d.getDue()<now) {
                buf.append("<script>downloadService.download(")
                   .append(QuotedStringTokenizer.quote(d.getId()))
                   .append(',')
                   .append(QuotedStringTokenizer.quote(d.getUrl()))
                   .append(',')
                   .append("{version:"+QuotedStringTokenizer.quote(Hudson.VERSION)+'}')
                   .append(',')
                   .append(QuotedStringTokenizer.quote(Stapler.getCurrentRequest().getContextPath()+'/'+getUrl()+"/byId/"+d.getId()+"/postBack"))
                   .append(',')
                   .append("null);</script>");
            }
        }
        return buf.toString();
    }

    /**
     * Gets {@link Downloadable} by its ID.
     * Used to bind them to URL.
     */
    public Downloadable getById(String id) {
        for (Downloadable d : Downloadable.all())
            if(d.getId().equals(id))
                return d;
        return null;
    }

    public static abstract class Downloadable implements ExtensionPoint {
        private final String id;
        private final String url;
        private final long interval;
        private volatile long due=0;

        /**
         *
         * @param url
         *      URL relative to {@link UpdateCenter#getUrl()}.
         *      So if this string is "foo.json", the ultimate URL will be
         *      something like "https://hudson.dev.java.net/foo.json"
         *
         *      For security and privacy reasons, we don't allow the retrieval
         *      from random locations.
         */
        protected Downloadable(String id, String url, long interval) {
            this.id = id;
            this.url = url;
            this.interval = interval;
        }

        public String getId() {
            return id;
        }

        /**
         * URL to download.
         */
        public String getUrl() {
            return Hudson.getInstance().getUpdateCenter().getUrl()+url;
        }

        /**
         * How often do we retrieve the new image?
         *
         * @return
         *      number of milliseconds between retrieval.
         */
        public long getInterval() {
            return interval;
        }

        /**
         * This is where the retrieved file will be stored.
         */
        public TextFile getDataFile() {
            return new TextFile(new File(Hudson.getInstance().getRootDir(),"updates/"+id));
        }

        /**
         * When shall we retrieve this file next time?
         */
        public long getDue() {
            if(due==0)
                // if the file doesn't exist, this code should result
                // in a very small (but >0) due value, which should trigger
                // the retrieval immediately.
                due = getDataFile().file.lastModified()+interval;
            return due;
        }

        /**
         * Loads the current file into JSON and returns it, or null
         * if no data exists.
         */
        public JSONObject getData() throws IOException {
            TextFile df = getDataFile();
            if(df.exists())
                return JSONObject.fromObject(df.read());
            return null;
        }

        /**
         * This is where the browser sends us the data. 
         */
        public void doPostBack(@QueryParameter String json) throws IOException {
            long dataTimestamp = System.currentTimeMillis();
            TextFile df = getDataFile();
            df.write(json);
            df.file.setLastModified(dataTimestamp);
            due = dataTimestamp+getInterval();
            LOGGER.info("Obtained the updated data file for "+id);
        }

        /**
         * Returns all the registered {@link Downloadable}s.
         */
        public static ExtensionList<Downloadable> all() {
            return Hudson.getInstance().getExtensionList(Downloadable.class);
        }

        private static final Logger LOGGER = Logger.getLogger(Downloadable.class.getName());
    }
}
