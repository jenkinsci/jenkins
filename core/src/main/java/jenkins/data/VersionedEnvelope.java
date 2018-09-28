package jenkins.data;

import java.util.List;

/**
 * Wrapper for in/out messages.
 * It adds the API version and the effective content of the request/response.
 */
public class VersionedEnvelope<T> {
    /*
        This is the envelope format
        {
          version: 1,
          data:[{
            ...
          }]
        }
     */
    public static final String DEFAULT_VERSION = "1";
    private String version = DEFAULT_VERSION;
    private List<T> data;

    /**
     * Creates a versioned resource using {@code data} and the {@link #DEFAULT_VERSION}.
     *
     * @param data the data object
     */
    public VersionedEnvelope(List<T> data) {
        this.data = data;
    }

    /**
     * Creates a versioned resource using {@code data} and the given {@code version}.
     *
     * @param version a specific API version
     * @param data    the data object
     */
    public VersionedEnvelope(String version, List<T> data) {
        if (version == null) {
            throw new IllegalArgumentException("version has to be an integer, found null");
        }
        try {
            Integer.parseInt(version);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("version has to be an integer, found " + version);
        }
        this.version = version;
        this.data = data;
    }

    public VersionedEnvelope(int version, List<T> data) {
        this.version = Integer.toString(version);
        this.data = data;
    }

    public List<T> getData() {
        return data;
    }

    public void setData(List<T> data) {
        this.data = data;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}