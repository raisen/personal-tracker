export function createModal(title: string): {
  dialog: HTMLDialogElement;
  body: HTMLDivElement;
  actions: HTMLDivElement;
  show: () => void;
  close: () => void;
} {
  const dialog = document.createElement('dialog');

  const heading = document.createElement('h2');
  heading.textContent = title;

  const body = document.createElement('div');
  body.className = 'modal-body';

  const actions = document.createElement('div');
  actions.className = 'dialog-actions';

  dialog.appendChild(heading);
  dialog.appendChild(body);
  dialog.appendChild(actions);

  dialog.addEventListener('click', (e) => {
    if (e.target === dialog) dialog.close();
  });

  dialog.addEventListener('close', () => {
    dialog.remove();
  });

  return {
    dialog,
    body,
    actions,
    show: () => {
      document.body.appendChild(dialog);
      dialog.showModal();
    },
    close: () => dialog.close(),
  };
}
