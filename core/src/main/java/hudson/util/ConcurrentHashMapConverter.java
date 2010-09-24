/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
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
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.collections.MapConverter;
import com.thoughtworks.xstream.converters.reflection.ReflectionProvider;
import com.thoughtworks.xstream.converters.reflection.SerializableConverter;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.mapper.Mapper;

import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link ConcurrentHashMap} should convert like a map, instead of via serialization.
 *
 * @author Kohsuke Kawaguchi
 */
public class ConcurrentHashMapConverter extends MapConverter {
    private final SerializableConverter sc;

    public ConcurrentHashMapConverter(XStream xs) {
        this(xs.getMapper(),xs.getReflectionProvider());
    }

    public ConcurrentHashMapConverter(Mapper mapper, ReflectionProvider reflectionProvider) {
        super(mapper);
        sc = new SerializableConverter(mapper,reflectionProvider);
    }

    @Override
    public boolean canConvert(Class type) {
        return type==ConcurrentHashMap.class;
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        // ConcurrentHashMap used to serialize as custom serialization,
        // so read it in a compatible fashion.
        String s = reader.getAttribute("serialization");
        if(s!=null && s.equals("custom")) {
            return sc.unmarshal(reader,context);
        } else {
            return super.unmarshal(reader, context);
        }
    }
}
