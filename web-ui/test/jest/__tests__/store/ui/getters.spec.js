import state from '../../../../../src/store/ui/state';
import * as getters from '../../../../../src/store/ui/getters';
jest.mock('../../../../../src/utils/local-storage');
import quasar from 'quasar';
// quasar.LocalStorage.getItem.mockImplementation(() =>{});
//     quasar.LocalStorage.set.mockImplementation(() =>{});

describe('UI Store Getters', () => {
  beforeEach(() => {
    // quasar.mockClear();
  });
  const skipped = ['activeXmas', 'supportLevelMap'];

  Object.keys(state).forEach((k) => {
    if (skipped.indexOf(k) === -1) {
      it(`should return the value of ${k}`, () => {
        expect(getters[k](state)).toBe(state[k]);
      });
    }
  });
});
