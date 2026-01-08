import state from '../../../../../src/store/fleets/state';
import * as mutations from '../../../../../src/store/fleets/mutations';
import { camelCase } from 'change-case';

describe('Fleets Store Mutations', () => {
  const skip = ['visibleColumns', 'columns'];
  Object.keys(state).forEach((k) => {
    if (skip.includes(k)) {
      return;
    }
    it(`should set the value of ${k} to passed value`, () => {
      const expected = { test: true };
      mutations[camelCase('set_' + k)](state, expected);
      expect(state[k]).to.deep.eq(expected);
    });
  });
});
