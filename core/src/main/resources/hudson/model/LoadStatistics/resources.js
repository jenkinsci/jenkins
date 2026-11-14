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
(function () {
  document.addEventListener("DOMContentLoaded", function () {
    const timespanSelect = document.querySelector("#timespan-select");
    timespanSelect.addEventListener("change", () => {
      const graphLocation = document.querySelector(".jenkins-load-graph");
      if (graphLocation) {
        const type = timespanSelect.value;
        const baseUrl = graphLocation.getAttribute("data-graph-base-url");
        const graphAlt = graphLocation.getAttribute("data-graph-alt");

        // type in {sec10, min, hour}
        // The SVG is inlined into the DOM (rather than referenced via an <img>) so that it
        // resolves the page's theme CSS variables and scales with its container via its viewBox.
        const graphUrl = baseUrl + "/graphSvg?type=" + type;
        fetch(graphUrl)
          .then((response) => response.text())
          .then((svg) => {
            graphLocation.innerHTML = svg;
            // The injected <svg> already carries role="img"; give it an accessible name.
            const svgElement = graphLocation.querySelector("svg");
            if (svgElement) {
              svgElement.setAttribute("aria-label", graphAlt);
            }
          });
      }
    });

    // Dispatch a change event to insert a graph on page load
    timespanSelect.dispatchEvent(new Event("change"));
  });
})();
