package jenkins.model.GlobalDisplayConfiguration

def f=namespace(lib.FormTagLib)

f.optionalBlock( field:"useShortDisplayName", checked:app.useShortDisplayName, title:_("Use short name display")) {
    f.entry(title:_("Max job name length to display"), field:"shortDisplayNameLength") {
        f.number(clazz:"number", min:0)
    }
}

