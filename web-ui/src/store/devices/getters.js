export function devicesData(state, getters) {
  return getters.devices;
}
export function devices(state, getters) {
  let data = _.uniqBy(state.devices, (device) => device.uuid + device.deviceId + device.deviceName);
  return data;
}
export function offset(state) {
  return state.offset;
}
export function limit(state) {
  return state.limit;
}
export function total(state) {
  return state.total;
}
export function sort(state) {
  return state.sort;
}
export function sortField(state, getters) {
  let columns = getters['columns'];
  let sort = state.sort || { name: 'asc' };
  // Find the column that is being sorted by
  let column = columns.find((f) => f.name === Object.keys(sort)[0] || f.id === Object.keys(sort)[0] || f.field === Object.keys(sort)[0]) || columns.find((f) => f.id === 'name');
  return { ...column, descending: Object.keys(sort)[0] === 'desc' ? true : false };
}

export function devicesByUuid(state) {
  return _.keyBy(state.devices, 'uuid');
}
export function device(state) {
  return state.device;
}
export function selectedDevice(state) {
  return state.selectedDevice;
}

export function visibleColumns(state) {
  return state.visibleColumns;
}
export function columns(state) {
  return state.columns;
}
export function deviceListRefreshRate(state) {
  return state.deviceListRefreshRate;
}
export function deviceRefreshRate(state) {
  return state.deviceRefreshRate;
}
export function timeoutId(state) {
  return state.timeoutId;
}
export function updateInstallationEvents(state) {
  return state.updateInstallationEvents;
}
export function updateInstallationHistory(state) {
  return state.updateInstallationHistory;
}
export function deviceMetrics(state) {
  return state.deviceMetrics;
}
