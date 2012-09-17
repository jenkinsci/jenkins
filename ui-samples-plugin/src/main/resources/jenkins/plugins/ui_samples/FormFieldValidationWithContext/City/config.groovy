package jenkins.plugins.ui_samples.FormFieldValidationWithContext.City;

def f = namespace(lib.FormTagLib)

f.entry(title:"City Name", field:"name") {
    f.textbox()
}
