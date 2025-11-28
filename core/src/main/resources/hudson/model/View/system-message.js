document.addEventListener("DOMContentLoaded", function () {
  // --- HELPER: CLEAN & PARSE MARKDOWN ---
  function processSystemMessage(rawText) {
    if (!rawText) {
      return { html: "", plain: "" };
    } // Safety check

    var safe = rawText
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#039;");

    var clean = safe
      .replace(/\[URGENT\]/i, "")
      .replace(/\[MAINTENANCE\]/i, "")
      .replace(/\[UPDATE\]/i, "")
      .trim();

    var html = clean
      .replace(/\*\*(.*?)\*\*/g, "<b>$1</b>")
      .replace(/\*(.*?)\*/g, "<i>$1</i>")
      .replace(
        /`(.*?)`/g,
        '<code style="background:var(--background-hover); padding:2px 4px; border-radius:3px;">$1</code>',
      )
      .replace(
        /\[([^\]]+)\]\(([^)]+)\)/g,
        '<a href="$2" style="text-decoration:underline;">$1</a>',
      );

    return { html: html, plain: clean };
  }

  // --- PART 1: DASHBOARD BANNER ---
  var contentDiv = document.getElementById("system-message-content");
  if (contentDiv) {
    var raw = contentDiv.getAttribute("data-raw");
    var result = processSystemMessage(raw);
    contentDiv.innerHTML = result.html;
  }

  // --- PART 2: GLOBAL TOAST ---
  var banner = document.getElementById("systemmessage");
  var toast = document.getElementById("jenkins-global-toast");
  var toastContent = document.getElementById("jenkins-toast-content");
  var closeBtn = document.getElementById("toast-close-btn");

  if (toast) {
    var rawMsg = toast.getAttribute("data-message");

    // MEMORY CHECK: Has user dismissed THIS specific message?
    // We use a simple hash of the message string to detect if the Admin changed it.
    var msgHash = "dismissed_" + btoa(unescape(encodeURIComponent(rawMsg)));
    var isDismissed = sessionStorage.getItem(msgHash);

    // A. Show Toast logic
    // Show ONLY if: (Not on Dashboard) AND (Not Dismissed)
    if (!banner && !isDismissed) {
      toast.style.display = "block";

      // B. Render Markdown
      var toastResult = processSystemMessage(rawMsg);
      if (toastContent) {
        toastContent.innerHTML = toastResult.html;
      }
    }

    // C. Close Button Handler
    if (closeBtn) {
      closeBtn.onclick = function (e) {
        // IMPORTANT: Stop propagation so we don't trigger the toast.onclick alert
        e.stopPropagation();

        // Hide the toast
        toast.style.display = "none";

        // Save to session storage (It won't appear again until browser restart or message change)
        sessionStorage.setItem(msgHash, "true");
      };
    }

    // D. Main Click Handler (Browser Alert)
    toast.onclick = function () {
      var cleanText = rawMsg
        .replace(/\[.*?\]/g, "")
        .replace(/\*\*/g, "")
        .replace(/\[(.*?)\]\(.*?\)/g, "$1");
      alert(cleanText.trim());
    };
  }
});
