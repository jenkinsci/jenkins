import * as Symbols from "./symbols";
import { xmlEscape } from "@/util/security";

/**
 * @param {Object} params
 * @param {string} params.icon
 * @param {string} params.label
 * @param {'symbol' | 'image'} params.type
 * @param {string} params.url
 * @param {string | null} params.group
 * @param {boolean | undefined} params.isExternal
 */
export function LinkResult(params) {
  return {
    label: params.label,
    url: params.url,
    group: params.group,
    render: () => {
      return `<a class="jenkins-command-palette__results__item" href="${xmlEscape(
        params.url,
      )}">
        ${params.type === "image" ? `<img alt="${xmlEscape(params.label)}" class="jenkins-command-palette__results__item__icon jenkins-avatar" src="${params.icon}" />` : ""}
        ${params.type !== "image" ? `<div class="jenkins-command-palette__results__item__icon">${params.icon}</div>` : ""}
        ${xmlEscape(params.label)}
        ${params.isExternal ? Symbols.EXTERNAL_LINK : ""}
    </a>`;
    },
  };
}
