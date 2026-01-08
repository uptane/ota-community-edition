import Vue from 'vue';
import { extend } from 'quasar';
import { EventBus } from 'src/event-bus';

export function setDevicesData(state, devices) {
  Vue.set(state, 'devicesData', devices);
}
export function updateSingleDevice(state, device) {
  // Partially update the device in the devices array if it exists with the new data
  let index = state.devices.findIndex((d) => d.uuid === device.uuid);
  if (index > -1) {
    let updated = extend(true, state.devices[index], device);
    state.devices.splice(index, 1, updated);
  }

  // Emit a global event to notify other components that the device has been updated
  EventBus.$emit('devices:partial-update', { deviceUuid: device.uuid, partialUpdateData: device });
}
export function addNewDevice(state, device) {
  state.devices.push(device);
}
export function setDevices(state, val) {
  Vue.set(state, 'devices', val);
}
export function setOffset(state, val) {
  Vue.set(state, 'offset', val);
}
export function setLimit(state, val) {
  Vue.set(state, 'limit', val);
}
export function setTotal(state, val) {
  Vue.set(state, 'total', val);
}
export function setSort(state, val) {
  Vue.set(state, 'sort', val);
}
export function setDevice(state, val) {
  // Vue.set(state, 'device', val);
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

export function setUpdateInstallationEventsForDevice(state, { uuid, events }) {
  const sortedEvents = _.sortBy(events, 'receivedAt').reverse();
  Vue.set(state.updateInstallationEvents, uuid, sortedEvents);
}
export function clearUpdateInstallationEventsForDevice(state, { uuid }) {
  Vue.set(state.updateInstallationEvents, uuid, []);
}
export function addUpdateInstallationEventForDevice(state, { uuid, event }) {
  const existing = state.updateInstallationEvents[uuid] || [];
  existing.push(event);
  setUpdateInstallationEventsForDevice(state, { uuid, events: existing });
}
export function setUpdateInstallationHistoryForDevice(state, { uuid, history }) {
  Vue.set(state.updateInstallationHistory, uuid, history);
}
