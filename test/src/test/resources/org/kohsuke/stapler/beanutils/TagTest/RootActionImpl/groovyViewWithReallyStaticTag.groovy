package org.kohsuke.stapler.beanutils.TagTest.RootActionImpl

def st = namespace("jelly:stapler")
def lib = namespace("/org/kohsuke/stapler/beanutils/TagTest/lib")

st.contentType(value: "text/html")
h1(class: 'title') {
    raw('It works from Groovy!')
}
