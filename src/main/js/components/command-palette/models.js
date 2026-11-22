import * as Symbols from "./symbols";
import { xmlEscape } from "@/util/security";

/**
 * @param {Object} params
 * @param {string} params.icon
 * @param {string} params.label
 * @param {string | null | undefined} params.description
 * @param {'symbol' | 'image'} params.type
 * @param {string} params.url
 * @param {string | null} params.group
 * @param {boolean | undefined} params.isExternal
 * @param {string | null | undefined} params.semantic
 * @param {(() => void) | undefined} params.onSelect
 */
export function LinkResult(params) {
  return {
    label: params.label,
    url: params.url,
    group: params.group,
    onSelect: params.onSelect,
    render: () => {
      const tag = params.onSelect ? "button" : "a";
      const clazz = [
        "jenkins-dropdown__item",
        "jenkins-command-palette__results__item",
        params.semantic
          ? "jenkins-!-" + params.semantic.toLowerCase() + "-color"
          : null,
      ]
        .filter(Boolean)
        .join(" ");
      const attributes = params.onSelect
        ? `type="button"`
        : `href="${xmlEscape(params.url)}"`;

      return `<${tag} class="${clazz}" ${attributes}>
        ${params.type === "image" ? `<img alt="${xmlEscape(params.label)}" class="jenkins-dropdown__item__icon jenkins-avatar" src="${params.icon}" />` : ""}
        ${params.type !== "image" ? `<div class="jenkins-dropdown__item__icon">${params.icon}</div>` : ""}
        <span class="jenkins-dropdown__item__label">${xmlEscape(params.label)}</span>
        ${params.description ? `<span class="jenkins-command-palette__results__item__description">${xmlEscape(params.description)}</span>` : ""}
        ${params.isExternal ? Symbols.EXTERNAL_LINK : ""}
    </${tag}>`;
    },
  };
}
