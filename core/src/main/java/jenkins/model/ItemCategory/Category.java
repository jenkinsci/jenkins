package jenkins.model.ItemCategory;

import hudson.model.TopLevelItem;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.io.Serializable;
import java.util.List;

/**
 * Represents an {$link ItemCategory} and its {@link TopLevelItem}s.
 */
@ExportedBean
public class Category implements Serializable {

    private String id;

    private String name;

    private String description;

    private String iconClassName;

    private List<String> items;

    public Category(String id, String name, String description, String iconClassName, List<String> items) {
        this.id= id;
        this.name = name;
        this.description = description;
        this.iconClassName = iconClassName;
        this.items = items;
    }

    @Exported
    public String getId() {
        return id;
    }

    @Exported
    public String getName() {
        return name;
    }

    @Exported
    public String getDescription() {
        return description;
    }

    @Exported
    public String getIconClassName() {
        return iconClassName;
    }

    @Exported
    public List<String> getItems() {
        return items;
    }

}
