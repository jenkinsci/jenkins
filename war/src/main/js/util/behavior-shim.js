function specify(selector, id, priority, behavior) {
  // eslint-ignore-next-line
  Behaviour.specify(selector, id, priority, behavior);
}

function applySubtree(startNode, includeSelf) {
  // eslint-ignore-next-line
  Behaviour.applySubtree(startNode, includeSelf);
}

export default { specify, applySubtree };
