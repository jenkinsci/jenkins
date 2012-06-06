package jenkins.plugins.ui_samples.FormFieldValidationWithContext.State;

def f = namespace(lib.FormTagLib)

f.entry(title:"State Name", field:"name") {
    f.textbox()
}

f.nested {
    table {
        f.section(title:"Capital city") {
            f.property(field:"capital")
        }

        f.entry(title:"Other cities") {
            f.repeatableProperty(field:"cities")
        }
    }
}
