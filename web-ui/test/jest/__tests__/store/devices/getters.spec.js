import state from '../../../../../src/store/devices/state';
import * as getters from '../../../../../src/store/devices/getters';

describe('Devices Store Getters', () => {
  const skipped = ['device', 'devicesData', 'devices'];
  Object.keys(state).forEach((k) => {
    if (skipped.indexOf(k) === -1) {
      it(`should return the value of ${k}`, () => {
        expect(getters[k](state)).toBe(state[k]);
      });
    }
  });
});
