"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.AUTH_PROMPT_DEFAULTS = void 0;
exports.normalizeAuthPrompt = normalizeAuthPrompt;
// Default authentication prompt options
const AUTH_PROMPT_DEFAULTS = exports.AUTH_PROMPT_DEFAULTS = {
  title: 'Authenticate to retrieve secret',
  cancel: 'Cancel'
};
function normalizeAuthPrompt(
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