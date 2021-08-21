package org.kohsuke.stapler.beanutils.TagTest.RootActionImpl

import org.kohsuke.stapler.beanutils.BeanUtilsTagLibrary

def st = namespace("jelly:stapler")
st.contentType(value: 'text/html')

def lib = jelly(BeanUtilsTagLibrary.class)
lib.tagWithObjectTypedClassProperty(class: "objectParam");
