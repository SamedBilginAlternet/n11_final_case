module.exports = {
  root: true,
  env: { browser: true, es2022: true, node: true },
  extends: [
    'eslint:recommended',
    'plugin:react/recommended',
    'plugin:react/jsx-runtime',
    'plugin:react-hooks/recommended',
  ],
  parserOptions: { ecmaVersion: 'latest', sourceType: 'module' },
  settings: { react: { version: '18.2' } },
  ignorePatterns: ['dist', 'node_modules'],
  rules: {
    'react/prop-types': 'off',
  },
  overrides: [
    {
      files: ['**/*.test.{js,jsx}', 'src/test/**'],
      env: { jest: true },
    },
  ],
};
