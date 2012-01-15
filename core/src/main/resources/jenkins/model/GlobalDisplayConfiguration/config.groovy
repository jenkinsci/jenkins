package jenkins.model.GlobalDisplayConfiguration

import hudson.Functions

def f=namespace(lib.FormTagLib)

f.optionalBlock( field:"useShortDisplayName", checked:app.useShortDisplayName, title:_("Use short name display"))

