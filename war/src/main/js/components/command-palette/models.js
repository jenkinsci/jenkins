import * as Symbols from "./symbols";
import { getSymbol } from "@/api/symbols";

export class LinkResult {
  constructor(icon, label, description, category, url, isExternal) {
    this.icon = icon;
    this.label = label;
    this.description = description;
    this.category = category;
    this.url = url;
    this.isExternal = isExternal;
  }
  render() {
    let icon2;

    getSymbol(this.icon, (e) => {
      icon2 = e;
    });

    // this.icon.includes("symbol-") ? getSymbol(this.icon, (e) => {icon = e}) : `<img src="${this.icon}" alt="" />`

    return `<a class="jenkins-command-palette__results__item" href="${
      this.url
    }">
        <div class="jenkins-command-palette__results__item__icon">${icon2}</div>
        ${this.label}
        ${
          this.description
            ? `<span class="jenkins-command-palette__results__item__description">${this.description}</span>`
            : ``
        }
        ${this.isExternal ? Symbols.EXTERNAL_LINK : Symbols.CHEVRON_RIGHT}
    </a>`;
  }
}
