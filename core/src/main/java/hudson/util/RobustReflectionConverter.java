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

import static java.util.logging.Level.FINE;

import com.thoughtworks.xstream.XStreamException;
import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.SingleValueConverter;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.reflection.ObjectAccessException;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;
import com.thoughtworks.xstream.converters.reflection.ReflectionConverter;
import com.thoughtworks.xstream.converters.reflection.ReflectionProvider;
import com.thoughtworks.xstream.core.util.Primitives;
import com.thoughtworks.xstream.core.util.SerializationMembers;
import com.thoughtworks.xstream.io.ExtendedHierarchicalStreamWriterHelper;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.mapper.Mapper;
import com.thoughtworks.xstream.security.InputManipulationException;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.diagnosis.OldDataMonitor;
import hudson.model.Saveable;
import hudson.security.ACL;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;
import jenkins.util.xstream.CriticalXStreamException;
import net.jcip.annotations.GuardedBy;
import org.acegisecurity.Authentication;
import org.jvnet.tiger_types.Types;

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
@SuppressWarnings({"rawtypes", "unchecked"})
public class RobustReflectionConverter implements Converter {

    static /* non-final for Groovy */ boolean RECORD_FAILURES_FOR_ALL_AUTHENTICATIONS = SystemProperties.getBoolean(RobustReflectionConverter.class.getName() + ".recordFailuresForAllAuthentications", false);
    private static /* non-final for Groovy */ boolean RECORD_FAILURES_FOR_ADMINS = SystemProperties.getBoolean(RobustReflectionConverter.class.getName() + ".recordFailuresForAdmins", false);

    protected final ReflectionProvider reflectionProvider;
    protected final Mapper mapper;
    protected transient SerializationMembers serializationMethodInvoker;
    private transient ReflectionProvider pureJavaReflectionProvider;
    private final @NonNull XStream2.ClassOwnership classOwnership;
    /** There are typically few critical fields around, but we end up looking up in this map a lot.
        in addition, this map is really only written to during static initialization, so we should use
        reader writer lock to avoid locking as much as possible.  In addition, to avoid looking up
        the class name (which requires calling class.getName, which may not be cached, the map is inverted
        with the fields as the keys.**/
    private final ReadWriteLock criticalFieldsLock = new ReentrantReadWriteLock();
    @GuardedBy("criticalFieldsLock")
    private final Map<String, Set<String>> criticalFields = new HashMap<>();

    public RobustReflectionConverter(Mapper mapper, ReflectionProvider reflectionProvider) {
        this(mapper, reflectionProvider, new XStream2().new PluginClassOwnership());
    }

    RobustReflectionConverter(Mapper mapper, ReflectionProvider reflectionProvider, XStream2.ClassOwnership classOwnership) {
        this.mapper = mapper;
        this.reflectionProvider = reflectionProvider;
        assert classOwnership != null;
        this.classOwnership = classOwnership;
        serializationMethodInvoker = new SerializationMembers();
    }

    void addCriticalField(Class<?> clazz, String field) {
        // Lock the write lock
        criticalFieldsLock.writeLock().lock();
        try {
            // If the class already exists, then add a new field, otherwise
            // create the hash map field
            if (!criticalFields.containsKey(field)) {
                criticalFields.put(field, new HashSet<>());
            }
            criticalFields.get(field).add(clazz.getName());
        }
        finally {
            // Unlock
            criticalFieldsLock.writeLock().unlock();
        }
    }

    private boolean hasCriticalField(Class<?> clazz, String field) {
        // Lock the write lock
        criticalFieldsLock.readLock().lock();
        try {
            Set<String> classesWithField = criticalFields.get(field);
            if (classesWithField == null) {
                return false;
            }
            if (!classesWithField.contains(clazz.getName())) {
                return false;
            }
            return true;
        }
        finally {
            criticalFieldsLock.readLock().unlock();
        }
    }

    @Override
    public boolean canConvert(Class type) {
        return true;
    }

    @Override
    public void marshal(Object original, final HierarchicalStreamWriter writer, final MarshallingContext context) {
        final Object source = serializationMethodInvoker.callWriteReplace(original);

        if (source.getClass() != original.getClass()) {
            writer.addAttribute(mapper.aliasForAttribute("resolves-to"), mapper.serializedClass(source.getClass()));
        }

        OwnerContext oc = OwnerContext.find(context);
        oc.startVisiting(writer, classOwnership.ownerOf(original.getClass()));
        try {
            doMarshal(source, writer, context);
        } finally {
            oc.stopVisiting();
        }
    }

    /** Marks {@code plugin="..."} on elements where the owner is known and distinct from the closest owned ancestor. */
    private static class OwnerContext extends LinkedList<String> {
        static OwnerContext find(MarshallingContext context) {
            OwnerContext c = (OwnerContext) context.get(OwnerContext.class);
            if (c == null) {
                c = new OwnerContext();
                context.put(OwnerContext.class, c);
            }
            return c;
        }

        private void startVisiting(HierarchicalStreamWriter writer, String owner) {
            if (owner != null) {
                boolean redundant = false;
                for (String parentOwner : this) {
                    if (parentOwner != null) {
                        redundant = parentOwner.equals(owner);
                        break;
                    }
                }
                if (!redundant) {
                    writer.addAttribute("plugin", owner);
                }
            }
            addFirst(owner);
        }

        private void stopVisiting() {
            removeFirst();
        }
    }

    protected void doMarshal(final Object source, final HierarchicalStreamWriter writer, final MarshallingContext context) {
        final Set seenFields = new HashSet();
        final Set seenAsAttributes = new HashSet();

        // Attributes might be preferred to child elements ...
         reflectionProvider.visitSerializableFields(source, new ReflectionProvider.Visitor() {
            @SuppressWarnings("deprecation") // deliberately calling deprecated methods?
            @Override
            public void visit(String fieldName, Class type, Class definedIn, Object value) {
                SingleValueConverter converter = mapper.getConverterFromItemType(fieldName, type, definedIn);
                if (converter == null) converter = mapper.getConverterFromItemType(fieldName, type);
                if (converter == null) converter = mapper.getConverterFromItemType(type);
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
            @Override
            public void visit(String fieldName, Class fieldType, Class definedIn, Object newObj) {
                if (!seenAsAttributes.contains(fieldName) && newObj != null) {
                    Mapper.ImplicitCollectionMapping mapping = mapper.getImplicitCollectionDefForFieldName(source.getClass(), fieldName);
                    if (mapping != null) {
                        if (mapping.getItemFieldName() != null) {
                            Collection list = (Collection) newObj;
                            for (Object obj : list) {
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

            @SuppressWarnings("deprecation") // TODO HierarchicalStreamWriter#startNode(String, Class) in 1.5.0
            private void writeField(String fieldName, String aliasName, Class fieldType, Class definedIn, Object newObj) {
                try {
                    if (!mapper.shouldSerializeMember(definedIn, aliasName)) {
                        return;
                    }
                    ExtendedHierarchicalStreamWriterHelper.startNode(writer, mapper.serializedMember(definedIn, aliasName), fieldType);

                    Class actualType = newObj.getClass();

                    Class defaultType = mapper.defaultImplementationOf(fieldType);
                    if (!actualType.equals(defaultType)) {
                        String serializedClassName = mapper.serializedClass(actualType);
                        if (!serializedClassName.equals(mapper.serializedClass(defaultType))) {
                            writer.addAttribute(mapper.aliasForSystemAttribute("class"), serializedClassName);
                        }
                    }

                    if (seenFields.contains(aliasName)) {
                        writer.addAttribute(mapper.aliasForAttribute("defined-in"), mapper.serializedClass(definedIn));
                    }

                    Field field = reflectionProvider.getField(definedIn, fieldName);
                    marshallField(context, newObj, field);
                    writer.endNode();
                } catch (RuntimeException e) {
                    // intercept an exception so that the stack trace shows how we end up marshalling the object in question
                    throw new RuntimeException("Failed to serialize " + definedIn.getName() + "#" + fieldName + " for " + source.getClass(), e);
                }
            }

        });
    }

    protected void marshallField(final MarshallingContext context, Object newObj, Field field) {
        Converter converter = mapper.getLocalConverter(field.getDeclaringClass(), field.getName());
        context.convertAnother(newObj, converter);
    }

    @Override
    public Object unmarshal(final HierarchicalStreamReader reader, final UnmarshallingContext context) {
        Object result = instantiateNewInstance(reader, context);
        result = doUnmarshal(result, reader, context);
        return serializationMethodInvoker.callReadResolve(result);
    }

    public Object doUnmarshal(final Object result, final HierarchicalStreamReader reader, final UnmarshallingContext context) {
        final SeenFields seenFields = new SeenFields();
        Iterator it = reader.getAttributeNames();
        // Remember outermost Saveable encountered, for reporting below
        if (result instanceof Saveable && context.get("Saveable") == null)
            context.put("Saveable", result);

        // Process attributes before recursing into child elements.
        while (it.hasNext()) {
            String attrAlias = (String) it.next();
            String attrName = mapper.attributeForAlias(attrAlias);
            Class classDefiningField = determineWhichClassDefinesField(reader);
            boolean fieldExistsInClass = fieldDefinedInClass(result, attrName);
            if (fieldExistsInClass) {
                Field field = reflectionProvider.getField(result.getClass(), attrName);
                SingleValueConverter converter = mapper.getConverterFromAttribute(field.getDeclaringClass(), attrName, field.getType());
                Class type = field.getType();
                if (converter == null) {
                    converter = mapper.getConverterFromItemType(type); // TODO add fieldName & definedIn args
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

        Map<String, Collection<Object>> implicitCollectionsForCurrentObject = new HashMap<>();
        Map<String, Class<?>> implicitCollectionElementTypesForCurrentObject = new HashMap<>();
        while (reader.hasMoreChildren()) {
            reader.moveDown();

            boolean critical = false;
            try {
                String fieldName = mapper.realMember(result.getClass(), reader.getNodeName());
                for (Class<?> concrete = result.getClass(); concrete != null; concrete = concrete.getSuperclass()) {
                    // Not quite right since a subclass could shadow a field, but probably suffices:
                    if (hasCriticalField(concrete, fieldName)) {
                        critical = true;
                        break;
                    }
                }
                boolean implicitCollectionHasSameName = mapper.getImplicitCollectionDefForFieldName(result.getClass(), reader.getNodeName()) != null;

                Class classDefiningField = determineWhichClassDefinesField(reader);
                boolean fieldExistsInClass = !implicitCollectionHasSameName && fieldDefinedInClass(result, fieldName);

                Class type = determineType(reader, fieldExistsInClass, result, fieldName, classDefiningField);
                final Object value;
                if (fieldExistsInClass) {
                    Field field = reflectionProvider.getField(result.getClass(), fieldName);
                    value = unmarshalField(context, result, type, field);
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
                        writeValueToImplicitCollection(reader, context, value, implicitCollectionsForCurrentObject, implicitCollectionElementTypesForCurrentObject, result, fieldName);
                    }
                }
            } catch (CriticalXStreamException e) {
                throw e;
            } catch (InputManipulationException e) {
                LOGGER.warning(
                        "DoS detected and prevented. If the heuristic was too aggressive, " +
                                "you can customize the behavior by setting the hudson.util.XStream2.collectionUpdateLimit system property. " +
                                "See https://www.jenkins.io/redirect/xstream-dos-prevention for more information.");
                throw new CriticalXStreamException(e);
            } catch (XStreamException e) {
                if (critical) {
                    throw new CriticalXStreamException(e);
                }
                addErrorInContext(context, e);
            } catch (LinkageError e) {
                if (critical) {
                    throw e;
                }
                addErrorInContext(context, e);
            }

            reader.moveUp();
        }

        // Report any class/field errors in Saveable objects if it happens during loading of existing data from disk
        if (shouldReportUnloadableDataForCurrentUser() && context.get("ReadError") != null && context.get("Saveable") == result) {
            // Avoid any error in OldDataMonitor to be catastrophic. See JENKINS-62231 and JENKINS-59582
            // The root cause is the OldDataMonitor extension is not ready before a plugin triggers an error, for
            // example when trying to load a field that was created by a new version and you downgrade to the previous
            // one.
            try {
                OldDataMonitor.report((Saveable) result, (ArrayList<Throwable>) context.get("ReadError"));
            } catch (Throwable t) {
                // it should be already reported, but we report with INFO just in case
                StringBuilder message = new StringBuilder("There was a problem reporting unmarshalling field errors");
                Level level = Level.WARNING;
                if (t instanceof IllegalStateException && t.getMessage().contains("Expected 1 instance of " + OldDataMonitor.class.getName())) {
                    message.append(". Make sure this code is executed after InitMilestone.EXTENSIONS_AUGMENTED stage, for example in Plugin#postInitialize instead of Plugin#start");
                    level = Level.INFO; // it was reported when getting the singleton for OldDataMonitor
                }
                // it should be already reported, but we report with INFO just in case
                LOGGER.log(level, message.toString(), t);
            }
            context.put("ReadError", null);
        }
        return result;
    }

    /**
     * Returns whether the current user authentication is allowed to have errors loading data reported.
     *
     * <p>{@link ACL#SYSTEM} always has errors reported.
     * If {@link #RECORD_FAILURES_FOR_ALL_AUTHENTICATIONS} is {@code true}, errors are reported for all authentications.
     * Otherwise errors are reported for users with {@link Jenkins#ADMINISTER} permission if {@link #RECORD_FAILURES_FOR_ADMINS} is {@code true}.</p>
     *
     * @return whether the current user authentication is allowed to have errors loading data reported.
     */
    private static boolean shouldReportUnloadableDataForCurrentUser() {
        if (RECORD_FAILURES_FOR_ALL_AUTHENTICATIONS) {
            return true;
        }
        final Authentication authentication = Jenkins.getAuthentication();
        if (authentication.equals(ACL.SYSTEM)) {
            return true;
        }
        return RECORD_FAILURES_FOR_ADMINS && Jenkins.get().hasPermission(Jenkins.ADMINISTER);
    }

    public static void addErrorInContext(UnmarshallingContext context, Throwable e) {
        LOGGER.log(FINE, "Failed to load", e);
        ArrayList<Throwable> list = (ArrayList<Throwable>) context.get("ReadError");
        if (list == null)
            context.put("ReadError", list = new ArrayList<>());
        list.add(e);
    }

    private boolean fieldDefinedInClass(Object result, String attrName) {
        // during unmarshalling, unmarshal into transient fields like XStream 1.1.3
        //boolean fieldExistsInClass = reflectionProvider.fieldDefinedInClass(attrName, result.getClass());
        return reflectionProvider.getFieldOrNull(result.getClass(), attrName) != null;
    }

    protected Object unmarshalField(final UnmarshallingContext context, final Object result, Class type, Field field) {
        Converter converter = mapper.getLocalConverter(field.getDeclaringClass(), field.getName());
        if (converter == null) {
            if (new RobustCollectionConverter(mapper, reflectionProvider).canConvert(type)) {
                converter = new RobustCollectionConverter(mapper, reflectionProvider, field.getGenericType());
            } else if (new RobustMapConverter(mapper).canConvert(type)) {
                converter = new RobustMapConverter(mapper, field.getGenericType());
            }
        }
        return context.convertAnother(result, type, converter);
    }

    private void writeValueToImplicitCollection(HierarchicalStreamReader reader, UnmarshallingContext context, Object value, Map<String, Collection<Object>> implicitCollections, Map<String, Class<?>> implicitCollectionElementTypes, Object result, String itemFieldName) {
        String fieldName = mapper.getFieldNameForItemTypeAndName(context.getRequiredType(), value.getClass(), itemFieldName);
        if (fieldName != null) {
            Collection collection = implicitCollections.get(fieldName);
            if (collection == null) {
                Field field = reflectionProvider.getField(result.getClass(), fieldName);
                Class<?> fieldType = mapper.defaultImplementationOf(field.getType());
                if (!Collection.class.isAssignableFrom(fieldType)) {
                    throw new ObjectAccessException("Field " + fieldName + " of " + result.getClass().getName() +
                            " is configured for an implicit Collection, but field is of type " + fieldType.getName());
                }
                if (pureJavaReflectionProvider == null) {
                    pureJavaReflectionProvider = new PureJavaReflectionProvider();
                }
                collection = (Collection) pureJavaReflectionProvider.newInstance(fieldType);
                reflectionProvider.writeField(result, fieldName, collection, null);
                implicitCollections.put(fieldName, collection);
                Type fieldGenericType = field.getGenericType();
                Type elementGenericType = Types.getTypeArgument(Types.getBaseClass(fieldGenericType, Collection.class), 0, Object.class);
                Class<?> elementType = Types.erasure(elementGenericType);
                implicitCollectionElementTypes.put(fieldName, elementType);
            }
            Class<?> elementType = implicitCollectionElementTypes.getOrDefault(fieldName, Object.class);
            try {
                elementType.cast(value);
            } catch (ClassCastException e) {
                var exception = new ConversionException("Invalid element type for implicit collection for field: " + fieldName, e);
                reader.appendErrors(exception);
                throw exception;
            }
            collection.add(value);
        } else {
            // TODO: Should we warn in this case? The value will be ignored.
        }
    }

    private Class determineWhichClassDefinesField(HierarchicalStreamReader reader) {
        String definedIn = reader.getAttribute(mapper.aliasForAttribute("defined-in"));
        return definedIn == null ? null : mapper.realClass(definedIn);
    }

    protected Object instantiateNewInstance(HierarchicalStreamReader reader, UnmarshallingContext context) {
        String readResolveValue = reader.getAttribute(mapper.aliasForAttribute("resolves-to"));

        Class type = readResolveValue != null ? mapper.realClass(readResolveValue) : context.getRequiredType();

        Object currentObject = context.currentObject();
        if (currentObject != null) {
            if (type.isInstance(currentObject))
                return currentObject;
        }
        return reflectionProvider.newInstance(type);
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
        if (classAttribute != null) {
            Class specifiedType = mapper.realClass(classAttribute);
            Class fieldType = reflectionProvider.getFieldType(result, fieldName, definedInCls);
            if (fieldType.isAssignableFrom(specifiedType))
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
            Class fieldType = reflectionProvider.getFieldType(result, fieldName, definedInCls);
            return mapper.defaultImplementationOf(fieldType);
        }
    }

    private Object readResolve() {
        serializationMethodInvoker = new SerializationMembers();
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
