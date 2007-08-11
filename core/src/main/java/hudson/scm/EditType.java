package hudson.scm;

import org.kohsuke.stapler.export.CustomExportedBean;

import java.util.List;
import java.util.Collections;
import java.util.Arrays;

/**
 * Designates the SCM operation.
 *
 * @author Kohsuke Kawaguchi
 */
public final class EditType implements CustomExportedBean {
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

    public String toExportedObject() {
        return name;
    }

    public static final EditType ADD = new EditType("add","The file was added");
    public static final EditType EDIT = new EditType("edit","The file was modified");
    public static final EditType DELETE = new EditType("delete","The file was removed");

    public static final List<EditType> ALL = Collections.unmodifiableList(Arrays.asList(ADD,EDIT,DELETE));
}
