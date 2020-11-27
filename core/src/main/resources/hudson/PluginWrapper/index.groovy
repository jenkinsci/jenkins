package hudson.PluginWrapper

import hudson.ExtensionList
import hudson.ExtensionPoint
import hudson.PluginWrapper
import hudson.model.Descriptor
import jenkins.model.Jenkins

import java.util.jar.Manifest

def l = namespace(lib.LayoutTagLib)
def t = namespace(lib.JenkinsTagLib)


void discoverExtensionPoints(Class originalClass, Class extensionClass, Map<Class, Set<Class>> mapping) {
    for (Class iface : extensionClass.getInterfaces()) {
        if (iface == ExtensionPoint.class) {
            if (!mapping.containsKey(extensionClass)) {
                mapping.put(extensionClass, new HashSet<Class>())
            }
            mapping.get(extensionClass).add(originalClass)
        } else {
            discoverExtensionPoints(originalClass, iface, mapping)
        }
    }
    Class superType = extensionClass.getSuperclass();
    if (superType != null && superType != Object.class) {
        discoverExtensionPoints(originalClass, superType, mapping)
    }
}

Class<Descriptor<?>> findDescriptorClass(Descriptor d) {
    def clazz = d.class
    while (clazz != Descriptor.class) {
        clazz = clazz.getSuperclass()
    }
    return clazz
}

Map<Class, Class> descriptorClassToDescribableClass = new HashMap<>();

String urlFor(Class clazz, PluginWrapper plugin, boolean isExtensionPoint) {
    if (isExtensionPoint) {
        // we need to do this e.g. for jenkins.scm.api.SCMFileSystem$Builder -> scmfilesystem-builder
        String anchor = clazz.getName().substring(clazz.getPackageName().length() + 1).toLowerCase(Locale.US).replace('$', '-')

        if (plugin == null) {
            return 'https://jenkins.io/doc/developer/extensions/jenkins-core/#' + anchor
        }
        return 'https://jenkins.io/doc/developer/extensions/' + plugin.getShortName() + '/#' + anchor
    } else {
        def classUrl = clazz.getName().replace('.', '/').replace('$', '.')
        return 'https://javadoc.jenkins.io/plugin/' + plugin.getShortName() + '/' + classUrl + '.html'
    }
}

l.layout {
    l.main_panel {
        h1 _(my.displayName)

        p {
            a(href: my.url, target: '_blank') {
                text(my.url)
            }
        }

        h2 _("Manifest")

        Manifest manifest = my.manifest
        ul {
            manifest.getMainAttributes().sort { e -> e.key.toString() }.each { k, v ->
                li {
                    strong {
                        text(k)
                        text(': ')
                    }
                    text(v)
                }
            }
        }

        h2 _("Implemented Extensions")

        p raw(_("extensionsBlurb"))

        Map<Class, Set<Class>> implementationsByExtensionPoints = new HashMap<>()
        for (Object extension : ExtensionList.lookup(Object.class)) {
            if (Jenkins.get().getPluginManager().whichPlugin(extension.class) == my) {
                if (extension instanceof Descriptor) {
                    descriptorClassToDescribableClass.put(extension.class, extension.clazz)
                    discoverExtensionPoints(extension.getClass(), extension.clazz, implementationsByExtensionPoints)
                } else {
                    discoverExtensionPoints(extension.getClass(), extension.getClass(), implementationsByExtensionPoints)
                }
            }
        }

        def extensionPoints = implementationsByExtensionPoints.keySet().sort { it.simpleName }
        if (extensionPoints.isEmpty()) {
            p _("noExtensionPoints")
        } else {
            for (Class extensionPoint : (extensionPoints)) {
                def plugin = Jenkins.get().getPluginManager().whichPlugin(extensionPoint)

                h3 {
                    a(href: urlFor(extensionPoint, plugin, true)) {
                        code {
                            text(extensionPoint.name)
                        }
                    }
                }

                text(" ")
                if (plugin == null) {
                    text(_("inCore"))
                } else {
                    raw(_("inPlugin", plugin.url, plugin.displayName))
                }

                ul {
                    for (Class extensionClass : implementationsByExtensionPoints.get(extensionPoint).sort { it.name }) {
                        li {
                            def extension = ExtensionList.lookup(extensionClass).find { it.class == extensionClass }
                            strong {
                                text(_("Implementation: "))
                            }

                            Class visibleExtensionClass = Descriptor.class.isAssignableFrom(extensionClass) ? descriptorClassToDescribableClass.get(extensionClass) : extensionClass
                            a(href: urlFor(visibleExtensionClass, (PluginWrapper) my, false)) {
                                code {
                                    text(visibleExtensionClass.name)
                                }
                            }
                            try {
                                if (extensionClass.getMethod("getDisplayName") != null && extension != null) {
                                    br()
                                    strong {
                                        text(_("Display Name: "))
                                    }
                                    em {
                                        text(extension.displayName)
                                    }
                                }
                            } catch (NoSuchMethodException nsme) { /* ignore */
                            }
                        }
                    }
                }
            }
        }

        h2 _("Defined Extension Points")

        p raw(_("definitionsBlurb"))

        implementationsByExtensionPoints = new HashMap<>()
        for (Object extension : ExtensionList.lookup(Object.class)) {
            if (extension instanceof Descriptor) {
                discoverExtensionPoints(extension.getClass(), extension.clazz, implementationsByExtensionPoints)
            } else {
                discoverExtensionPoints(extension.getClass(), extension.getClass(), implementationsByExtensionPoints)
            }
        }

        def extensionPointsDefinedInThisPlugin = implementationsByExtensionPoints.keySet().findAll { Jenkins.get().getPluginManager().whichPlugin(it) == my }.sort { it.simpleName }

        if (extensionPointsDefinedInThisPlugin.isEmpty()) {
            p _("noDefinitions")
        } else {
            for (Class extensionPoint : (extensionPointsDefinedInThisPlugin)) {
                h3 {
                    a(href: urlFor(extensionPoint, my, true)) {
                        code {
                            text(extensionPoint.name)
                        }
                    }
                }

                ul {
                    for (Class extensionClass : implementationsByExtensionPoints.get(extensionPoint).sort { it.name }) {
                        PluginWrapper extensionPlugin = Jenkins.get().getPluginManager().whichPlugin(extensionClass)
                        li {
                            def extension = ExtensionList.lookupSingleton(extensionClass)
                            strong {
                                text(_("Implementation in " + extensionPlugin.displayName + ": "))
                            }
                            a(href: urlFor(extensionClass, extensionPlugin, false)) {
                                code {
                                    text(extensionClass.name)
                                }
                            }
                            try {
                                if (extensionClass.getMethod("getDisplayName") != null) {
                                    br()
                                    strong {
                                        text(_("Display Name: "))
                                    }
                                    em {
                                        text(extension.displayName)
                                    }
                                }
                            } catch (NoSuchMethodException nsme) { /* ignore */
                            }
                        }
                    }
                }
            }
        }

        h2 _("Extensions without Descriptor or Known Extension Point")

        p raw(_("unclearBlurb"))

        List<Object> extensions = new ArrayList<>(ExtensionList.lookup(Object.class))
        extensions.removeAll(ExtensionList.lookup(ExtensionPoint.class))
        extensions = extensions.findAll { !(it instanceof Descriptor) }.findAll { my == Jenkins.get().getPluginManager().whichPlugin(it.getClass()) }

        if (extensions.isEmpty()) {
            p {
                text(_("noUnclear"))
            }
        } else {
            ul {
                for (Object o : extensions) {
                    li {
                        a(href:urlFor(o.class, (PluginWrapper) my, false)) {
                            code {
                                text(o.class.name)
                            }
                        }
                        try {
                            if (o.class.getMethod("getDisplayName") != null) {
                                br()
                                strong {
                                    text(_("Display Name: "))
                                }
                                em {
                                    text(o.class.displayName)
                                }
                            }
                        } catch (NoSuchMethodException nsme) { /* ignore */ }
                    }
                }
            }
        }
    }
}