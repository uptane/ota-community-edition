import state from '../../../../../src/store/fleets/state';

describe('Fleets Store State', () => {
  it('should have fleet list as empty array for default value ', () => {
    expect(state.fleets).to.deep.eq([]);
  });
  it('should have preparedFleets as empty object for default value ', () => {
    expect(state.preparedFleets).to.deep.eq({});
  });
  it('should have selectedFleet as null for default value ', () => {
    expect(state.selectedFleet).toBe(null);
  });
});
