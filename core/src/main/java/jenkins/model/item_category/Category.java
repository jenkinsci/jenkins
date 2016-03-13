package jenkins.model.item_category;

import hudson.model.TopLevelItem;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Represents an {$link item_category} and its {@link TopLevelItem}s.
 */
@ExportedBean
public class Category implements Serializable {

    private String id;

    private String name;

    private String description;

    private String iconClassName;

    private int weight;

    private List<Map<String, Object>> items;

    public Category(String id, String name, String description, String iconClassName, int weight, List<Map<String, Object>> items) {
        this.id= id;
        this.name = name;
        this.description = description;
        this.iconClassName = iconClassName;
        this.weight = weight;
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
    public int getWeight() {
        return weight;
    }

    @Exported
    public List<Map<String, Object>> getItems() {
        return items;
    }

}
