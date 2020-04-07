package jenkins;



public class BreadCrumbItem {

    private String displayName;
    private boolean hasContextualMenu;


    public BreadCrumbItem getFirst() {
        // TO-DO
        return null;
    }

    public BreadCrumbItem getLast() {
        // TO-DO
        return null;
    }

    public void reNameBreadCrumb() {
        // TO-DO
    }

    public BreadCrumbItem getParentBreadCrumb() {
        // TO-DO
        return null;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean isHasContextualMenu() {
        return hasContextualMenu;
    }

    public void setHasContextualMenu(boolean hasContextualMenu) {
        this.hasContextualMenu = hasContextualMenu;
    }
}
