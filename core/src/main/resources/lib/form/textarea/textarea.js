Behaviour.specify("TEXTAREA.codemirror", 'textarea', 0, function(e) {
        //ensure, that textarea is visible, when obtaining its height, see JENKINS-25455
        function getTextareaHeight() {
            var p = e.parentNode.parentNode; //first parent is CodeMirror div, second is actual element which needs to be visible
            var display = p.style.display; 
            p.style.display = "";
            var h = e.clientHeight;
            p.style.display = display;
            return h;
        }
        
        var h = e.clientHeight || getTextareaHeight();
        var config = e.getAttribute("codemirror-config");
        config += (config ? ", " : " ") + "onBlur: function(editor){editor.save()}";
        config = eval('({'+config+'})');
        var codemirror = CodeMirror.fromTextArea(e,config);
        e.codemirrorObject = codemirror;
        if(typeof(codemirror.getScrollerElement) !== "function") {
            // Maybe older versions of CodeMirror do not provide getScrollerElement method.
            codemirror.getScrollerElement = function(){
                return findElementsBySelector(codemirror.getWrapperElement(), ".CodeMirror-scroll")[0];
            };
        }
        var scroller = codemirror.getScrollerElement();
        scroller.setAttribute("style","border:1px solid black;");
        scroller.style.height = h+"px";

        // the form needs to be populated before the "Apply" button
        if(e.up('form')) { // Protect against undefined element
    		Element.on(e.up('form'),"jenkins:apply", function() {
			e.value = codemirror.getValue()
		})
        }
		
        //refresh CM when there are some layout updates
        function refreshCM() {
            codemirror.refresh();
        }
        layoutUpdateCallback.add(refreshCM);
    });

Behaviour.specify("DIV.textarea-preview-container", 'textarea', 100, function (e) {
        var previewDiv = findElementsBySelector(e,".textarea-preview")[0];
        var showPreview = findElementsBySelector(e,".textarea-show-preview")[0];
        var hidePreview = findElementsBySelector(e,".textarea-hide-preview")[0];
        $(hidePreview).hide();
        $(previewDiv).hide();

        showPreview.onclick = function() {
            // Several TEXTAREAs may exist if CodeMirror is enabled. The first one has reference to the CodeMirror object.
            var textarea = e.parentNode.getElementsByTagName("TEXTAREA")[0];
            var text = textarea.codemirrorObject ? textarea.codemirrorObject.getValue() : textarea.value;
            var render = function(txt) {
                $(hidePreview).show();
                $(previewDiv).show();
                previewDiv.innerHTML = txt;
                layoutUpdateCallback.call();
            };

            new Ajax.Request(rootURL + showPreview.getAttribute("previewEndpoint"), {
                parameters: {
                    text: text
                },
                onSuccess: function(obj) {
                    render(obj.responseText)
                },
                onFailure: function(obj) {
                    render(obj.status + " " + obj.statusText + "<HR/>" + obj.responseText)
                }
            });
            return false;
        }

        hidePreview.onclick = function() {
            $(hidePreview).hide();
            $(previewDiv).hide();
        };
});
