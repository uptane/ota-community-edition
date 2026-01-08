import state from '../../../../../src/store/fleets/state';
import * as getters from '../../../../../src/store/fleets/getters';

describe('Fleets Store Getters', () => {
  Object.keys(state).forEach((k) => {
    it(`should return the value of ${k}`, () => {
      expect(getters[k](state)).toBe(state[k]);
    });
  });
});
