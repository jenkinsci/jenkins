# definition of the IPS package.
# see https://updatecenter2.dev.java.net/maven-makepkgs-plugin/ for more about this

import builder;

# IPS can't do SNAPSHOT
version = builder.props['version']
if version.endswith("-SNAPSHOT"):
    version = version[:-9];

pkg = builder.build_pkg(name="hudson", version=version+",0-0")
pkg.update({
    "attributes"    : { 
        "pkg.summary" : "Hudson", 
        "pkg.description" : "Extensible continuous integration system",
    }
})


pkg.addfile("/usr/local/bin/hudson.war",{"file":"./target/hudson.war"})
pkg.addfile("/var/svc/manifest/application/hudson.xml",{"file":"../ips/hudson.xml"})
# this is the Hudson home directory
pkg.mkdirs("/var/lib/hudson")

# TODO: register SMF when the feature is available?
# see http://www.pauloswald.com/article/29/hudson-solaris-smf-manifest
# see http://blogs.sun.com/wittyman/entry/postgresql_packages_from_ips_repository
