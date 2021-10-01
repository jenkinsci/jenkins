package org.kohsuke.stapler.beanutils.TagTest.RootActionImpl

import org.kohsuke.stapler.jelly.StaplerTagLibrary

def lib = jelly(StaplerTagLibrary.class)

lib.contentType(value: "text/html")

// This case would fail without the compatibility code: 'class' attribute set in a Groovy view
lib.include(page: "_included", class: it.class)
