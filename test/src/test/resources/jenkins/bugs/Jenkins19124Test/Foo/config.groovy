package jenkins.bugs.Jenkins19124Test.Foo;

def f = namespace(lib.FormTagLib)

f.entry(field:"alpha") {
    f.textbox()
}
f.entry(field:"bravo") {
    f.select()
}