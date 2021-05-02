package org.kohsuke.stapler.beanutils.TagTest.RootActionImpl

import org.kohsuke.stapler.beanutils.BeanUtilsTagLibrary

def lib = jelly(BeanUtilsTagLibrary.class)
def st = namespace("jelly:stapler")

st.include(page: "jellyWithMyTagLibClassName")
