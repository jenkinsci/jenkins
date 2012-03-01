package jenkins.plugins.ui_samples.InpageNavigationWithBreadcrumb

def l=namespace(lib.LayoutTagLib.class)

// put them under your l.layout
l.breadcrumb(title:"Click me! Click me!",id:"id-of-breadcrumb-item")
