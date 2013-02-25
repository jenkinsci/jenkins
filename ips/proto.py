# The MIT License
# 
# Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
# 
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
# 
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
# 
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
# THE SOFTWARE.


# definition of the IPS package.
# see https://wikis.oracle.com/display/IpsBestPractices/Producing+and+Maintaining+Packages for more about this

import builder;

# IPS can't do SNAPSHOT
version = builder.props['version']
if version.endswith("-SNAPSHOT"):
    version = version[:-9];

pkg = builder.build_pkg(name="jenkins", version=version+",0-0")
pkg.update({
    "attributes"    : { 
        "pkg.summary" : "Jenkins", 
        "pkg.description" : "Extensible continuous integration system",
    }
})


# restart_fmri instructs IPS to reload the manifest
pkg.addfile("/usr/local/bin/jenkins.war",{"file":"./target/jenkins.war"})
pkg.addfile("/var/svc/manifest/application/jenkins.xml",{"file":"../ips/jenkins.xml","restart_fmri":"svc:/system/manifest-import:default"})
# this is the Hudson home directory
pkg.mkdirs("/var/lib/jenkins")

# TODO: register SMF when the feature is available?
# see http://www.pauloswald.com/article/29/hudson-solaris-smf-manifest
# see http://blogs.sun.com/wittyman/entry/postgresql_packages_from_ips_repository
