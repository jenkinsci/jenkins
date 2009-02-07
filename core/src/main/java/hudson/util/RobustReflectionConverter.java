/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.converters.SingleValueConverter;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.reflection.ReflectionConverter;
import com.thoughtworks.xstream.converters.reflection.ReflectionProvider;
import com.thoughtworks.xstream.converters.reflection.SerializationMethodInvoker;
import com.thoughtworks.xstream.converters.reflection.ObjectAccessException;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;
import com.thoughtworks.xstream.converters.reflection.NonExistentFieldException;
import com.thoughtworks.xstream.core.util.Primitives;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.ExtendedHierarchicalStreamWriterHelper;
import com.thoughtworks.xstream.mapper.Mapper;
import com.thoughtworks.xstream.mapper.CannotResolveClassException;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Custom {@link ReflectionConverter} that handle errors more gracefully.
 *
 * <ul>
 * <li>If the field is missing, the value is ignored instead of causing an error.
 *     This makes evolution easy.
 * <li>If the type found in XML is no longer available, the element is skipped
 *     instead of causing an error.
 * </ul>
 *
 */
public class RobustReflectionConverter implements Converter {

    protected final ReflectionProvider reflectionProvider;
    protected final Mapper mapper;
    protected transient SerializationMethodInvoker serializationMethodInvoker;
    private transient ReflectionProvider pureJavaReflectionProvider;

    public RobustReflectionConverter(Mapper mapper, ReflectionProvider reflectionProvider) {
        this.mapper = mapper;
        this.reflectionProvider = reflectionProvider;
        serializationMethodInvoker = new SerializationMethodInvoker();
    }

    public boolean canConvert(Class type) {
        return true;
    }

    public void marshal(Object original, final HierarchicalStreamWriter writer, final MarshallingContext context) {
        final Object source = serializationMethodInvoker.callWriteReplace(original);

        if (source.getClass() != original.getClass()) {
            writer.addAttribute(mapper.aliasForAttribute("resolves-to"), mapper.serializedClass(source.getClass()));
        }

        doMarshal(source, writer, context);
    }

    protected void doMarshal(final Object source, final HierarchicalStreamWriter writer, final MarshallingContext context) {
        final Set seenFields = new HashSet();
        final Set seenAsAttributes = new HashSet();

        // Attributes might be preferred to child elements ...
         reflectionProvider.visitSerializableFields(source, new ReflectionProvider.Visitor() {
            public void visit(String fieldName, Class type, Class definedIn, Object value) {
                SingleValueConverter converter = mapper.getConverterFromItemType(fieldName, type);
                if (converter == null) {
                    converter = mapper.getConverterFromItemType(type);
                }
                if (converter != null) {
                    if (value != null) {
                        final String str = converter.toString(value);
                        if (str != null) {
                            writer.addAttribute(mapper.aliasForAttribute(fieldName), str);
                        }
                    }
                    seenAsAttributes.add(fieldName);
                }
            }
        });

        // Child elements not covered already processed as attributes ...
        reflectionProvider.visitSerializableFields(source, new ReflectionProvider.Visitor() {
            public void visit(String fieldName, Class fieldType, Class definedIn, Object newObj) {
                if (!seenAsAttributes.contains(fieldName) && newObj != null) {
                    Mapper.ImplicitCollectionMapping mapping = mapper.getImplicitCollectionDefForFieldName(source.getClass(), fieldName);
                    if (mapping != null) {
                        if (mapping.getItemFieldName() != null) {
                            Collection list = (Collection) newObj;
                            for (Iterator iter = list.iterator(); iter.hasNext();) {
                                Object obj = iter.next();
                                writeField(fieldName, mapping.getItemFieldName(), mapping.getItemType(), definedIn, obj);
                            }
                        } else {
                            context.convertAnother(newObj);
                        }
                    } else {
                        writeField(fieldName, fieldName, fieldType, definedIn, newObj);
                        seenFields.add(fieldName);
                    }
                }
            }

            private void writeField(String fieldName, String aliasName, Class fieldType, Class definedIn, Object newObj) {
                try {
                    if (!mapper.shouldSerializeMember(definedIn, aliasName)) {
                        return;
                    }
                    ExtendedHierarchicalStreamWriterHelper.startNode(writer, mapper.serializedMember(definedIn, aliasName), fieldType);

                    Class actualType = newObj.getClass();

                    Class defaultType = mapper.defaultImplementationOf(fieldType);
                    if (!actualType.equals(defaultType)) {
                        writer.addAttribute(mapper.aliasForAttribute("class"), mapper.serializedClass(actualType));
                    }

                    if (seenFields.contains(aliasName)) {
                        writer.addAttribute(mapper.aliasForAttribute("defined-in"), mapper.serializedClass(definedIn));
                    }

                    Field field = reflectionProvider.getField(definedIn,fieldName);
                    marshallField(context, newObj, field);
                    writer.endNode();
                } catch (RuntimeException e) {
                    // intercept an exception so that the stack trace shows how we end up marshalling the object in question
                    throw new RuntimeException("Failed to serialize "+definedIn.getName()+"#"+fieldName+" for "+source.getClass(),e);
                }
            }

        });
    }

    protected void marshallField(final MarshallingContext context, Object newObj, Field field) {
        context.convertAnother(newObj);
    }

    public Object unmarshal(final HierarchicalStreamReader reader, final UnmarshallingContext context) {
        Object result = instantiateNewInstance(reader, context);
        result = doUnmarshal(result, reader, context);
        return serializationMethodInvoker.callReadResolve(result);
    }

    public Object doUnmarshal(final Object result, final HierarchicalStreamReader reader, final UnmarshallingContext context) {
        final SeenFields seenFields = new SeenFields();
        Iterator it = reader.getAttributeNames();

        // Process attributes before recursing into child elements.
        while (it.hasNext()) {
            String attrAlias = (String) it.next();
            String attrName = mapper.attributeForAlias(attrAlias);
            Class classDefiningField = determineWhichClassDefinesField(reader);
            boolean fieldExistsInClass = fieldDefinedInClass(result, attrName);
            if (fieldExistsInClass) {
                SingleValueConverter converter = mapper.getConverterFromAttribute(attrName);
                Class type = reflectionProvider.getFieldType(result, attrName, classDefiningField);
                if (converter == null) {
                    converter = mapper.getConverterFromItemType(type);
                }
                if (converter != null) {
                    Object value = converter.fromString(reader.getAttribute(attrAlias));
                    if (type.isPrimitive()) {
                        type = Primitives.box(type);
                    }
                    if (value != null && !type.isAssignableFrom(value.getClass())) {
                        throw new ConversionException("Cannot convert type " + value.getClass().getName() + " to type " + type.getName());
                    }
                    reflectionProvider.writeField(result, attrName, value, classDefiningField);
                    seenFields.add(classDefiningField, attrName);
                }
            }
        }

        Map implicitCollectionsForCurrentObject = null;
        while (reader.hasMoreChildren()) {
            reader.moveDown();

            try {
                String fieldName = mapper.realMember(result.getClass(), reader.getNodeName());
                boolean implicitCollectionHasSameName = mapper.getImplicitCollectionDefForFieldName(result.getClass(), reader.getNodeName()) != null;

                Class classDefiningField = determineWhichClassDefinesField(reader);
                boolean fieldExistsInClass = !implicitCollectionHasSameName && fieldDefinedInClass(result,fieldName);

                Class type = determineType(reader, fieldExistsInClass, result, fieldName, classDefiningField);
                final Object value;
                if (fieldExistsInClass) {
                    Field field = reflectionProvider.getField(result.getClass(),fieldName);
                    value = unmarshallField(context, result, type, field);
                    // TODO the reflection provider should have returned the proper field in first place ....
                    Class definedType = reflectionProvider.getFieldType(result, fieldName, classDefiningField);
                    if (!definedType.isPrimitive()) {
                        type = definedType;
                    }
                } else {
                    value = context.convertAnother(result, type);
                }

                if (value != null && !type.isAssignableFrom(value.getClass())) {
                    LOGGER.warning("Cannot convert type " + value.getClass().getName() + " to type " + type.getName());
                    // behave as if we didn't see this element
                } else {
                    if (fieldExistsInClass) {
                        reflectionProvider.writeField(result, fieldName, value, classDefiningField);
                        seenFields.add(classDefiningField, fieldName);
                    } else {
                        implicitCollectionsForCurrentObject = writeValueToImplicitCollection(context, value, implicitCollectionsForCurrentObject, result, fieldName);
                    }
                }
            } catch (NonExistentFieldException e) {
                LOGGER.log(Level.WARNING,"Skipping a non-existent field "+e.getFieldName(),e);
            } catch (CannotResolveClassException e) {
                LOGGER.log(Level.WARNING,"Skipping a non-existend type",e);
            }

            reader.moveUp();
        }

        return result;
    }

    private boolean fieldDefinedInClass(Object result, String attrName) {
        // during unmarshalling, unmarshal into transient fields like XStream 1.1.3
        //boolean fieldExistsInClass = reflectionProvider.fieldDefinedInClass(attrName, result.getClass());
        return reflectionProvider.getFieldOrNull(result.getClass(),attrName)!=null;
    }

    protected Object unmarshallField(final UnmarshallingContext context, final Object result, Class type, Field field) {
        return context.convertAnother(result, type);
    }

    private Map writeValueToImplicitCollection(UnmarshallingContext context, Object value, Map implicitCollections, Object result, String itemFieldName) {
        String fieldName = mapper.getFieldNameForItemTypeAndName(context.getRequiredType(), value.getClass(), itemFieldName);
        if (fieldName != null) {
            if (implicitCollections == null) {
                implicitCollections = new HashMap(); // lazy instantiation
            }
            Collection collection = (Collection) implicitCollections.get(fieldName);
            if (collection == null) {
                Class fieldType = mapper.defaultImplementationOf(reflectionProvider.getFieldType(result, fieldName, null));
                if (!Collection.class.isAssignableFrom(fieldType)) {
                    throw new ObjectAccessException("Field " + fieldName + " of " + result.getClass().getName() +
                            " is configured for an implicit Collection, but field is of type " + fieldType.getName());
                }
                if (pureJavaReflectionProvider == null) {
                    pureJavaReflectionProvider = new PureJavaReflectionProvider();
                }
                collection = (Collection)pureJavaReflectionProvider.newInstance(fieldType);
                reflectionProvider.writeField(result, fieldName, collection, null);
                implicitCollections.put(fieldName, collection);
            }
            collection.add(value);
        }
        return implicitCollections;
    }

    private Class determineWhichClassDefinesField(HierarchicalStreamReader reader) {
        String definedIn = reader.getAttribute(mapper.aliasForAttribute("defined-in"));
        return definedIn == null ? null : mapper.realClass(definedIn);
    }

    protected Object instantiateNewInstance(HierarchicalStreamReader reader, UnmarshallingContext context) {
        String readResolveValue = reader.getAttribute(mapper.aliasForAttribute("resolves-to"));
        Object currentObject = context.currentObject();
        if (currentObject != null) {
            return currentObject;
        } else if (readResolveValue != null) {
            return reflectionProvider.newInstance(mapper.realClass(readResolveValue));
        } else {
            return reflectionProvider.newInstance(context.getRequiredType());
        }
    }

    private static class SeenFields {

        private Set seen = new HashSet();

        public void add(Class definedInCls, String fieldName) {
            String uniqueKey = fieldName;
            if (definedInCls != null) {
                uniqueKey += " [" + definedInCls.getName() + "]";
            }
            if (seen.contains(uniqueKey)) {
                throw new DuplicateFieldException(uniqueKey);
            } else {
                seen.add(uniqueKey);
            }
        }

    }

    private Class determineType(HierarchicalStreamReader reader, boolean validField, Object result, String fieldName, Class definedInCls) {
        String classAttribute = reader.getAttribute(mapper.aliasForAttribute("class"));
        Class fieldType = reflectionProvider.getFieldType(result, fieldName, definedInCls);
        if (classAttribute != null) {
            Class specifiedType = mapper.realClass(classAttribute);
            if(fieldType.isAssignableFrom(specifiedType))
                // make sure that the specified type in XML is compatible with the field type.
                // this allows the code to evolve in more flexible way.
                return specifiedType;
        }
        if (!validField) {
            Class itemType = mapper.getItemTypeForItemFieldName(result.getClass(), fieldName);
            if (itemType != null) {
                return itemType;
            } else {
                return mapper.realClass(reader.getNodeName());
            }
        } else {
            return mapper.defaultImplementationOf(fieldType);
        }
    }

    private Object readResolve() {
        serializationMethodInvoker = new SerializationMethodInvoker();
        return this;
    }

    public static class DuplicateFieldException extends ConversionException {
        public DuplicateFieldException(String msg) {
            super(msg);
            add("duplicate-field", msg);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(RobustReflectionConverter.class.getName());
}
