import { registerSortableTableDragDrop } from "@/sortable-drag-drop";

document.addEventListener("DOMContentLoaded", function () {
  document.querySelectorAll("tbody").forEach((table) =>
    registerSortableTableDragDrop(table, function () {
      document.getElementById("saveButton").classList.remove("jenkins-hidden");
    }),
  );
});
