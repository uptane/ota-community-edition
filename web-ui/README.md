[![pipeline status](http://gitlab.toradex.int/rd/innovation/ota-frontend/badges/master/pipeline.svg)](http://gitlab.toradex.int/rd/innovation/ota-frontend/commits/master)
[![coverage report](http://gitlab.toradex.int/rd/innovation/ota-frontend/badges/master/coverage.svg)](http://gitlab.toradex.int/rd/innovation/ota-frontend/commits/master)

# Torizon OTA

## Getting Started

1. Install Node JS version 14
2. Decide what package manager to use.
   You can use either npm or yarn (yarn is recomended)
3. Install quasar framework
   1. Visit Quasar developer documentation: [https://v1.quasar.dev](https://v1.quasar.dev)
   2. Install Quasar CLI
   3. Install useful vue extensions for VS Code or Sublime
   - VS Code is recommened with Vetur extension. you can also read more about it [here](https://v1.quasar.dev/start/vs-code-configuration#install-vs-code-extensions-for-standard)
4. Run `yarn install` or `npm install` in the root directory where `package.json` is located.
5. Start the dev server with hot reload by executing one of the bash files in ./dev-scripts directory.

   - `./dev-scripts/dev.sh` (recomended) will start the server in dev environment. i.e all backend calls will be routed to the dev environment
   - `./dev-scripts/pilot.sh` will start the server in pilot environment.
   - `./dev-scripts/prod.sh` (not recomended) will start the server prod environment.

6. The dev server is configured to run as https. You may need to add trusted localhost certs to your browser. (I use mkcert for mac, installed through homebrew). This https option can also be disabled in `quasar.conf.js` in `devServer` section by setting `https` to false.
7. Refer to [Quasar Documentation](https://v1.quasar.dev) for guide on how to use the framework.

## API

The API endpoints are available for test in Postman

[![Run in Postman](https://run.pstmn.io/button.svg)](https://app.getpostman.com/run-collection/2db3b4ec8d00ba2cd2b8#?env%5BToradex%20OTA%20int%5D=W3sia2V5IjoiQVBJX0JBU0VfVVJMIiwidmFsdWUiOiJodHRwOi8vYXBpLnRvcmFkZXguaW50IiwiZGVzY3JpcHRpb24iOiIiLCJlbmFibGVkIjp0cnVlfSx7ImtleSI6IkFQSV9CQVNFX1VSTF9JUCIsInZhbHVlIjoiaHR0cDovLzEwLjEyLjEuNzgiLCJkZXNjcmlwdGlvbiI6IiIsImVuYWJsZWQiOnRydWV9LHsia2V5IjoiQVBJX0JBU0VfVVJMX1BST1hJRUQiLCJ2YWx1ZSI6Imh0dHA6Ly9sb2NhbGhvc3Q6ODA4MCIsImRlc2NyaXB0aW9uIjoiIiwiZW5hYmxlZCI6dHJ1ZX1d)

## Tests

This application includes unit tests and e2e tests. The testing framework used is Jest for unit test and Cypress for E2E test.

As time goes on, there will be a run the tests using docker but for now you will have to clone the repository and install NodeJs dependencies with `npm install` or `yarn`. The run `npm run selenium:install` to install selenium deriver.

### Run Unit test:

1. Single run with snapshot update `npm run test:unit`
2. Run in watch mode `npm run test:unit:watch`
3. Generate coverage report `npm run test:unit:coverage`
4. View coverage report `npm run test:unit:coverage` you can view the report @ http://localhost:8788/

### Run E2E Test

1. Run using Cypress w/o dashboard `npm run test:e2e:CI`
2. Run using Cypress with dashboard `npm run test:e2e:CI:open`
3. Run using webdriver: run `npm run selenium:start` first then in a different terminal, run `npm run test:e2e`
4. Run as mobile app `npm run test:e2e:appium`
5. Run in electron mode `test:e2e:electron`
