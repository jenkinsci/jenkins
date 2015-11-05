/*
 * Copyright (C) 2005, 2006 Joe Walnes.
 * Copyright (C) 2006, 2007, 2008 XStream Committers.
 * All rights reserved.
 *
 * The software in this package is published under the terms of the BSD
 * style license a copy of which has been included with this distribution in
 * the LICENSE.txt file.
 *
 * Created on 22. January 2005 by Joe Walnes
 */
package hudson.util.xstream;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.SingleValueConverter;
import com.thoughtworks.xstream.mapper.Mapper;
import com.thoughtworks.xstream.mapper.MapperWrapper;

/**
 * Works like {@link MapperWrapper} except it lets the subtype
 * change the delegation target.
 *
 * <p>
 * Since {@link XStream} caches the result of mapper pipeline,
 * the kind of mutation and when you can do it is limited.
 *
 * @author Kohsuke Kawaguchi
 */
public class MapperDelegate extends MapperWrapper {
    protected Mapper delegate;

    public MapperDelegate(Mapper delegate) {
        super(null);
        this.delegate = delegate;
    }

    public String serializedClass(Class type) {
        return delegate.serializedClass(type);
    }

    public Class realClass(String elementName) {
        return delegate.realClass(elementName);
    }

    public String serializedMember(Class type, String memberName) {
        return delegate.serializedMember(type, memberName);
    }

    public String realMember(Class type, String serialized) {
        return delegate.realMember(type, serialized);
    }

    public boolean isImmutableValueType(Class type) {
        return delegate.isImmutableValueType(type);
    }

    public Class defaultImplementationOf(Class type) {
        return delegate.defaultImplementationOf(type);
    }

    public String aliasForAttribute(String attribute) {
        return delegate.aliasForAttribute(attribute);
    }

    public String attributeForAlias(String alias) {
        return delegate.attributeForAlias(alias);
    }

    public String aliasForSystemAttribute(String attribute) {
        return delegate.aliasForSystemAttribute(attribute);
    }

    public String getFieldNameForItemTypeAndName(Class definedIn, Class itemType, String itemFieldName) {
        return delegate.getFieldNameForItemTypeAndName(definedIn, itemType, itemFieldName);
    }

    public Class getItemTypeForItemFieldName(Class definedIn, String itemFieldName) {
        return delegate.getItemTypeForItemFieldName(definedIn, itemFieldName);
    }

    public ImplicitCollectionMapping getImplicitCollectionDefForFieldName(Class itemType, String fieldName) {
        return delegate.getImplicitCollectionDefForFieldName(itemType, fieldName);
    }

    public boolean shouldSerializeMember(Class definedIn, String fieldName) {
        return delegate.shouldSerializeMember(definedIn, fieldName);
    }

    /**
     * @deprecated since 1.3, use {@link #getConverterFromItemType(String, Class, Class)}
     */
    @Deprecated
    public SingleValueConverter getConverterFromItemType(String fieldName, Class type) {
        return delegate.getConverterFromItemType(fieldName, type);
    }

    /**
     * @deprecated since 1.3, use {@link #getConverterFromItemType(String, Class, Class)}
     */
    @Deprecated
    public SingleValueConverter getConverterFromItemType(Class type) {
        return delegate.getConverterFromItemType(type);
    }

    /**
     * @deprecated since 1.3, use {@link #getConverterFromAttribute(Class, String, Class)}
     */
    @Deprecated
    public SingleValueConverter getConverterFromAttribute(String name) {
        return delegate.getConverterFromAttribute(name);
    }

    public Converter getLocalConverter(Class definedIn, String fieldName) {
        return delegate.getLocalConverter(definedIn, fieldName);
    }

    public Mapper lookupMapperOfType(Class type) {
        return type.isAssignableFrom(getClass()) ? this : delegate.lookupMapperOfType(type);
    }

    public SingleValueConverter getConverterFromItemType(String fieldName, Class type, Class definedIn) {
    	return delegate.getConverterFromItemType(fieldName, type, definedIn);
    }

    /**
     * @deprecated since 1.3, use combination of {@link #serializedMember(Class, String)} and {@link #getConverterFromItemType(String, Class, Class)}
     */
    @Deprecated
    public String aliasForAttribute(Class definedIn, String fieldName) {
    	return delegate.aliasForAttribute(definedIn, fieldName);
    }

    /**
     * @deprecated since 1.3, use combination of {@link #realMember(Class, String)} and {@link #getConverterFromItemType(String, Class, Class)}
     */
    @Deprecated
    public String attributeForAlias(Class definedIn, String alias) {
    	return delegate.attributeForAlias(definedIn, alias);
    }

    /**
     * @deprecated since 1.3.1, use {@link #getConverterFromAttribute(Class, String, Class)}
     */
    @Deprecated
    public SingleValueConverter getConverterFromAttribute(Class type, String attribute) {
    	return delegate.getConverterFromAttribute(type, attribute);
    }

    public SingleValueConverter getConverterFromAttribute(Class definedIn, String attribute, Class type) {
        return delegate.getConverterFromAttribute(definedIn, attribute, type);
    }
}
