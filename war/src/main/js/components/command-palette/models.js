import * as Symbols from "./symbols";
import { xmlEscape } from "@/util/security";

export class LinkResult {
  constructor(icon, label, url, isExternal) {
    this.icon = icon;
    this.label = label;
    this.url = url;
    this.isExternal = isExternal;
  }
  render() {
    return `<a class="jenkins-command-palette__results__item" href="${xmlEscape(
      this.url,
    )}">
        <div class="jenkins-command-palette__results__item__icon">${
          this.icon
        }</div>
        ${xmlEscape(this.label)}
        ${this.isExternal ? Symbols.EXTERNAL_LINK : ""}
    </a>`;
  }
}
