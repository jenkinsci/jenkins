Behaviour.register({
    "INPUT.advanced-button" : function(e) {
        makeButton(e,function(e) {
            var link = e.target;
            while(!Element.hasClassName(link,"advancedLink"))
                link = link.parentNode;
            link.style.display = "none"; // hide the button

            var container = $(link).next().down(); // TABLE -> TBODY

            var tr = link;
            while (tr.tagName != "TR")
                tr = tr.parentNode;

            // move the contents of the advanced portion into the main table
            var nameRef = tr.getAttribute("nameref");
            while (container.lastChild != null) {
                var row = container.lastChild;
                if(nameRef!=null && row.getAttribute("nameref")==null)
                    row.setAttribute("nameref",nameRef); // to handle inner rowSets, don't override existing values
                tr.parentNode.insertBefore(row, $(tr).next());
            }
            layoutUpdateCallback.call();
        });
        e = null; // avoid memory leak
    }
});