/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
function selectAll(anchor){
    var parent = anchor.up('.legacy-token-usage');
    var allCheckBoxes = parent.querySelectorAll('.token-to-revoke');
    var concernedCheckBoxes = allCheckBoxes;
    
    checkTheDesiredOne(allCheckBoxes, concernedCheckBoxes);
}

function selectFresh(anchor){
    var parent = anchor.up('.legacy-token-usage');
    var allCheckBoxes = parent.querySelectorAll('.token-to-revoke');
    var concernedCheckBoxes = parent.querySelectorAll('.token-to-revoke.fresh-token');
    
    checkTheDesiredOne(allCheckBoxes, concernedCheckBoxes);
}

function selectRecent(anchor){
    var parent = anchor.up('.legacy-token-usage');
    var allCheckBoxes = parent.querySelectorAll('.token-to-revoke');
    var concernedCheckBoxes = parent.querySelectorAll('.token-to-revoke.recent-token');
    
    checkTheDesiredOne(allCheckBoxes, concernedCheckBoxes);
}

function checkTheDesiredOne(allCheckBoxes, concernedCheckBoxes){
    var mustCheck = false;
    for(var i = 0; i < concernedCheckBoxes.length && !mustCheck ; i++){
        var checkBox = concernedCheckBoxes[i];
        if(!checkBox.checked){
            mustCheck = true;
        }
    }
    
    for(var i = 0; i < allCheckBoxes.length ; i++){
        var checkBox = allCheckBoxes[i];
        checkBox.checked = false;
    }
    
    for(var i = 0; i < concernedCheckBoxes.length ; i++){
        var checkBox = concernedCheckBoxes[i];
        checkBox.checked = mustCheck;
    }
    
    for(var i = 0; i < allCheckBoxes.length ; i++){
        var checkBox = allCheckBoxes[i];
        onCheckChanged(checkBox);
    }
}

function confirmAndRevokeAllSelected(button){
    var parent = button.up('.legacy-token-usage');
    var allCheckBoxes = parent.querySelectorAll('.token-to-revoke');
    var allCheckedCheckBoxes = [];
    for(var i = 0; i < allCheckBoxes.length ; i++){
        var checkBox = allCheckBoxes[i];
        if(checkBox.checked){
            allCheckedCheckBoxes.push(checkBox);
        }
    }
    
    if(allCheckedCheckBoxes.length == 0){
        var nothingSelected = button.getAttribute('data-nothing-selected');
        alert(nothingSelected);
    }else{
        var confirmMessageTemplate = button.getAttribute('data-confirm-template');
        var confirmMessage = confirmMessageTemplate.replace('%num%', allCheckedCheckBoxes.length);
        if(confirm(confirmMessage)){
            var url = button.getAttribute('data-url');
            var selectedValues = [];
            
            for(var i = 0; i < allCheckedCheckBoxes.length ; i++){
                var checkBox = allCheckedCheckBoxes[i];
                var userId = checkBox.getAttribute('data-user-id');
                var uuid = checkBox.getAttribute('data-uuid');
                selectedValues.push({userId: userId, uuid: uuid});
            }
            
            var params = {values: selectedValues}
            new Ajax.Request(url, {
                postBody: Object.toJSON(params),
                contentType:"application/json",
                encoding:"UTF-8",
                onComplete: function(rsp) {
                    window.location.reload();
                }
            });
        }
    }
}

function onLineClicked(event){
    var line = this;
    var checkBox = line.querySelector('.token-to-revoke');
    // to allow click on checkbox to act normally
    if(event.target === checkBox){
        return;
    }
    checkBox.checked = !checkBox.checked;
    onCheckChanged(checkBox);
}

function onCheckChanged(checkBox){
    var line = checkBox.up('tr');
    if(checkBox.checked){
        line.addClassName('selected');
    }else{
        line.removeClassName('selected');
    }
}

(function(){
    document.addEventListener("DOMContentLoaded", function() {
        var allLines = document.querySelectorAll('.legacy-token-usage table tr');
        for(var i = 0; i < allLines.length; i++){
            var line = allLines[i];
            if(!line.hasClassName('no-token-line')){
                line.onclick = onLineClicked;
            }
        }
        
        var allCheckBoxes = document.querySelectorAll('.token-to-revoke');
        for(var i = 0; i < allCheckBoxes.length; i++){
            var checkBox = allCheckBoxes[i];
            checkBox.onchange = function(){ onCheckChanged(this); };
        }
    });
})()
