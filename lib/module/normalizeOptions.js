"use strict";

// Default authentication prompt options
export const AUTH_PROMPT_DEFAULTS = {
  title: 'Authenticate to retrieve secret',
  cancel: 'Cancel'
};
export function normalizeAuthPrompt(
// eslint-disable-next-line @typescript-eslint/no-explicit-any
options = {}) {
  const {
    title = AUTH_PROMPT_DEFAULTS.title,
    cancel = AUTH_PROMPT_DEFAULTS.cancel
  } = options.authenticationPrompt ?? {};
  return {
    ...options,
    authenticationPrompt: {
      ...options.authenticationPrompt,
      title,
      cancel
    }
  };
}
//# sourceMappingURL=normalizeOptions.js.map