import Vue from 'vue';

export function setIsDashboardPage(state, val) {
  Vue.set(state, 'isDashboardPage', val);
}
export function setIsDarkTheme(state, val) {
  Vue.set(state, 'isDarkTheme', val);
}
export function setCurrentPageTitle(state, val) {
  Vue.set(state, 'currentPageTitle', val);
}
export function setFleetInProcess(state, val) {
  Vue.set(state, 'fleetInProcess', val);
}
export function setLoadingFleets(state, val) {
  Vue.set(state, 'loadingFleets', val);
}
export function setLoadingDevices(state, val) {
  Vue.set(state, 'loadingDevices', val);
}
export function setLoadingPackages(state, val) {
  Vue.set(state, 'loadingPackages', val);
}
export function setLoadingUpdates(state, val) {
  Vue.set(state, 'loadingUpdates', val);
}
export function setDeviceInProcess(state, val) {
  Vue.set(state, 'deviceInProcess', val);
}
export function setDeviceDeleteInProgress(state, val) {
  Vue.set(state, 'deviceDeleteInProgress', val);
}
export function setFleetDeleteInProgress(state, val) {
  Vue.set(state, 'fleetDeleteInProgress', val);
}
export function setDevicesWithUpdateInProgress(state, data) {
  localStorage.set('devicesWithUpdateInProgress', data);
  Vue.set(state, 'devicesWithUpdateInProgress', data);
}
export function setIsLeftDrawerOpen(state, data) {
  Vue.set(state, 'isLeftDrawerOpen', data);
}
export function setIsFirstTimer(state, data) {
  Vue.set(state, 'isFirstTimer', data);
}
export function setIsMiniState(state, data) {
  Vue.set(state, 'isMiniState', data);
}
export function setIsMiniBar(state, data) {
  Vue.set(state, 'isMiniBar', data);
}
export function setUser(state, data) {
  Vue.set(state, 'user', data);
}
export function setUserDeviceProvisionData(state, data) {
  if (state.user) {
    Vue.set(state.user, 'deviceProvisionData', data);
  }
}
export function setDeviceWithUpdateInProgress(state, data) {}
export function removeDeviceWithUpdateInProgress(state, uuid) {
  const devicesWithUpdateInProgress = localStorage.getItem('devicesWithUpdateInProgress') || {};
  delete devicesWithUpdateInProgress[uuid];
  setDevicesWithUpdateInProgress(state, devicesWithUpdateInProgress);
}

export function setUserSettings(state, data) {
  let settings = data;
  if (typeof data === 'string') {
    settings = JSON.parse(data);
  }
  Vue.set(state, 'userSettings', settings);
}
export function setSingleUserSetting(state, data) {
  if (data.key) {
    Vue.set(state.userSettings, data.key, data.value);
  }
}
export function setSurveyShown(state, data) {
  Vue.set(state, 'surveyShown', data);
}
export function setActiveXmas(state, value) {
  setSingleUserSetting(state, { key: OPTION_MAP.activateXmas, value: value });
}
export function setAllowedUsersForRoutes(state, data) {
  Vue.set(state, 'allowedUsersForRoutes', data);
}
export function setAdminMode(state, data) {
  Vue.set(state, 'adminMode', data);
}
export function setLaunchedAt(state, data) {
  Vue.set(state, 'launchedAt', data);
}
export function setJustConfirmedEmail(state, data) {
  Vue.set(state, 'justConfirmedEmail', data);
}
export function setAlmostLoaded(state, data) {
  Vue.set(state, 'almostLoaded', data);
}
export function setLoaded(state, data) {
  Vue.set(state, 'loaded', data);
}
export function setUiLoaderText(state, data) {
  Vue.set(state, 'uiLoaderText', data);
}
export function setReleaseNotes(state, data) {
  Vue.set(state, 'releaseNotes', data);
}
export function setWideDrawer(state, data) {
  Vue.set(state, 'wideDrawer', data);
}
