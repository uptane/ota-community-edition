import state from '../../../../../src/store/devices/state';
import * as mutations from '../../../../../src/store/devices/mutations';
import { camelCase } from 'change-case';

describe('Devices Store Mutations', () => {
  const skipped = ['device', 'uptime', 'columns', 'updateInstallationHistory', 'updateInstallationEvents'];
  Object.keys(state).forEach((k) => {
    if (skipped.indexOf(k) === -1) {
      it(`should set the value of ${k} to passed value`, () => {
        const expected = { test: true };
        mutations[camelCase('set_' + k)](state, expected);
        expect(state[k]).to.deep.eq(expected);
      });
    }
  });
});
