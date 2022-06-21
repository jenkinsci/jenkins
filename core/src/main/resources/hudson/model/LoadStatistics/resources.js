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
        const graphLocation = document.querySelector('.js-load-graph');
        if (graphLocation) {
            const parentSelector = graphLocation.getAttribute("data-graph-parent-selector");
            const baseUrl = graphLocation.getAttribute("data-graph-base-url");
            const graphAlt = graphLocation.getAttribute("data-graph-alt");
            const height = graphLocation.getAttribute("data-graph-height") || 500;
            let graphWidth = graphLocation.getAttribute("data-graph-width");
            const parent = parentSelector && document.querySelector(parentSelector);
            if (parent) {
                const availableWidth = parent.offsetWidth;
                const padding = 30;
                // for some browsers, the perfect width is not enough
                const quirkyBrowserAdjustment = 15;
                graphWidth = availableWidth - padding - quirkyBrowserAdjustment;
            }
            const graphUrl = baseUrl + "/png/?width=" + graphWidth + "&height=" + height;
            const graphImgTag = document.createElement("img");
            graphImgTag.src = graphUrl;
            graphImgTag.srcset = graphUrl + "&scale=2 2x";
            graphImgTag.alt = graphAlt;
            graphLocation.appendChild(graphImgTag);
            if (graphLocation.getAttribute("data-graph-has-map")) {
                graphImgTag.setAttribute("lazymap", baseUrl + "/map");
                Behaviour.applySubtree(graphImgTag);
            }
        }
    });
})();
