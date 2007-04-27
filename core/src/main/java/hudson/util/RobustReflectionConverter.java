package hudson.util;

import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.reflection.ReflectionConverter;
import com.thoughtworks.xstream.converters.reflection.ReflectionProvider;
import com.thoughtworks.xstream.converters.reflection.SerializationMethodInvoker;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.mapper.Mapper;
import com.thoughtworks.xstream.alias.CannotResolveClassException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
    private final Mapper mapper;
    private final ReflectionProvider reflectionProvider;
    private final SerializationMethodInvoker serializationMethodInvoker;

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
            writer.addAttribute(mapper.attributeForReadResolveField(), mapper.serializedClass(source.getClass()));
        }

        final Set seenFields = new HashSet();

        reflectionProvider.visitSerializableFields(source, new ReflectionProvider.Visitor() {
            public void visit(String fieldName, Class fieldType, Class definedIn, Object newObj) {
                if (newObj != null) {
                    Mapper.ImplicitCollectionMapping mapping = mapper.getImplicitCollectionDefForFieldName(source.getClass(), fieldName);
                    if (mapping != null) {
                        if (mapping.getItemFieldName() != null) {
                            ArrayList list = (ArrayList) newObj;
                            for (Object obj : list) {
                                writeField(mapping.getItemFieldName(), mapping.getItemType(), definedIn, obj);
                            }
                        } else {
                            context.convertAnother(newObj);
                        }
                    } else {
                        writeField(fieldName, fieldType, definedIn, newObj);
                        seenFields.add(fieldName);
                    }
                }
            }

            private void writeField(String fieldName, Class fieldType, Class definedIn, Object newObj) {
                if (!mapper.shouldSerializeMember(definedIn, fieldName)) {
                    return;
                }
                writer.startNode(mapper.serializedMember(definedIn, fieldName));

                Class actualType = newObj.getClass();

                Class defaultType = mapper.defaultImplementationOf(fieldType);
                if (!actualType.equals(defaultType)) {
                    writer.addAttribute(mapper.attributeForImplementationClass(), mapper.serializedClass(actualType));
                }

                if (seenFields.contains(fieldName)) {
                    writer.addAttribute(mapper.attributeForClassDefiningField(), mapper.serializedClass(definedIn));
                }
                context.convertAnother(newObj);

                writer.endNode();
            }

        });
    }

    public Object unmarshal(final HierarchicalStreamReader reader, final UnmarshallingContext context) {
        final Object result = instantiateNewInstance(context, reader.getAttribute(mapper.attributeForReadResolveField()));
        final SeenFields seenFields = new SeenFields();

        Map implicitCollectionsForCurrentObject = null;
        while (reader.hasMoreChildren()) {
            reader.moveDown();

            String fieldName = mapper.realMember(result.getClass(), reader.getNodeName());

            Class classDefiningField = determineWhichClassDefinesField(reader);
            boolean fieldExistsInClass = reflectionProvider.fieldDefinedInClass(fieldName, result.getClass());

            try {
                Class type = determineType(reader, fieldExistsInClass, result, fieldName, classDefiningField);
                Object value = context.convertAnother(result, type);

                if (fieldExistsInClass) {
                    reflectionProvider.writeField(result, fieldName, value, classDefiningField);
                    seenFields.add(classDefiningField, fieldName);
                } else {
                    implicitCollectionsForCurrentObject = writeValueToImplicitCollection(context, value, implicitCollectionsForCurrentObject, result, fieldName);
                }
            } catch (CannotResolveClassException e) {
                LOGGER.log(Level.WARNING,"Skipping a non-existend type",e);
            }

            reader.moveUp();
        }

        return serializationMethodInvoker.callReadResolve(result);
    }


    private Map writeValueToImplicitCollection(UnmarshallingContext context, Object value, Map implicitCollections, Object result, String itemFieldName) {
        String fieldName = mapper.getFieldNameForItemTypeAndName(context.getRequiredType(), value.getClass(), itemFieldName);
        if (fieldName != null) {
            if (implicitCollections == null) {
                implicitCollections = new HashMap(); // lazy instantiation
            }
            Collection collection = (Collection) implicitCollections.get(fieldName);
            if (collection == null) {
                collection = new ArrayList();
                reflectionProvider.writeField(result, fieldName, collection, null);
                implicitCollections.put(fieldName, collection);
            }
            collection.add(value);
        }
        return implicitCollections;
    }

    private Class determineWhichClassDefinesField(HierarchicalStreamReader reader) {
        String definedIn = reader.getAttribute(mapper.attributeForClassDefiningField());
        return definedIn == null ? null : mapper.realClass(definedIn);
    }

    private Object instantiateNewInstance(UnmarshallingContext context, String readResolveValue) {
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
        String classAttribute = reader.getAttribute(mapper.attributeForImplementationClass());
        if (classAttribute != null) {
            return mapper.realClass(classAttribute);
        } else if (!validField) {
            Class itemType = mapper.getItemTypeForItemFieldName(result.getClass(), fieldName);
            if (itemType != null) {
                return itemType;
            } else {
                return mapper.realClass(reader.getNodeName());
            }
        } else {
            return mapper.defaultImplementationOf(reflectionProvider.getFieldType(result, fieldName, definedInCls));
        }
    }

    public static class DuplicateFieldException extends ConversionException {
        public DuplicateFieldException(String msg) {
            super(msg);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(RobustReflectionConverter.class.getName());
}
