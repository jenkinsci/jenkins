/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
 * Copyright (c) 2011, Richard Mortimer
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
package hudson.util.xstream;

import com.google.common.collect.ImmutableList;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.XStreamException;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.collections.CollectionConverter;
import com.thoughtworks.xstream.converters.reflection.ReflectionProvider;
import com.thoughtworks.xstream.converters.reflection.SerializableConverter;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.mapper.Mapper;

import hudson.util.RobustReflectionConverter;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link ImmutableList} should convert like a list, instead of via serialization.
 *
 * @author Kohsuke Kawaguchi
 * @author Richard Mortimer
 */
public class ImmutableListConverter extends CollectionConverter {
    private final SerializableConverter sc;

    public ImmutableListConverter(XStream xs) {
        this(xs.getMapper(),xs.getReflectionProvider());
    }

    public ImmutableListConverter(Mapper mapper, ReflectionProvider reflectionProvider) {
        super(mapper);
        sc = new SerializableConverter(mapper,reflectionProvider);
    }

    @Override
    public boolean canConvert(Class type) {
        return ImmutableList.class.isAssignableFrom(type);
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        String resolvesTo = reader.getAttribute("resolves-to");
        if ("com.google.common.collect.ImmutableList$SerializedForm".equals(resolvesTo)) {
            // Skip into the elements element. This has the real children.
            List items = new ArrayList();
            if (reader.hasMoreChildren()) {
                reader.moveDown();
	            // read the individual items from xml into a list
	            while (reader.hasMoreChildren()) {
	                reader.moveDown();
	                try {
	                    Object item = readItem(reader, context, items);
	                    items.add(item);
	                } catch (XStreamException e) {
	                    RobustReflectionConverter.addErrorInContext(context, e);
	                } catch (LinkageError e) {
	                    RobustReflectionConverter.addErrorInContext(context, e);
	                }
	                reader.moveUp();
	            }

                // move back up past the elements element.
                reader.moveUp();
            }
            return ImmutableList.copyOf(items);
        } else {
            return ImmutableList.copyOf((List)super.unmarshal(reader, context));
        }
    }

    @Override
    protected Object createCollection(Class type) {
        return new ArrayList();
    }
}
