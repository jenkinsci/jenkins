import { LitElement, html } from 'lit';
import { customElement, property } from 'lit/decorators';
import Styles from './Checkbox.styles';

@customElement('jenkins-checkbox')
export class Checkbox extends LitElement {
  static styles = [Styles];

  @property({ type: String }) id: string = '1234';

  @property({ type: String }) name: string;

  @property({ type: String }) title: string;

  @property({ type: String }) tooltip: string;

  @property({ type: String }) value: string;

  @property({ type: Boolean }) checked: boolean = false;

  @property({ type: Boolean }) readonly: boolean = false;

  @property({ type: Boolean }) json: boolean;

  handleChange() {
    this.checked = !this.checked;
    this.dispatchEvent(
      new Event('change', {
        bubbles: true,
        composed: true,
      })
    );
  }

  render() {
    return html`<div
      class="jenkins-checkbox"
      @click="${this.handleChange}"
      @keydown="${this.handleChange}"
    >
      <input
        type="checkbox"
        name="${this.name}"
        title="${this.tooltip || this.title}"
        id="${this.id}"
        json="${this.json}"
        ?disabled="${this.readonly}"
        ?checked="${this.checked}"
      />
      <label
        for="${this.name}"
        class="attach-previous ${this.title == null
          ? 'js-checkbox-label-empty'
          : ''}"
        title="${this.tooltip || this.title}"
        >${this.title}</label
      >
    </div>`;
  }
}
