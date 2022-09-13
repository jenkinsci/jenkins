package org.kohsuke.stapler.beanutils.TagTest.RootActionImpl

import org.kohsuke.stapler.jelly.StaplerTagLibrary

def lib = jelly(StaplerTagLibrary.class)

lib.contentType(value: "text/html")
lib.include(page: "_included", it: my)
