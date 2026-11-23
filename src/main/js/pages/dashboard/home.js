import behaviorShim from "@/util/behavior-shim";
import { initItemList } from "@/pages/dashboard/item-list";

const applySubtree = (element) => behaviorShim.applySubtree(element, true);

behaviorShim.specify(".app-dashboard-items", "dashboard-items", 1000, (root) =>
  initItemList(root, { applySubtree }),
);
