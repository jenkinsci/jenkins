package org.kohsuke.stapler.beanutils.TagTest.RootActionImpl

def st = namespace("jelly:stapler")

st.contentType(value: "text/html")
st.include(page: "_included", it: my)
