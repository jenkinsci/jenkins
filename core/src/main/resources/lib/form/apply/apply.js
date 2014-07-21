Behaviour.specify("INPUT.apply-button", 'apply', 0, function (e) {
        var id;
        var containerId = "container"+(iota++);

        var responseDialog = new YAHOO.widget.Panel("wait"+(iota++), {
            fixedcenter:true,
            close:true,
            draggable:true,
            zindex:4,
            modal:true,
            visible:false
        });

        responseDialog.setHeader("Error");
        responseDialog.setBody("<div id='"+containerId+"'></div>");
        responseDialog.render(document.body);
        var target; // iframe

        function attachIframeOnload(target, f) {
            if (target.attachEvent) {
                target.attachEvent("onload", f);
            } else {
                target.onload = f;
            }
        }

        makeButton(e,function (e) {
            var f = findAncestor(e.target, "FORM");

            // create a throw-away IFRAME to avoid back button from loading the POST result back
            id = "iframe"+(iota++);
            target = Element('iframe', {id: id, name: id, style: 'height:100%; width:100%'});
            $(containerId).appendChild(target);

            attachIframeOnload(target, function () {
                if (target.contentWindow && target.contentWindow.applyCompletionHandler) {
                    // apply-aware server is expected to set this handler
                    target.contentWindow.applyCompletionHandler(window);
                } else {
                    // otherwise this is possibly an error from the server, so we need to render the whole content.
                    var doc = target.contentDocument || target.contentWindow.document;
                    var error = doc.getElementById('error-description');
                    if (!error) {
                        // fallback if it's not a regular error dialog from oops.jelly: use the entire body
                        error = Element('div', {id: 'error-description'});
                        error.appendChild(doc.getElementsByTagName('body')[0]);
                    }

                    if (oldError = $('error-description')) {
                        // Remove old error if there is any
                        $(containerId).removeChild(oldError);
                    }

                    $(containerId).appendChild(error);
                    var r = YAHOO.util.Dom.getClientRegion();

                    var contentHeight = r.height*3/4;
                    var dialogStyleHeight = contentHeight+40;
                    var contentWidth = r.width*3/4;
                    var dialogStyleWidth = contentWidth+20;

                    $(containerId).style.height = contentHeight+"px";
                    $(containerId).style.width = contentWidth+"px";
                    $(containerId).style.overflow = "scroll";

                    responseDialog.cfg.setProperty("width", dialogStyleWidth+"px");
                    responseDialog.cfg.setProperty("height", dialogStyleHeight+"px");
                    responseDialog.center();
                    responseDialog.show();
                }
                window.setTimeout(function() {// otherwise Firefox will fail to leave the "connecting" state
                    $(id).remove();
                },0)
            });

            f.target = target.id;
            f.elements['core:apply'].value = "true";
            Event.fire(f,"jenkins:apply"); // give everyone a chance to write back to DOM
            try {
                buildFormTree(f);
                f.submit();
            } finally {
                f.elements['core:apply'].value = null;
                f.target = '_self';
            }
        });
});
