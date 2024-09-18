/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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

import com.thoughtworks.xstream.XStreamException;
import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.collections.MapConverter;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.mapper.Mapper;
import com.thoughtworks.xstream.security.InputManipulationException;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.logging.Logger;
import jenkins.util.xstream.CriticalXStreamException;
import org.jvnet.tiger_types.Types;

/**
 * Loads a {@link Map} while tolerating read errors on its keys and values.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
final class RobustMapConverter extends MapConverter {
    private static final Object ERROR = new Object();

    /**
     * When available, this field holds the declared type of the keys of the map being deserialized.
     * This is used to ensure that deserialized keys have the expected type.
     */
    // See note above RobustCollectionConverter.elementType for limitations regarding non-concrete parameterized types.
    private final @CheckForNull Class<?> keyType;

    /**
     * When available, this field holds the declared type of the values of the map being deserialized.
     * This is used to ensure that deserialized values have the expected type.
     */
    private final @CheckForNull Class<?> valueType;

    RobustMapConverter(Mapper mapper) {
        this(mapper, null);
    }

    RobustMapConverter(Mapper mapper, Type mapType) {
        super(mapper);
        if (mapType != null && Map.class.isAssignableFrom(Types.erasure(mapType))) {
            var baseType = Types.getBaseClass(mapType, Map.class);
            var keyTypeArg = Types.getTypeArgument(baseType, 0, Object.class);
            this.keyType = Types.erasure(keyTypeArg);
            var valueTypeArg = Types.getTypeArgument(baseType, 1, Object.class);
            this.valueType = Types.erasure(valueTypeArg);
        } else {
            this.keyType = null;
            this.valueType = null;
        }
    }

    @Override protected void putCurrentEntryIntoMap(HierarchicalStreamReader reader, UnmarshallingContext context, Map map, Map target) {
        Object key = read(reader, context, map, keyType);
        Object value = read(reader, context, map, valueType);
        if (key != ERROR && value != ERROR) {
            try {
                long nanoNow = System.nanoTime();
                target.put(key, value);
                XStream2SecurityUtils.checkForCollectionDoSAttack(context, nanoNow);
            } catch (InputManipulationException e) {
                Logger.getLogger(RobustMapConverter.class.getName()).warning(
                        "DoS detected and prevented. If the heuristic was too aggressive, " +
                                "you can customize the behavior by setting the hudson.util.XStream2.collectionUpdateLimit system property. " +
                                "See https://www.jenkins.io/redirect/xstream-dos-prevention for more information.");
                throw new CriticalXStreamException(e);
            }
        }
    }

    private Object read(HierarchicalStreamReader reader, UnmarshallingContext context, Map map, @CheckForNull Class<?> expectedType) {
        if (!reader.hasMoreChildren()) {
            var exception = new ConversionException("Invalid map entry");
            reader.appendErrors(exception);
            RobustReflectionConverter.addErrorInContext(context, exception);
            return ERROR;
        }
        reader.moveDown();
        try {
            var object = readBareItem(reader, context, map);
            try {
                if (expectedType != null) {
                    // When possible, disallow invalid keys and values.
                    expectedType.cast(object);
                }
                return object;
            } catch (ClassCastException e) {
                var exception = new ConversionException("Invalid type for map entry key/value", e);
                reader.appendErrors(exception);
                RobustReflectionConverter.addErrorInContext(context, exception);
                return ERROR;
            }
        } catch (CriticalXStreamException x) {
            throw x;
        } catch (XStreamException | LinkageError x) {
            RobustReflectionConverter.addErrorInContext(context, x);
            return ERROR;
        } finally {
            reader.moveUp();
        }
    }

}
