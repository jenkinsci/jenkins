# definition of the IPS package.
# see http://wiki.updatecenter.java.net/Wiki.jsp?page=UC20.Docs.Packaging for more about this

import builder;

pkg = builder.build_pkg(name="hudson", version="1.223,0-0")
pkg.update({
    "attributes"    : { 
        "pkg.summary" : "Hudson", 
        "pkg.description" : "Extensible continuous integration system",
    }
})


pkg.addfile("/usr/local/bin/hudson.war",{"file":"./hudson.war"})
pkg.addfile("/var/svc/manifest/application/hudson.xml",{"file":"hudson.xml"})
# this is the Hudson home directory
pkg.mkdirs("/var/lib/hudson")

# TODO: how do I register SMF?
# see http://www.pauloswald.com/article/29/hudson-solaris-smf-manifest
# see http://blogs.sun.com/wittyman/entry/postgresql_packages_from_ips_repository
