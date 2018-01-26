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
function revokeToken(anchorRevoke){
    var listItemParent = anchorRevoke.up('.token-list-item');
    var confirmMessage = anchorRevoke.attributes['data-confirm'].value;
    var targetUrl = anchorRevoke.attributes['data-target-url'].value;
    
    var inputUuid = listItemParent.querySelector('input.token-uuid-input');
    var tokenId = inputUuid.value;

    console.warn('revokeToken');
    if(confirm(confirmMessage)){
        new Ajax.Request(targetUrl, {
            method: "post",
            parameters: {tokenId: tokenId},
            onSuccess: function(rsp,_) {
                listItemParent.remove();
            }
        });
    }

    return false;
}

function saveApiToken(button){
    if(button.hasClassName('request-pending')){
        // avoid multiple requests to be sent if user is clicking multiple times
        return;
    }
    button.addClassName('request-pending');
    var targetUrl = button.attributes['data-target-url'].value;
    var rowParent = button.up('tr');
    var nameInput = rowParent.querySelector('[name="newTokenName"]');
    var tokenName = nameInput.value;
    
    new Ajax.Request(targetUrl, {
        method: "post",
        parameters: {"newTokenName": tokenName},
        onSuccess: function(rsp,_) {
            var json = rsp.responseJSON;
            var errorSpan = rowParent.querySelector('.error');
            if(json.status === 'error'){
                errorSpan.style.display = 'block';
                errorSpan.innerHTML = json.message;

                button.removeClassName('request-pending');
            }else{
                errorSpan.style.display = 'none';
                
                nameInput.setAttribute('readonly', 'readonly');
                
                var tokenValue = json.data.tokenValue;
                var valueInput = rowParent.querySelector('#newTokenValue');
                valueInput.value = tokenValue;
                valueInput.style.display = 'inline-block';

                // we do not want to allow user to create twice a token using same name by mistake
                button.remove();
            }
        }
    });
}
