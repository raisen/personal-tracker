import type { FieldConfig, Entry } from './types';

export function formatDate(iso: string): string {
  try {
    const d = new Date(iso);
    return d.toLocaleDateString(undefined, {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    });
  } catch {
    return iso;
  }
}

export function exportToCsv(
  fields: FieldConfig[],
  entries: Entry[],
): void {
  const headers = ['_id', '_created', '_updated', ...fields.map((f) => f.label)];
  const keys = ['_id', '_created', '_updated', ...fields.map((f) => f.id)];

  const rows = entries.map((entry) =>
    keys.map((key) => {
      const val = entry[key];
      if (val === undefined || val === null) return '';
      const str = String(val);
      // Escape CSV values
      if (str.includes(',') || str.includes('"') || str.includes('\n')) {
        return `"${str.replace(/"/g, '""')}"`;
      }
      return str;
    }),
  );

  const csv = [headers.join(','), ...rows.map((r) => r.join(','))].join('\n');
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `tracker-export-${new Date().toISOString().slice(0, 10)}.csv`;
  link.click();
  URL.revokeObjectURL(url);
}
