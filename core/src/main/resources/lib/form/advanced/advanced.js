Behaviour.specify("INPUT.advanced-button", 'advanced', 0, function(e) {
        makeButton(e,function(e) {
            var link = $(e.target).up(".advancedLink");
            link.style.display = "none"; // hide the button

            var container = link.next().down(); // TABLE -> TBODY

            var tr = link.up("TR");

            // move the contents of the advanced portion into the main table
            var nameRef = tr.getAttribute("nameref");
            while (container.lastChild != null) {
                var row = container.lastChild;
                if(nameRef!=null && row.getAttribute("nameref")==null)
                    row.setAttribute("nameref",nameRef); // to handle inner rowSets, don't override existing values
                $(row).setOpacity(0);

                tr.parentNode.insertBefore(row, $(tr).next());

                new YAHOO.util.Anim(row, {
                    opacity: { to:1 }
                }, 0.2, YAHOO.util.Easing.easeIn).animate();

            }
            layoutUpdateCallback.call();
        });
        e = null; // avoid memory leak
});