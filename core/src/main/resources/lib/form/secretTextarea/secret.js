/*
 * The MIT License
 *
 * Copyright (c) 2019 CloudBees, Inc.
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

Behaviour.specify('.secret', 'secret-button', 0, function (e) {
    var secretUpdateBtn = e.querySelector('.secret-update-btn');
    if (secretUpdateBtn === null) return;

    var id = 'secret-' + (iota++);
    var name = e.getAttribute('data-name');
    var placeholder = e.getAttribute('data-placeholder');
    var prompt = e.getAttribute('data-prompt');

    var appendSecretInput = function () {
        var textarea = document.createElement('textarea');
        textarea.setAttribute('id', id);
        textarea.setAttribute('name', name);
        if (placeholder !== null && placeholder !== '') {
            textarea.setAttribute('placeholder', placeholder);
        }
        var secretInput = document.createElement('div');
        secretInput.setAttribute('class', 'secret-input');
        secretInput.appendChild(textarea);
        e.appendChild(secretInput);
    }

    var clearSecretValue = function () {
        var secretValue = e.querySelector('input[type="hidden"]');
        if (secretValue !== null) {
            secretValue.parentNode.removeChild(secretValue);
        }
    }

    var replaceUpdateButton = function () {
        var secretLabel = document.createElement('label');
        secretLabel.setAttribute('for', id);
        secretLabel.appendChild(document.createTextNode(prompt));
        secretUpdateBtn.parentNode.replaceChild(secretLabel, secretUpdateBtn);
    }

    var removeSecretLegendLabel = function () {
        var secretLegend = e.querySelector('.secret-legend');
        var secretLegendText = secretLegend.querySelector('span');
        if (secretLegendText !== null) {
            secretLegend.removeChild(secretLegendText);
        }
    }

    secretUpdateBtn.onclick = function () {
        appendSecretInput();
        clearSecretValue();
        replaceUpdateButton();
        removeSecretLegendLabel();
        // fix UI bug when DOM changes
        Event.fire(window, 'jenkins:bottom-sticker-adjust');
    };
});
