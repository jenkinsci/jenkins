// @include org.kohsuke.stapler.d3
(function() {
    var DUR = 1000;
    function idem(x) { return x; }

    var widget = d3.select("#buildQueue");
    var sel = widget.selectAll("tr.existing");

    var enterTransition = idem; // the first time there won't be any transition
    function exitTransition(sel) {
        return sel.transition().duration(DUR);
    }

    function update(data) {
        enterTransition = function (sel) { return sel.transition()/*.delay(DUR)*/.duration(DUR); };
        var sel = widget.selectAll("tr.existing").data(data,function(d){return d.id;});
        enter(sel);
        exit(sel);

        Behaviour.applySubtree(widget);
        layoutUpdateCallback.call();
    }

    // for entering data points
    function enter(sel) {
        var h = widget.select("TD.pane-header").style("height"); // row height

        // 'existing' class is used so that we can insert new rows at the top, not bottom

        var trs = sel.enter().insert("tr","tr.existing")
            .style("opacity",0)     // start transparent and it will transition to opaque
            .classed("existing",true)
            .html(function (d){ return d.html; });
        enterTransition(trs)
            .style("opacity",1);

        var from = "#FFF";  // TODO: RGBA
        var to   = "#BBB";
        var tds = trs.selectAll("td")
            .style("border-top-color",from)
            .style("border-bottom-color",from)
            .style("padding-top","0px")
            .style("padding-bottom","0px");
        enterTransition(tds)
            .style("border-top-color",to)
            .style("border-bottom-color",to)
            .style("padding-top","3px")
            .style("padding-bottom","3px");         // any way to retrieve these values from CSS?

        var divs = tds.selectAll("div")
            .style("height",0);
        enterTransition(divs)
                .style("height",h);

    }

    // for exiting data points
    function exit(sel) {
        sel = sel.exit();
        exitTransition(sel)
            .style("opacity",0)
            .remove();

        exitTransition(sel.selectAll("TD"))
            .style("border-top-color","#FFF")
            .style("border-bottom-color","#FFF")
            .style("padding-top","0px")
            .style("padding-bottom","0px");

        exitTransition(sel.selectAll("DIV"))
            .style("height","0px");
    }

    // TODO: share this function with others
    function refreshPart(url,callback) {
        var f = function() {
            if(isPageVisible()) {
                new Ajax.Request(url, {
                    onSuccess: function(rsp) {
                        callback(rsp.responseJSON);

                        if(isRunAsTest) return;
                        refreshPart(url,callback);
                    }
                });
            } else {
                // postpone
                if(isRunAsTest) return;
                refreshPart(url,callback);
            }

        };
        // if run as test, just do it once and do it now to make sure it's working,
        // but don't repeat.
        if(isRunAsTest) f();
        else    window.setTimeout(f, 3000);
    }

    refreshPart(rootURL+"/queue/display",update);
})();