import Vue from 'vue';

export function setDevicesData(state, devices) {
  Vue.set(state, 'devicesData', devices);
}
export function updateSingleDevice(state, device) {
  const existingDeviceIndex = state.devices.findIndex((x) => x.uuid === device.uuid);
  const existingDevice = state.devices[existingDeviceIndex];
  const newDevice = { ...existingDevice, ...device };
  state.devices.splice(existingDeviceIndex, 1, newDevice);
  Vue.set(state, 'devices', [...state.devices]);
}
export function addNewDevice(state, device) {
  state.devices.push(device);
}
export function setDevices(state, val) {
  Vue.set(state, 'devices', val);
}
export function setDevice(state, val) {
  Vue.set(state, 'device', val);
}
export function setSelectedDevice(state, val) {
  Vue.set(state, 'selectedDevice', { ...val });
}
export function setVisibleColumns(state, val) {
  Vue.set(state, 'visibleColumns', val);
}
export function setDeviceListRefreshRate(state, val) {
  Vue.set(state, 'deviceListRefreshRate', val);
}
export function setDeviceRefreshRate(state, val) {
  Vue.set(state, 'deviceRefreshRate', val);
}
export function setTimeoutId(state, val) {
  Vue.set(state, 'timeoutId', val);
}
