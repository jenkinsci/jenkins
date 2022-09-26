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
const Fetch = {
  get: function (url, callback) {
    const xhr = new XMLHttpRequest();
    xhr.open("GET", url, true);
    xhr.onload = function () {
      const status = xhr.status;
      if (callback) {
        callback(null, status);
      }
    };
    xhr.onerror = function () {
      const message = xhr.responseText;
      const status = xhr.status;
      if (callback) {
        callback(message, status);
      }
    };
    xhr.send();
  },
};

function safeRedirector(url) {
  const timeout = 5000;
  window.setTimeout(function () {
    const statusChecker = arguments.callee;
    Fetch.get(url, function (error, status) {
      if ((status >= 502 && status <= 504) || status === 0) {
        window.setTimeout(statusChecker, timeout);
      } else {
        window.location.replace(url);
      }
      if (error) {
        console.error(error);
      }
    });
  }, timeout);
}

const rootUrl = document.head.getAttribute("data-rooturl");
safeRedirector(rootUrl + "/");
