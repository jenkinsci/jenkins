package hudson.agents;

/**
 * Additional convenience implementation on top of {@link Cloud} that are likely useful to
 * typical {@link Cloud} implementations.
 *
 * <p>
 * Whereas {@link Cloud} is the contract between the rest of Jenkins and a cloud implementation,
 * this class focuses on providing a convenience to minimize the effort it takes to integrate
 * a new cloud to Jenkins.
 *
 * @author Kohsuke Kawaguchi
 * @see AbstractCloudAgent
 */
public abstract class AbstractCloudImpl extends Cloud {
    /**
     * Upper bound on how many instances we may provision.
     */
    private int instanceCap;

    protected AbstractCloudImpl(String name, String instanceCapStr) {
        super(name);

        setInstanceCapStr(instanceCapStr);
    }

    protected void setInstanceCapStr(String value) {
        if (value == null || value.isEmpty())
            this.instanceCap = Integer.MAX_VALUE;
        else
            this.instanceCap = Integer.parseInt(value);
    }

    /**
     * Gets the instance cap as string. Used primarily for form binding.
     */
    public String getInstanceCapStr() {
        if (instanceCap == Integer.MAX_VALUE)
            return "";
        else
            return String.valueOf(instanceCap);
    }

    /**
     * Gets the instance cap as int, where the capless is represented as {@link Integer#MAX_VALUE}
     */
    public int getInstanceCap() {
        return instanceCap;
    }

    protected void setInstanceCap(int v) {
        this.instanceCap = v;
    }
}
