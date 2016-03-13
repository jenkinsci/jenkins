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

    private int minToShow;

    private List<Map<String, Serializable>> items;

    public Category(String id, String name, String description, String iconClassName, int weight, int minToShow,
                    List<Map<String, Serializable>> items) {
        this.id= id;
        this.name = name;
        this.description = description;
        this.iconClassName = iconClassName;
        this.weight = weight;
        this.minToShow = minToShow;
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
    public int getMinToShow() {
        return minToShow;
    }

    @Exported
    public List<Map<String, Serializable>> getItems() {
        return items;
    }

}
