package jenkins.util;

import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Used in conjunction with /lib/form/serverTcpPort tag to parse the submitted JSON back into a port number.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.445
 */
public class ServerTcpPort {
    private int value;
    private String type;

    @DataBoundConstructor
    public ServerTcpPort(int value, String type) {
        this.value = value;
        this.type = type;
    }
    
    public ServerTcpPort(JSONObject o) {
        type = o.getString("type");
        value = o.optInt("value");
    }

    /**
     * Parses the value back into the port number
     */
    public int getPort() {
        if (type.equals("fixed"))   return value;
        if (type.equals("random"))  return 0;
        return -1;
    }
}
