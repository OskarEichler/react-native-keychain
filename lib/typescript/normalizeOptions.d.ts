import type { AuthenticationPrompt, GetOptions, SetOptions } from './types';
export declare const AUTH_PROMPT_DEFAULTS: AuthenticationPrompt;
export declare function normalizeAuthPrompt<T extends SetOptions | GetOptions>(options?: T): T & {
    authenticationPrompt: {
        title: string | undefined;
        cancel: string | undefined;
        subtitle?: string;
        description?: string;
    };
};
//# sourceMappingURL=normalizeOptions.d.ts.map