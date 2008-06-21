# definition of the IPS package.
# see http://wiki.updatecenter.java.net/Wiki.jsp?page=UC20.Docs.Packaging for more about this

pkg = {
    "name"          : "hudson",
    "version"       : "1.227,0-0",
    "attributes"    : { 
                      "pkg.summary" : "Hudson", 
                      "pkg.description" : "Extensible continuous integration system",
                      },
    "dirs"          : {},
    "files"         : {}
}


# add directories recursively, kind of like "mkdir -p"
def mkdirs(path,attributes={}):
    head = ""
    for i in path.split('/'):
        if len(i)==0 :
            continue
        if len(head) > 0 :
            head += '/'
        head += i
        if head not in pkg["dirs"]:
            # print 'Adding %s' % head
            pkg["dirs"][head] = attributes

# add a file, and create parent directories if necessary
def addfile(fullpath,attributes={}):
    components = fullpath.rpartition('/')
    mkdirs(components[0])
    pkg["files"][fullpath] = attributes;

addfile("/usr/local/bin/hudson.war",{"file":"./hudson.war"})
addfile("/var/svc/manifest/local/hudson.xml",{"file":"hudson.xml"})
# this is the Hudson home directory
mkdirs("/var/lib/hudson")

# TODO: how do I register SMF?
# see http://www.pauloswald.com/article/29/hudson-solaris-smf-manifest
