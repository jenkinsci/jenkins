def st=namespace("jelly:stapler")

namespace("/lib/samples").sample(title:_("Syntax Highlighted Text Area")) {
    p("CodeMirror can be used to turn ordinary text area into syntax-highlighted content-assistable text area")

    // this loads the necessary JavaScripts, if it hasn't loaded already
    // the first we load is the mode definition file (mode as in Emacs mode)
    // the second is the theme.
    //
    // for other modes, look for "clike.js" in your IDE and see adjacent folders.
    st.adjunct(includes:"org.kohsuke.stapler.codemirror.mode.clike.clike")
    st.adjunct(includes:"org.kohsuke.stapler.codemirror.theme.default")

    // TODO: adjunct tag doesn't work because 'wroteHEAD' is not set correctly
    // TODO: provide abstraction that hides CSS hookup

    // this text area is what we convert to the super text area
    // we use CSS class to hook up the initialization script. In this particular demo,
    // the ID attribute can be used, but in more general case (such as when you use this in your Builder, etc.,
    // a single web page may end up containing multiple instances of such text area, so the CSS class works better.
    textarea("class":"my-groovy-textbox", style:"width:100%; height:10em")

    // see CodeMirror web site for more about how to control the newly instantiated text area.
    script("""
        hudsonRules["TEXTAREA.my-groovy-textbox"] = function(e) {
            var w = CodeMirror.fromTextArea(e,{
              mode:"text/x-groovy",
              lineNumbers: true
            }).getWrapperElement();
            w.setAttribute("style","border:1px solid black; margin-top: 1em; margin-bottom: 1em")
        }
    """)
}
