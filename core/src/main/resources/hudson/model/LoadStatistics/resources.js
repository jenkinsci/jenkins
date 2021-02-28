/*
 * The MIT License
 *
 * Copyright (c) 2020, CloudBees, Inc.
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
(function(){
    document.addEventListener("DOMContentLoaded", function() {
        var graphLocation = document.querySelector('.js-load-graph');
        if (graphLocation) {
            var type = graphLocation.getAttribute("data-graph-type");
            var parentSelector = graphLocation.getAttribute("data-graph-parent-selector");
            var baseUrl = graphLocation.getAttribute("data-graph-base-url");
            var graphAlt = graphLocation.getAttribute("data-graph-alt");
            
            var parent = document.querySelector(parentSelector);
            if (parent) {
                var availableWidth = parent.offsetWidth;
                var padding = 30;
                // for some browsers, the perfect width is not enough
                var quirkyBrowserAdjustment = 15;
                var graphWidth = availableWidth - padding - quirkyBrowserAdjustment;

                // type in {sec10, min, hour}
                var graphUrl = baseUrl + "/graph?type" + type + "&width=" + graphWidth + "&height=500";
                var graphImgTag = document.createElement("img");
                graphImgTag.src = graphUrl;
                graphImgTag.alt = graphAlt;
                graphLocation.appendChild(graphImgTag);
            }
        }
    });
})();
