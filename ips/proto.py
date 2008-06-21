# definition of the IPS package.
# see http://wiki.updatecenter.java.net/Wiki.jsp?page=UC20.Docs.Packaging for more about this

mode755 = {"mode":"0755"}

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
def mkdirs(path):
    head = ""
    for i in path.split('/'):
        if len(i)==0 :
            continue
        if len(head) > 0 :
            head += '/'
        head += i
        if head not in pkg["dirs"]:
            print 'Adding %s' % head
            pkg["dirs"][head] = mode755

# add a file, and create parent directories if necessary
def addfile(fullpath,properties={}):
    components = fullpath.rpartition('/')
    mkdirs(components[0])

mkdirs("/var/run/hudson")
mkdirs("/var/log/hudson")
addfile("/usr/local/bin/hudson.war")
addfile("/var/svc/manifest/local/hudson.xml")
# TODO: how do I register SMF?
# see http://www.pauloswald.com/article/29/hudson-solaris-smf-manifest
