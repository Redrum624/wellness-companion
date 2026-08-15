module.exports = {
  preset: 'ts-jest',
  testEnvironment: 'node',
  roots: ['<rootDir>/test'],
  moduleNameMapper: { '^@shared/(.*)$': '<rootDir>/../shared/$1' },
  // tsconfig.test.json (not the root solution-style tsconfig.json) so JSON
  // imports (resolveJsonModule) and Node core-module types resolve for tests.
  transform: {
    '^.+\\.tsx?$': ['ts-jest', { tsconfig: '<rootDir>/tsconfig.test.json' }]
  }
}
