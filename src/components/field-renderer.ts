import type { FieldConfig } from '../types';

export function renderField(
  field: FieldConfig,
  value?: unknown,
): HTMLDivElement {
  const group = document.createElement('div');
  group.className = 'form-group';
  group.dataset['fieldId'] = field.id;

  const label = document.createElement('label');
  label.className = 'form-label';
  label.innerHTML = `${field.icon ? `<span class="field-icon">${field.icon}</span>` : ''}${field.label}${field.required ? '<span class="required">*</span>' : ''}`;

  group.appendChild(label);

  switch (field.type) {
    case 'text': {
      if (field.multiline) {
        const textarea = document.createElement('textarea');
        textarea.className = 'form-textarea';
        textarea.name = field.id;
        textarea.required = field.required;
        if (field.placeholder) textarea.placeholder = field.placeholder;
        if (typeof value === 'string') textarea.value = value;
        group.appendChild(textarea);
      } else {
        const input = document.createElement('input');
        input.type = 'text';
        input.className = 'form-input';
        input.name = field.id;
        input.required = field.required;
        if (field.placeholder) input.placeholder = field.placeholder;
        if (typeof value === 'string') input.value = value;
        group.appendChild(input);
      }
      break;
    }

    case 'number':
    case 'range': {
      const input = document.createElement('input');
      input.type = field.type;
      input.className = 'form-input';
      input.name = field.id;
      input.required = field.required;
      if (field.min !== undefined) input.min = String(field.min);
      if (field.max !== undefined) input.max = String(field.max);
      if (field.step !== undefined) input.step = String(field.step);
      if (typeof value === 'number') input.value = String(value);
      if (field.type === 'range') {
        const display = document.createElement('span');
        display.className = 'text-secondary text-sm';
        display.textContent = input.value || String(field.min ?? 0);
        input.addEventListener('input', () => {
          display.textContent = input.value;
        });
        group.appendChild(input);
        group.appendChild(display);
      } else {
        group.appendChild(input);
      }
      break;
    }

    case 'date':
    case 'time': {
      const input = document.createElement('input');
      input.type = field.type;
      input.className = 'form-input';
      input.name = field.id;
      input.required = field.required;
      if (typeof value === 'string') input.value = value;
      group.appendChild(input);
      break;
    }

    case 'select': {
      const select = document.createElement('select');
      select.className = 'form-select';
      select.name = field.id;
      select.required = field.required;

      const defaultOpt = document.createElement('option');
      defaultOpt.value = '';
      defaultOpt.textContent = `Select ${field.label.toLowerCase()}...`;
      defaultOpt.disabled = true;
      defaultOpt.selected = !value;
      select.appendChild(defaultOpt);

      for (const opt of field.options ?? []) {
        const option = document.createElement('option');
        option.value = opt;
        option.textContent = opt;
        if (value === opt) option.selected = true;
        select.appendChild(option);
      }
      group.appendChild(select);
      break;
    }

    case 'radio': {
      const optionGroup = document.createElement('div');
      optionGroup.className = 'option-group';
      for (const opt of field.options ?? []) {
        const optLabel = document.createElement('label');
        optLabel.className = 'option-label';
        const radio = document.createElement('input');
        radio.type = 'radio';
        radio.name = field.id;
        radio.value = opt;
        radio.required = field.required;
        if (value === opt) radio.checked = true;
        optLabel.appendChild(radio);
        optLabel.appendChild(document.createTextNode(opt));
        optionGroup.appendChild(optLabel);
      }
      group.appendChild(optionGroup);
      break;
    }

    case 'checkbox': {
      const wrapper = document.createElement('div');
      wrapper.className = 'checkbox-wrapper';
      const cb = document.createElement('input');
      cb.type = 'checkbox';
      cb.name = field.id;
      if (value === true) cb.checked = true;
      const cbLabel = document.createElement('span');
      cbLabel.textContent = 'Yes';
      wrapper.appendChild(cb);
      wrapper.appendChild(cbLabel);
      group.appendChild(wrapper);
      break;
    }
  }

  return group;
}

export function getFieldValue(
  container: HTMLElement,
  field: FieldConfig,
): unknown {
  const el = container.querySelector(`[name="${field.id}"]`) as
    | HTMLInputElement
    | HTMLSelectElement
    | HTMLTextAreaElement
    | null;
  if (!el) return undefined;

  switch (field.type) {
    case 'checkbox':
      return (el as HTMLInputElement).checked;
    case 'number':
    case 'range':
      return el.value === '' ? undefined : Number(el.value);
    default:
      return el.value || undefined;
  }
}
