package hudson.util;

import com.thoughtworks.xstream.converters.collections.CollectionConverter;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.mapper.Mapper;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.alias.CannotResolveClassException;

import java.util.Collection;

/**
 * {@link CollectionConverter} that ignores {@link CannotResolveClassException}.
 *
 * <p>
 * This allows Hudson to load XML files that contain non-existent classes
 * (the expected scenario is that those classes belong to plugins that were unloaded.) 
 *
 * @author Kohsuke Kawaguchi
 */
public class RobustCollectionConverter extends CollectionConverter {
    public RobustCollectionConverter(Mapper mapper) {
        super(mapper);
    }

    protected void populateCollection(HierarchicalStreamReader reader, UnmarshallingContext context, Collection collection) {
        while (reader.hasMoreChildren()) {
            reader.moveDown();
            try {
                Object item = readItem(reader, context, collection);
                collection.add(item);
            } catch (CannotResolveClassException e) {
                System.err.println("failed to locate class: "+e);
            }
            reader.moveUp();
        }
    }

}
