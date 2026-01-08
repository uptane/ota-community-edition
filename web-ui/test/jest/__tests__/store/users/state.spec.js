import state from '../../../../../src/store/users/state';

describe('Users Store State', () => {
  it('should have userData with empty object as default value ', () => {
    expect(state.userData).to.deep.eq({});
  });
});
