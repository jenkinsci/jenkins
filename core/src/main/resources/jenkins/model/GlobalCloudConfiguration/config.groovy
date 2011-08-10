package jenkins.model.GlobalCloudConfiguration

import hudson.slaves.Cloud

def f=namespace(lib.FormTagLib)

def clouds = Cloud.all()

if (!clouds.isEmpty()) {
    f.section(title:_("Cloud")) {
        f.block {
            f.hetero_list(name:"cloud", hasHeader:true, descriptors:Cloud.all(), items:app.clouds,
                addCaption:_("Add a new cloud"), deleteCaption:_("Delete cloud"))
        }
    }
}
