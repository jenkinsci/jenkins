function specify(selector, id, priority, behavior) {
  Behaviour.specify(selector, id, priority, behavior);
}

function applySubtree(startNode, includeSelf) {
  Behaviour.applySubtree(startNode, includeSelf);
}

export default { specify, applySubtree };
