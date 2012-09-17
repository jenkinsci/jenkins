// prototype object to be duplicated for each radio button group
var radioBlockSupport = {
    buttons : null, // set of functions, one for updating one radio block each

    updateButtons : function() {
        for( var i=0; i<this.buttons.length; i++ )
            this.buttons[i]();
    },

    // update one block based on the status of the given radio button
    updateSingleButton : function(radio, blockStart, blockEnd) {
        var show = radio.checked;
        blockStart = $(blockStart);

        if (blockStart.getAttribute('hasHelp') == 'true') {
            n = blockStart.next();
        } else {
            n = blockStart;
        }
        while((n = n.next()) != blockEnd) {
          n.style.display = show ? "" : "none";
        }
        layoutUpdateCallback.call();
    }
};

// this needs to happen before TR.row-set-end rule kicks in.
Behaviour.specify("INPUT.radio-block-control", 'radioBlock', -100, function(r) {
        r.id = "radio-block-"+(iota++);

        // when one radio button is clicked, we need to update foldable block for
        // other radio buttons with the same name. To do this, group all the
        // radio buttons with the same name together and hang it under the form object
        var f = r.form;
        var radios = f.radios;
        if (radios == null)
            f.radios = radios = {};

        var g = radios[r.name];
        if (g == null) {
            radios[r.name] = g = object(radioBlockSupport);
            g.buttons = [];
        }

        var s = findAncestorClass(r,"radio-block-start");
        s.setAttribute("ref", r.id);

        // find the end node
        var e = (function() {
            var e = s;
            var cnt=1;
            while(cnt>0) {
                e = $(e).next();
                if (Element.hasClassName(e,"radio-block-start"))
                    cnt++;
                if (Element.hasClassName(e,"radio-block-end"))
                    cnt--;
            }
            return e;
        })();

        var u = function() {
            g.updateSingleButton(r,s,e);
        };
        g.buttons.push(u);

        // apply the initial visibility
        u();

        // install event handlers to update visibility.
        // needs to use onclick and onchange for Safari compatibility
        r.onclick = r.onchange = function() { g.updateButtons(); };
});
