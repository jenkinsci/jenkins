package jenkins.model.GlobalCloudConfiguration

import hudson.slaves.Cloud

def f=namespace(lib.FormTagLib)

def clouds = Cloud.all()

if (!clouds.isEmpty()) {
    f.section(title:_("Cloud")) {
        f.entry(field: "clouds") {
            f.hetero_list(hasHeader:true, descriptors:Cloud.all(),
                addCaption:_("Add a new cloud"), deleteCaption:_("Delete cloud"))
        }
    }
}
