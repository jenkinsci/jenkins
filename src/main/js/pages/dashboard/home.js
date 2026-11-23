import behaviorShim from "@/util/behavior-shim";
import { initItemList } from "@/pages/dashboard/item-list";
import { initRecent } from "@/pages/dashboard/recent";

const applySubtree = (element) => behaviorShim.applySubtree(element, true);

behaviorShim.specify(".app-dashboard-items", "dashboard-items", 1000, (root) =>
  initItemList(root, { applySubtree }),
);

// Before the tooltip behaviour, which adds attributes to the rows, so that the rows
// are recorded as the server rendered them
behaviorShim.specify("#dashboard-recent", "dashboard-recent", 999, (root) =>
  initRecent(root, { applySubtree }),
);
