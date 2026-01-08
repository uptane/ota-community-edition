import state from '../../../../../src/store/ui/state';

describe('UI Store State', () => {
  it('should have isDashboardPage as false for default value ', () => {
    expect(state.isDashboardPage).toBe(false);
  });
  it('should have isDarkTheme as true for default value ', () => {
    expect(state.isDarkTheme).toBe(true);
  });
  it('should have currentPageTitle as null for default value ', () => {
    expect(state.currentPageTitle).toBe(null);
  });
  it('should have fleetInProcess as {} for default value ', () => {
    expect(state.fleetInProcess).to.deep.eq({});
  });
  it('should have loadingFleets as false for default value ', () => {
    expect(state.loadingFleets).toBe(false);
  });
  it('should have loadingDevices as false for default value ', () => {
    expect(state.loadingDevices).toBe(false);
  });
  it('should have loadingPackages as false for default value ', () => {
    expect(state.loadingPackages).toBe(false);
  });
  it('should have loadingUpdates as false for default value ', () => {
    expect(state.loadingUpdates).toBe(false);
  });
  it('should have deviceInProcess as null for default value ', () => {
    expect(state.deviceInProcess).toBe(null);
  });
  it('should have deviceDeleteInProgress as null for default value ', () => {
    expect(state.deviceDeleteInProgress).toBe(null);
  });
  it('should have fleetDeleteInProgress as null for default value ', () => {
    expect(state.fleetDeleteInProgress).toBe(null);
  });
  it('should have devicesWithUpdateInProgress as {} for default value ', () => {
    expect(state.devicesWithUpdateInProgress).to.deep.eq({});
  });
  it('should have isLeftDrawerOpen as false for default value ', () => {
    expect(state.isLeftDrawerOpen).toBe(true);
  });
});
