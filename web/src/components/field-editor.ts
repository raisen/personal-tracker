import type { FieldConfig, FieldType } from '../types';
import { createModal } from './modal';

const FIELD_TYPES: { value: FieldType; label: string }[] = [
  { value: 'text', label: 'Text' },
  { value: 'number', label: 'Number' },
  { value: 'select', label: 'Select (dropdown)' },
  { value: 'radio', label: 'Radio (single choice)' },
  { value: 'checkbox', label: 'Checkbox (yes/no)' },
  { value: 'date', label: 'Date' },
  { value: 'time', label: 'Time' },
  { value: 'range', label: 'Range (slider)' },
];

export function openFieldEditor(
  existing: FieldConfig | null,
  onSave: (field: FieldConfig) => void,
): void {
  const isEdit = existing !== null;
  const modal = createModal(isEdit ? 'Edit Field' : 'Add Field');

  const field: FieldConfig = existing
    ? { ...existing, options: existing.options ? [...existing.options] : undefined }
    : {
        id: '',
        type: 'text',
        label: '',
        icon: '',
        required: false,
      };

  function render(): void {
    const needsOptions = field.type === 'select' || field.type === 'radio';
    const needsRange = field.type === 'number' || field.type === 'range';
    const isText = field.type === 'text';

    modal.body.innerHTML = `
      <div class="form-group">
        <label class="form-label">Label</label>
        <input type="text" class="form-input" id="field-label" value="${escHtml(field.label)}" placeholder="e.g. Mood" />
      </div>

      <div class="form-group">
        <label class="form-label">Icon (emoji)</label>
        <input type="text" class="form-input" id="field-icon" value="${escHtml(field.icon)}" placeholder="e.g. 😊" maxlength="4" />
      </div>

      <div class="form-group">
        <label class="form-label">Type</label>
        <select class="form-select" id="field-type">
          ${FIELD_TYPES.map(
            (t) =>
              `<option value="${t.value}" ${t.value === field.type ? 'selected' : ''}>${t.label}</option>`,
          ).join('')}
        </select>
      </div>

      <div class="form-group">
        <label class="option-label">
          <input type="checkbox" id="field-required" ${field.required ? 'checked' : ''} />
          Required
        </label>
      </div>

      ${
        needsOptions
          ? `<div class="form-group">
          <label class="form-label">Options (one per line)</label>
          <textarea class="form-textarea" id="field-options" rows="4" placeholder="Option 1&#10;Option 2&#10;Option 3">${(field.options ?? []).join('\n')}</textarea>
        </div>`
          : ''
      }

      ${
        needsRange
          ? `<div class="form-group">
          <label class="form-label">Min / Max / Step</label>
          <div style="display:flex;gap:8px">
            <input type="number" class="form-input" id="field-min" value="${field.min ?? ''}" placeholder="Min" />
            <input type="number" class="form-input" id="field-max" value="${field.max ?? ''}" placeholder="Max" />
            <input type="number" class="form-input" id="field-step" value="${field.step ?? ''}" placeholder="Step" />
          </div>
        </div>`
          : ''
      }

      ${
        isText
          ? `<div class="form-group">
          <label class="option-label">
            <input type="checkbox" id="field-multiline" ${field.multiline ? 'checked' : ''} />
            Multiline
          </label>
        </div>
        <div class="form-group">
          <label class="form-label">Placeholder</label>
          <input type="text" class="form-input" id="field-placeholder" value="${escHtml(field.placeholder ?? '')}" />
        </div>`
          : ''
      }
    `;

    // Re-render when type changes
    const typeSelect = modal.body.querySelector('#field-type') as HTMLSelectElement;
    typeSelect.addEventListener('change', () => {
      collectValues();
      field.type = typeSelect.value as FieldType;
      render();
    });
  }

  function collectValues(): void {
    const label = (modal.body.querySelector('#field-label') as HTMLInputElement)?.value.trim();
    if (label !== undefined) field.label = label ?? '';
    field.icon = (modal.body.querySelector('#field-icon') as HTMLInputElement)?.value.trim() ?? '';
    field.required = (modal.body.querySelector('#field-required') as HTMLInputElement)?.checked ?? false;

    const optionsEl = modal.body.querySelector('#field-options') as HTMLTextAreaElement | null;
    if (optionsEl) {
      field.options = optionsEl.value
        .split('\n')
        .map((s) => s.trim())
        .filter(Boolean);
    }

    const minEl = modal.body.querySelector('#field-min') as HTMLInputElement | null;
    const maxEl = modal.body.querySelector('#field-max') as HTMLInputElement | null;
    const stepEl = modal.body.querySelector('#field-step') as HTMLInputElement | null;
    if (minEl?.value) field.min = Number(minEl.value);
    else delete (field as Partial<FieldConfig>).min;
    if (maxEl?.value) field.max = Number(maxEl.value);
    else delete (field as Partial<FieldConfig>).max;
    if (stepEl?.value) field.step = Number(stepEl.value);
    else delete (field as Partial<FieldConfig>).step;

    const multilineEl = modal.body.querySelector('#field-multiline') as HTMLInputElement | null;
    if (multilineEl) field.multiline = multilineEl.checked;

    const placeholderEl = modal.body.querySelector('#field-placeholder') as HTMLInputElement | null;
    if (placeholderEl?.value) field.placeholder = placeholderEl.value;
    else delete (field as Partial<FieldConfig>).placeholder;
  }

  render();

  // Actions
  const cancelBtn = document.createElement('button');
  cancelBtn.className = 'btn btn-secondary';
  cancelBtn.textContent = 'Cancel';
  cancelBtn.addEventListener('click', () => modal.close());

  const saveBtn = document.createElement('button');
  saveBtn.className = 'btn btn-primary';
  saveBtn.textContent = isEdit ? 'Save' : 'Add';
  saveBtn.addEventListener('click', () => {
    collectValues();

    if (!field.label) {
      alert('Label is required');
      return;
    }

    // Generate ID from label if new
    if (!field.id) {
      field.id = field.label
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, '_')
        .replace(/^_|_$/g, '');
    }

    onSave(field);
    modal.close();
  });

  modal.actions.appendChild(cancelBtn);
  modal.actions.appendChild(saveBtn);

  modal.show();
}

function escHtml(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}
