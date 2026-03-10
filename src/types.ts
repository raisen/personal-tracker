export type FieldType =
  | 'text'
  | 'number'
  | 'select'
  | 'radio'
  | 'checkbox'
  | 'date'
  | 'time'
  | 'range';

export interface FieldConfig {
  id: string;
  type: FieldType;
  label: string;
  icon: string;
  required: boolean;
  options?: string[];
  min?: number;
  max?: number;
  step?: number;
  multiline?: boolean;
  placeholder?: string;
}

export interface TrackerConfig {
  version: number;
  title: string;
  fields: FieldConfig[];
}

export interface Entry {
  _id: string;
  _created: string;
  _updated: string;
  [key: string]: unknown;
}

export interface TrackerData {
  entries: Entry[];
}

export interface GistFile {
  filename: string;
  content: string;
}

export interface GistResponse {
  id: string;
  description: string;
  files: Record<string, { filename: string; content: string }>;
  html_url: string;
}
