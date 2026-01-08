import state from '../../../../../src/store/users/state';
import * as mutations from '../../../../../src/store/users/mutations';
import { camelCase } from 'change-case';

describe('Users Store Mutations', () => {
  const skipped = [];
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
