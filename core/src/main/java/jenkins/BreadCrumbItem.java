package jenkins;



public class BreadCrumbItem {

    private String displayName;
    private boolean hasContextualMenu;
    private BreadCrumb breadCrumb;


    public BreadCrumbItem getFirst() {
        return breadCrumb.generateBreadCrumbs().get(0);
    }

    public BreadCrumbItem getLast() {
        int sizeofBreadCrumbList = breadCrumb.generateBreadCrumbs().size();
        return breadCrumb.generateBreadCrumbs().get(sizeofBreadCrumbList - 1);
    }

    public void reNameBreadCrumb(int index, String name) {
        // RenameBreadCrumb using index, for now we just want to rename the first breadcrumb
        BreadCrumbItem breadCrumbItem= breadCrumb.generateBreadCrumbs().get(index);
        breadCrumbItem.setDisplayName(name);
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
