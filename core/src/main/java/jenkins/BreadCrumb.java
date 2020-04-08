package jenkins;

import hudson.model.ModelObject;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.ModelObjectWithContextMenu;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.Stapler;

public class BreadCrumb {

    public static boolean isModel(Object o) {
        return o instanceof ModelObject;
    }

    public static boolean isModelWithContextMenu(Object o) {
        return o instanceof ModelObjectWithContextMenu;
    }

    public List<BreadCrumbItem> generateBreadCrumbs() {
        List<BreadCrumbItem> breadCrumbItemList = new ArrayList<>();
        List list = Stapler.getCurrentRequest().getAncestors();
        for( int i=list.size()-1; i>=0; i-- ) {
            BreadCrumbItem breadCrumbItem = new BreadCrumbItem();
            Ancestor anc = (Ancestor) list.get(i);
            if(isModel(anc) & !anc.getPrev().getFullUrl().equals(anc.getUrl())) {
                String displayName = anc.getObject().getClass().getSimpleName();
                breadCrumbItem.setDisplayName(displayName);
                breadCrumbItem.setHasContextualMenu(isModelWithContextMenu(anc.getObject()));
            }
        }
        return breadCrumbItemList;
    }
}
