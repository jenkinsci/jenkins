/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package hudson.util;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.XStreamException;
import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.collections.CollectionConverter;
import com.thoughtworks.xstream.converters.reflection.ReflectionProvider;
import com.thoughtworks.xstream.converters.reflection.SerializableConverter;
import com.thoughtworks.xstream.core.ClassLoaderReference;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.mapper.Mapper;
import com.thoughtworks.xstream.security.InputManipulationException;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.diagnosis.OldDataMonitor;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Logger;
import jenkins.util.xstream.CriticalXStreamException;
import org.jvnet.tiger_types.Types;

/**
 * {@link CollectionConverter} that ignores {@link XStreamException}.
 *
 * <p>
 * This allows Hudson to load XML files that contain non-existent classes
 * (the expected scenario is that those classes belong to plugins that were unloaded.)
 *
 * @author Kohsuke Kawaguchi
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class RobustCollectionConverter extends CollectionConverter {
    private final SerializableConverter sc;
    /**
     * When available, this field holds the declared type of the collection being deserialized.
     */
    private final @CheckForNull Class<?> elementType;

    public RobustCollectionConverter(XStream xs) {
        this(xs.getMapper(), xs.getReflectionProvider(), null);
    }

    public RobustCollectionConverter(Mapper mapper, ReflectionProvider reflectionProvider) {
        this(mapper, reflectionProvider, null);
    }

    /**
     * Creates a converter that will validate the types of collection elements during deserialization.
     * <p>Elements with invalid types will be omitted from deserialized collections and may result in an
     * {@link OldDataMonitor} warning.
     * <p>This type checking currently uses the erasure of the type argument, so for example, the element type for a
     * {@code List<Optional<Integer>>} is just a raw {@code Optional}, so non-integer values inside of the optional
     * would still deserialize successfully and the resulting optional would be included in the list.
     *
     * @see RobustReflectionConverter#unmarshalField
     */
    public RobustCollectionConverter(Mapper mapper, ReflectionProvider reflectionProvider, Type collectionType) {
        super(mapper);
        sc = new SerializableConverter(mapper, reflectionProvider, new ClassLoaderReference(null));
        if (collectionType != null && Collection.class.isAssignableFrom(Types.erasure(collectionType))) {
            var baseType = Types.getBaseClass(collectionType, Collection.class);
            var typeArg = Types.getTypeArgument(baseType, 0, Object.class);
            this.elementType = Types.erasure(typeArg);
        } else {
            this.elementType = null;
        }
    }

    @Override
    public boolean canConvert(Class type) {
        return super.canConvert(type) || type == CopyOnWriteArrayList.class || type == CopyOnWriteArraySet.class;
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        // CopyOnWriteArrayList used to serialize as custom serialization,
        // so read it in a compatible fashion.
        String s = reader.getAttribute("serialization");
        if (s != null && s.equals("custom")) {
            return sc.unmarshal(reader, context);
        } else {
            return super.unmarshal(reader, context);
        }
    }

    @Override
    protected void populateCollection(HierarchicalStreamReader reader, UnmarshallingContext context, Collection collection) {
        while (reader.hasMoreChildren()) {
            reader.moveDown();
            try {
                Object item = readBareItem(reader, context, collection);
                if (elementType != null && item != null && !elementType.isInstance(item)) {
                    var exception = new ConversionException("Invalid type for collection element");
                    // c.f. TreeUnmarshaller.addInformationTo
                    exception.add("required-type", elementType.getName());
                    exception.add("class", item.getClass().getName());
                    exception.add("converter-type", getClass().getName());
                    reader.appendErrors(exception);
                    RobustReflectionConverter.addErrorInContext(context, exception);
                } else {
                    long nanoNow = System.nanoTime();
                    collection.add(item);
                    XStream2SecurityUtils.checkForCollectionDoSAttack(context, nanoNow);
                }
            } catch (CriticalXStreamException e) {
                throw e;
            } catch (InputManipulationException e) {
                Logger.getLogger(RobustCollectionConverter.class.getName()).warning(
                        "DoS detected and prevented. If the heuristic was too aggressive, " +
                                "you can customize the behavior by setting the hudson.util.XStream2.collectionUpdateLimit system property. " +
                                "See https://www.jenkins.io/redirect/xstream-dos-prevention for more information.");
                throw new CriticalXStreamException(e);
            } catch (XStreamException | LinkageError e) {
                RobustReflectionConverter.addErrorInContext(context, e);
            }
            reader.moveUp();
        }
    }

}
