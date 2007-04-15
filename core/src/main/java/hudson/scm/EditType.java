package hudson.scm;

import org.kohsuke.stapler.export.CustomExposureBean;

/**
 * Designates the SCM operation.
 *
 * @author Kohsuke Kawaguchi
 */
public final class EditType implements CustomExposureBean {
    private String name;
    private String description;

    public EditType(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Object toExposedObject() {
        return name;
    }

    public static final EditType ADD = new EditType("add","The file was added");
    public static final EditType EDIT = new EditType("edit","The file was modified");
    public static final EditType DELETE = new EditType("delete","The file was removed");
}
