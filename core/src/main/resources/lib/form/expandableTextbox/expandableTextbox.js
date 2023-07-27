/*
 * The MIT License
 *
 * Copyright (c) 2023, CloudBees, Inc.
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
function expandTextArea(button) {
  button.style.display = "none";
  var field = button.parentNode.previousSibling.children[0];
  var value = field.value.replace(/ +/g, "\n");

  var n = button;
  while (!n.classList.contains("expanding-input") && n.tagName !== "TABLE") {
    n = n.parentNode;
  }

  var parent = n.parentNode;
  parent.innerHTML = "<textarea rows=8 class='jenkins-input'></textarea>";
  var textArea = parent.childNodes[0];
  textArea.name = field.name;
  textArea.value = value;

  layoutUpdateCallback.call();
}

Behaviour.specify(
  ".expanding-input__button > button[type='button']",
  "expandable-textbox-expand-button",
  0,
  function (element) {
    element.addEventListener("click", function () {
      expandTextArea(element);
    });
  },
);
