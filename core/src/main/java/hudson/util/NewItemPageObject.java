package hudson.util;

import hudson.model.TopLevelItemDescriptor;
import jenkins.model.TopLevelItemDescriptorCategory;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
public class NewItemPageObject {
    @Exported
    public final List<Tile> tiles;
    
    @ExportedBean
    public class Tile {
        public final TopLevelItemDescriptorCategory category;
        public final List<TopLevelItemDescriptor> descriptors = new ArrayList<TopLevelItemDescriptor>();

        public Tile(TopLevelItemDescriptorCategory category) {
            this.category = category;
        }
    }

    public NewItemPageObject() {
        Map<TopLevelItemDescriptorCategory,Tile> m = new HashMap<TopLevelItemDescriptorCategory,Tile>(); 
        
        for (TopLevelItemDescriptor d : TopLevelItemDescriptor.all()) {
            TopLevelItemDescriptorCategory c = d.getCategory();
            Tile t = m.get(c);
            if (t==null)    t = new Tile(c);
            m.put(c,t);
            t.descriptors.add(d);
        }
        
        tiles = new ArrayList<Tile>(m.values());
        Collections.sort(tiles, new Comparator<Tile>() {
            @Override
            public int compare(Tile o1, Tile o2) {
                double d = (o1.category.getOrdinal() - o2.category.getOrdinal());
                if (d<0)    return -1;
                if (d>0)    return 1;
                return 0;
            }
        });
    }
}
