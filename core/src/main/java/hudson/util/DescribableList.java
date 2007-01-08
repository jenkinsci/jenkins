package hudson.util;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.collections.AbstractCollectionConverter;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.mapper.Mapper;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Persisted list of {@link Describable}s with some operations specific
 * to {@link Descriptor}s.
 *
 * @author Kohsuke Kawaguchi
 */
public class DescribableList<T extends Describable<T>, D extends Descriptor<T>> implements Iterable<T> {
    private final CopyOnWriteList<T> data = new CopyOnWriteList<T>();
    private Owner owner;

    private DescribableList() {
    }

    public DescribableList(Owner owner) {
        setOwner(owner);
    }

    public void setOwner(Owner owner) {
        this.owner = owner;
    }

    public void add(T item) throws IOException {
        data.add(item);
        owner.save();
    }

    public void remove(D descriptor) throws IOException {
        for (T t : data) {
            if(t.getDescriptor()==descriptor) {
                data.remove(t);
                owner.save();
                return;
            }
        }
    }

    public Iterator<T> iterator() {
        return data.iterator();
    }

    @SuppressWarnings("unchecked")
    public Map<D,T> toMap() {
        return (Map)Descriptor.toMap(data);
    }

    /**
     * Gets all the {@link Describable}s in an array.
     */
    public T[] toArray(T[] array) {
        return data.toArray(array);
    }

    /**
     * Rebuilds the list by creating a fresh instances from the submitted form.
     *
     * <p>
     * This method is almost always used by the owner.
     * This method does not invoke the save method.
     */
    public void rebuild(StaplerRequest req, List<Descriptor<T>> descriptors, String prefix) throws FormException {
        List<T> newList = new ArrayList<T>();

        for( int i=0; i< descriptors.size(); i++ ) {
            if(req.getParameter(prefix +i)!=null) {
                T instance = descriptors.get(i).newInstance(req);
                newList.add(instance);
            }
        }

        data.replaceBy(newList);
    }


    public interface Owner {
        /**
         * Called whenever the list is changed, so that it can be saved.
         */
        void save() throws IOException;
    }

    /**
     * {@link Converter} implementation for XStream.
     */
    public static final class ConverterImpl extends AbstractCollectionConverter {
        CopyOnWriteList.ConverterImpl copyOnWriteListConverter;

        public ConverterImpl(Mapper mapper) {
            super(mapper);
            copyOnWriteListConverter = new CopyOnWriteList.ConverterImpl(mapper());
        }

        public boolean canConvert(Class type) {
            return type==DescribableList.class;
        }

        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            for (Object o : (DescribableList) source)
                writeItem(o, context, writer);
        }

        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            CopyOnWriteList core = copyOnWriteListConverter.unmarshal(reader, context);

            DescribableList r = new DescribableList();
            r.data.replaceBy(core);
            return r;
        }
    }
}
