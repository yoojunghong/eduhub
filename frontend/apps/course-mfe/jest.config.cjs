/** @type {import('jest').Config} */
module.exports = {
  testEnvironment: 'jsdom',
  roots: ['<rootDir>/src'],
  transform: {
    '^.+\\.tsx?$': ['ts-jest', { tsconfig: { jsx: 'react-jsx', esModuleInterop: true, isolatedModules: true } }],
  },
  setupFilesAfterEnv: ['<rootDir>/src/setupTests.ts'],
};
