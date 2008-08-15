package hudson.util;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.collections.AbstractCollectionConverter;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.mapper.Mapper;
import hudson.model.*;
import hudson.model.Descriptor.FormException;
import hudson.tasks.Builder;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;
import org.apache.maven.model.Dependency;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.security.acl.Owner;

/**
 * Persisted list of {@link Describable}s with some operations specific
 * to {@link Descriptor}s.
 *
 * <p>
 * This class allows multiple instances of the same descriptor. Some clients
 * use this semantics, while other clients use it as "up to one instance per
 * one descriptor" model.
 *
 * Some of the methods defined in this class only makes sense in the latter model,
 * such as {@link #remove(Descriptor)}.
 *
 * @author Kohsuke Kawaguchi
 */
public class DescribableList<T extends Describable<T>, D extends Descriptor<T>> implements Iterable<T> {
    private final CopyOnWriteList<T> data = new CopyOnWriteList<T>();
    private Saveable owner;

    private DescribableList() {
    }

    /**
     * @deprecated
     *      Use {@link #DescribableList(Saveable)} 
     */
    public DescribableList(Owner owner) {
        setOwner(owner);
    }

    public DescribableList(Saveable owner) {
        setOwner(owner);
    }

    /**
     * @deprecated
     *      Use {@link #setOwner(Saveable)}
     */
    public void setOwner(Owner owner) {
        this.owner = owner;
    }

    public void setOwner(Saveable owner) {
        this.owner = owner;
    }

    public void add(T item) throws IOException {
        data.add(item);
        owner.save();
    }

    public T get(D descriptor) {
        for (T t : data)
            if(t.getDescriptor()==descriptor)
                return t;
        return null;
    }

    public boolean contains(D d) {
        return get(d)!=null;
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
     * Returns the snapshot view of instances as list.
     */
    public List<T> toList() {
        return data.getView();
    }

    /**
     * Gets all the {@link Describable}s in an array.
     */
    public T[] toArray(T[] array) {
        return data.toArray(array);
    }

    public void addAllTo(Collection<? super T> dst) {
        data.addAllTo(dst);
    }

    /**
     * Rebuilds the list by creating a fresh instances from the submitted form.
     *
     * <p>
     * This method is almost always used by the owner.
     * This method does not invoke the save method.
     *
     * @param json
     *      Structured form data that includes the data for nested descriptor list.
     */
    public void rebuild(StaplerRequest req, JSONObject json, List<? extends Descriptor<T>> descriptors, String prefix) throws FormException {
        List<T> newList = new ArrayList<T>();

        for( int i=0; i< descriptors.size(); i++ ) {
            String name = prefix + i;
            if(json.has(name)) {
                T instance = descriptors.get(i).newInstance(req,json.getJSONObject(name));
                newList.add(instance);
            }
        }

        data.replaceBy(newList);
    }

    /**
     * Rebuilds the list by creating a fresh instances from the submitted form.
     *
     * <p>
     * This version works with the the &lt;f:hetero-list> UI tag, where the user
     * is allowed to create multiple instances of the same descriptor. Order is also
     * significant.
     */
    public void rebuildHetero(StaplerRequest req, JSONObject formData, Collection<Descriptor<T>> descriptors, String key) throws FormException {
        data.replaceBy(Descriptor.newInstancesFromHeteroList(req,formData,key,descriptors));
    }

    /**
     * Picks up {@link DependecyDeclarer}s and allow it to build dependencies.
     */
    public void buildDependencyGraph(AbstractProject owner,DependencyGraph graph) {
        for (Object o : this) {
            if (o instanceof DependecyDeclarer) {
                DependecyDeclarer dd = (DependecyDeclarer) o;
                dd.buildDependencyGraph(owner,graph);
            }
        }
    }

    /**
     * @deprecated 
     *      Just implement {@link Saveable}.
     */
    public interface Owner extends Saveable {
    }

    /**
     * {@link Converter} implementation for XStream.
     *
     * Serializaion form is compatible with plain {@link List}.
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
