// ESLint, flat config. `npm run lint` fails on any warning, so nothing here is
// advisory: a rule is either on and enforced, or it is not configured.
import js from '@eslint/js';
import globals from 'globals';
import tseslint from 'typescript-eslint';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';

export default tseslint.config(
  {
    ignores: ['dist', 'artifacts', 'node_modules', 'coverage', 'playwright-report', 'test-results', 'public/data'],
  },
  {
    // Application source.
    files: ['src/**/*.{ts,tsx}'],
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
    languageOptions: {
      ecmaVersion: 2023,
      globals: { ...globals.browser, ...globals.serviceworker },
      parserOptions: { project: ['./tsconfig.app.json'], tsconfigRootDir: import.meta.dirname },
    },
    plugins: { 'react-hooks': reactHooks, 'react-refresh': reactRefresh },
    rules: {
      ...reactHooks.configs.recommended.rules,

      /*
       * Three rules from the React Compiler preview set are turned off
       * deliberately, each for a stated reason rather than to make the build
       * quiet.
       *
       * `react-hooks/refs` flags `ref={...}` in JSX and any read of a ref inside
       * an effect's cleanup, both of which are the documented way to use refs.
       * The genuine violations it found, a ref written during render in the
       * announcer, in useAsync and in the results route, have been fixed; what
       * remains is the rule misreading correct code.
       *
       * `react-hooks/set-state-in-effect` cannot see through an async function,
       * so every "load from IndexedDB on mount" effect trips it even though the
       * state is set after an await, which is the case the rule's own
       * documentation permits. Subscribing to an external system is exactly what
       * those effects do.
       *
       * `react-refresh/only-export-components` is a hot-reload convenience, not a
       * correctness rule, and this project deliberately exports a provider and
       * its hook from the same module.
       */
      'react-hooks/refs': 'off',
      'react-hooks/set-state-in-effect': 'off',
      'react-refresh/only-export-components': 'off',

      // The app must never build a DOM subtree from a string. Ticket files and
      // operator-supplied names both flow through this code.
      'no-restricted-properties': [
        'error',
        { object: 'element', property: 'innerHTML', message: 'Use textContent or React children, never innerHTML.' },
      ],
      'react/no-danger': 'off',
      'no-restricted-syntax': [
        'error',
        {
          selector: 'JSXAttribute[name.name="dangerouslySetInnerHTML"]',
          message: 'dangerouslySetInnerHTML is not permitted anywhere in this app.',
        },
        {
          selector: 'MemberExpression[property.name="innerHTML"]',
          message: 'innerHTML is not permitted anywhere in this app.',
        },
      ],

      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],
      '@typescript-eslint/no-explicit-any': 'error',
      '@typescript-eslint/consistent-type-imports': ['error', { prefer: 'type-imports', fixStyle: 'inline-type-imports' }],
      'no-console': ['error', { allow: ['error', 'warn'] }],
      eqeqeq: ['error', 'always', { null: 'ignore' }],
      'prefer-const': 'error',
      'no-var': 'error',
    },
  },
  {
    // Tests may reach for a few things the app may not.
    files: ['src/**/*.test.{ts,tsx}', 'src/test/**/*.{ts,tsx}'],
    rules: {
      'no-console': 'off',
      '@typescript-eslint/no-non-null-assertion': 'off',
    },
  },
  {
    // End-to-end suite and build scripts run in Node.
    files: ['e2e/**/*.ts', '*.config.ts', 'scripts/**/*.mjs'],
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
    languageOptions: {
      ecmaVersion: 2023,
      globals: { ...globals.node },
    },
    rules: {
      'no-console': 'off',
      '@typescript-eslint/no-explicit-any': 'error',
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
    },
  },
);
