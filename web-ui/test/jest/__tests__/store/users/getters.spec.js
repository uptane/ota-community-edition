import state from '../../../../../src/store/users/state';
import * as getters from '../../../../../src/store/users/getters';

describe('Users Store Getters', () => {
  Object.keys(state).forEach((k) => {
    it(`should return the value of ${k}`, () => {
      expect(getters[k](state)).toBe(state[k]);
    });
  });
});
