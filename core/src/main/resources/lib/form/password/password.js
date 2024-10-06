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

Behaviour.specify(
  ".hidden-password",
  "hidden-password-button",
  0,
  function (e) {
    var secretUpdateBtn = e.querySelector(".hidden-password-update-btn");
    if (secretUpdateBtn === null) {
      return;
    }

    secretUpdateBtn.onclick = function () {
      e.querySelector(".hidden-password-field").setAttribute(
        "type",
        "password",
      );
      e.querySelector(".hidden-password-placeholder").remove();
      secretUpdateBtn.remove();
    };
  },
);

Behaviour.specify(
  "input[type='text'].complex-password-field",
  "empty-password-text-input",
  0,
  function (element) {
    element.addEventListener("input", function () {
      element.setAttribute("type", "password");
    });
  },
);
