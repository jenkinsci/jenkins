Behaviour.specify("INPUT.combobox2", 'combobox', 100, function(e) {
        var items = [];

        var c = new ComboBox(e,function(value) {
            var candidates = [];
            for (var i=0; i<items.length; i++) {
                if (items[i].indexOf(value)==0) {
                    candidates.push(items[i]);
                    if (candidates.length>20)   break;
                }
            }
            return candidates;
        }, {});

        refillOnChange(e,function(params) {
            new Ajax.Request(e.getAttribute("fillUrl"),{
                parameters: params,
                onSuccess : function(rsp) {
                    items = eval('('+rsp.responseText+')');
                }
            });
        });
});