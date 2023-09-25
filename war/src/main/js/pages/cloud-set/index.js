import { registerSortableTableDragDrop } from "@/sortable-drag-drop";

document.addEventListener("DOMContentLoaded", function () {
  document.querySelectorAll("tbody").forEach((table) =>
    registerSortableTableDragDrop(table, function () {
      YAHOO.util.Dom.removeClass(
        document.getElementById("saveButton"),
        "jenkins-hidden",
      );
    }),
  );
});
