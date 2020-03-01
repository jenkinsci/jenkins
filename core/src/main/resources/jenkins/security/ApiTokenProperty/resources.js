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
    var repeatedChunk = anchorRevoke.up('.repeated-chunk');
    var tokenList = repeatedChunk.up('.token-list');
    var confirmMessage = anchorRevoke.getAttribute('data-confirm');
    var targetUrl = anchorRevoke.getAttribute('data-target-url');
    
    var inputUuid = repeatedChunk.querySelector('input.token-uuid-input');
    var tokenUuid = inputUuid.value;

    if(confirm(confirmMessage)){
        new Ajax.Request(targetUrl, {
            method: "post",
            parameters: {tokenUuid: tokenUuid},
            onSuccess: function(rsp,_) {
                if(repeatedChunk.querySelectorAll('.legacy-token').length > 0){
                    // we are revoking the legacy token
                    var messageIfLegacyRevoked = anchorRevoke.getAttribute('data-message-if-legacy-revoked');
                    
                    var legacyInput = document.getElementById('apiToken');
                    legacyInput.value = messageIfLegacyRevoked;
                }
                repeatedChunk.remove();
                adjustTokenEmptyListMessage(tokenList);
                
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
    var targetUrl = button.getAttribute('data-target-url');
    var repeatedChunk = button.up('.repeated-chunk ');
    var tokenList = repeatedChunk.up('.token-list');
    var nameInput = repeatedChunk.querySelector('[name="tokenName"]');
    var tokenName = nameInput.value;
    
    new Ajax.Request(targetUrl, {
        method: "post",
        parameters: {"newTokenName": tokenName},
        onSuccess: function(rsp,_) {
            var json = rsp.responseJSON;
            var errorSpan = repeatedChunk.querySelector('.error');
            if(json.status === 'error'){
                errorSpan.innerHTML = json.message;
                errorSpan.addClassName('visible');

                button.removeClassName('request-pending');
            }else{
                errorSpan.removeClassName('visible');
                
                var tokenName = json.data.tokenName;
                // in case the name was empty, the application will propose a default one
                nameInput.value = tokenName;
                
                var tokenValue = json.data.tokenValue;
                var tokenValueSpan = repeatedChunk.querySelector('.new-token-value');
                tokenValueSpan.innerText = tokenValue;
                tokenValueSpan.addClassName('visible');

                // show the copy button
                var tokenCopyButton = repeatedChunk.querySelector('.copy-button');
                tokenCopyButton.setAttribute('text', tokenValue);
                tokenCopyButton.removeClassName('invisible')
                tokenCopyButton.addClassName('visible');
                
                var tokenUuid = json.data.tokenUuid;
                var uuidInput = repeatedChunk.querySelector('[name="tokenUuid"]');
                uuidInput.value = tokenUuid;

                var warningMessage = repeatedChunk.querySelector('.display-after-generation');
                warningMessage.addClassName('visible');

                // we do not want to allow user to create twice a token using same name by mistake
                button.remove();
                
                var revokeButton = repeatedChunk.querySelector('.token-revoke');
                revokeButton.removeClassName('hidden-button');
                
                var cancelButton = repeatedChunk.querySelector('.token-cancel');
                cancelButton.addClassName('hidden-button')
                
                repeatedChunk.addClassName('token-list-fresh-item');
                
                adjustTokenEmptyListMessage(tokenList);
            }
        }
    });
}

function adjustTokenEmptyListMessage(tokenList){
    var emptyListMessage = tokenList.querySelector('.token-list-empty-item');

    // number of token that are already existing or freshly created
    var numOfToken = tokenList.querySelectorAll('.token-list-existing-item, .token-list-fresh-item').length;
    if(numOfToken >= 1){
        if(!emptyListMessage.hasClassName('hidden-message')){
            emptyListMessage.addClassName('hidden-message');
        }
    }else{
        if(emptyListMessage.hasClassName('hidden-message')){
            emptyListMessage.removeClassName('hidden-message');
        }
    }
}
