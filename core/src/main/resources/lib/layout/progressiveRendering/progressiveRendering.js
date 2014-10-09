/* 
 * The MIT License
 *
 * Copyright 2012 Jesse Glick.
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

function progressivelyRender(handler, callback, statusId) {
    function checkNews(response) {
        var r = response.responseObject();
        if (r.status == 'done') {
            callback(r.data);
            $(statusId).style.display = 'none';
        } else if (r.status == 'canceled') {
            // TODO ugly; replace with single tr of class=unknown?
            $$('#' + statusId + ' .progress-bar-done')[0].innerHTML = 'Aborted.';
        } else if (r.status == 'error') {
            $$('#' + statusId + ' .progress-bar-done')[0].style.width = '100%';
            $$('#' + statusId + ' .progress-bar-left')[0].style.width = '0%';
            $(statusId).className = 'progress-bar red';
        } else {
            callback(r.data);
            $$('#' + statusId + ' .progress-bar-done')[0].style.width = (100 * r.status) + '%';
            $$('#' + statusId + ' .progress-bar-left')[0].style.width = (100 - 100 * r.status) + '%';
            checkNewsLater(500);
        }
    }
    function checkNewsLater(timeout) {
        setTimeout(function() {
            handler.news(checkNews);
        }, timeout);
    }
    handler.start(function(response) {
        checkNewsLater(0);
    });
}
