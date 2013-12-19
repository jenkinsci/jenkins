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

import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.collections.CollectionConverter;
import com.thoughtworks.xstream.converters.reflection.ReflectionProvider;
import com.thoughtworks.xstream.converters.reflection.SerializableConverter;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.mapper.Mapper;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.XStreamException;

import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;


/**
 * {@link CollectionConverter} that ignores {@link XStreamException}.
 *
 * <p>
 * This allows Hudson to load XML files that contain non-existent classes
 * (the expected scenario is that those classes belong to plugins that were unloaded.) 
 *
 * @author Kohsuke Kawaguchi
 */
public class RobustCollectionConverter extends CollectionConverter {
    private final SerializableConverter sc;

    public RobustCollectionConverter(XStream xs) {
        this(xs.getMapper(),xs.getReflectionProvider());
    }

    public RobustCollectionConverter(Mapper mapper, ReflectionProvider reflectionProvider) {
        super(mapper);
        sc = new SerializableConverter(mapper,reflectionProvider);
    }

    @Override
    public boolean canConvert(Class type) {
        return super.canConvert(type) || type==CopyOnWriteArrayList.class || type==CopyOnWriteArraySet.class;
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        // CopyOnWriteArrayList used to serialize as custom serialization,
        // so read it in a compatible fashion.
        String s = reader.getAttribute("serialization");
        if(s!=null && s.equals("custom")) {
            return sc.unmarshal(reader,context);
        } else {
            return super.unmarshal(reader, context);
        }
    }

    @Override
    protected void populateCollection(HierarchicalStreamReader reader, UnmarshallingContext context, Collection collection) {
        while (reader.hasMoreChildren()) {
            reader.moveDown();
            try {
                Object item = readItem(reader, context, collection);
                collection.add(item);
            } catch (XStreamException e) {
                RobustReflectionConverter.addErrorInContext(context, e);
            } catch (LinkageError e) {
                RobustReflectionConverter.addErrorInContext(context, e);
            }
            reader.moveUp();
        }
    }

}
