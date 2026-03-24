import { registerSortableDragDrop } from "@/sortable-drag-drop";

document.addEventListener("DOMContentLoaded", function () {
  const bottomStickerShadow = document.querySelector(
    ".jenkins-bottom-app-bar__shadow",
  );
  const bottomSticker = document.querySelector("#bottom-sticker");

  // Hide the bottom sticker when the Save button is clicked
  document
    .querySelector("[name='Apply']")
    .addEventListener("click", hideBottomSticker);

  // Show the bottom sticker when clouds have been reordered
  const items = document.querySelector(".with-drag-drop");
  registerSortableDragDrop(items, showBottomSticker);

  function showBottomSticker() {
    bottomStickerShadow.classList.remove("jenkins-hidden");
    bottomSticker.classList.remove("jenkins-hidden");
  }

  function hideBottomSticker() {
    bottomStickerShadow.classList.add("jenkins-hidden");
    bottomSticker.classList.add("jenkins-hidden");
  }
});
