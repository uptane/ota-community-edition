import state from '../../../../../src/store/devices/state';

describe('Devices Store State', () => {
  it('should have device list as empty array for default value ', () => {
    expect(state.devicesData).to.deep.eq([]);
  });
  it('should have selectedDevice as null for default value ', () => {
    expect(state.selectedDevice).to.deep.eq({});
  });
});
