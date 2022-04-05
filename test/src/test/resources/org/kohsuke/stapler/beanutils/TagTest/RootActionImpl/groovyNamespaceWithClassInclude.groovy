package org.kohsuke.stapler.beanutils.TagTest.RootActionImpl

def st = namespace("jelly:stapler")

st.contentType(value: "text/html")

// This case would fail without the compatibility code: 'class' attribute set in a Groovy view
st.include(page: "_included", class: it.class)
