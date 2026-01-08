import Vue from 'vue';

export function isDashboardPage(state) {
  return state.isDashboardPage;
}
export function isFirstTimer(state) {
  return state.isFirstTimer;
}
export function isDarkTheme(state) {
  return state.isDarkTheme;
}
export function currentPageTitle(state) {
  return state.currentPageTitle;
}
export function fleetInProcess(state) {
  return state.fleetInProcess || {};
}
export function loadingFleets(state) {
  return state.loadingFleets;
}
export function loadingDevices(state) {
  return state.loadingDevices;
}
export function loadingPackages(state) {
  return state.loadingPackages;
}
export function loadingUpdates(state) {
  return state.loadingUpdates;
}

export function deviceInProcess(state) {
  return state.deviceInProcess;
}
export function deviceDeleteInProgress(state) {
  return state.deviceDeleteInProgress;
}
export function fleetDeleteInProgress(state) {
  return state.fleetDeleteInProgress;
}
export function devicesWithUpdateInProgress(state) {
  return state.devicesWithUpdateInProgress;
}
export function isLeftDrawerOpen(state) {
  return state.isLeftDrawerOpen;
}
export function isMiniState(state) {
  return state.isMiniState;
}
export function wideDrawer(state) {
  return state.wideDrawer;
}
export function isMiniBar(state) {
  return state.isMiniBar;
}
export function user(state) {
  return state.user || {};
}
export function userSettings(state) {
  return state.userSettings || {};
}
export function surveyShown(state) {
  return state.surveyShown;
}
export function activeXmas(state) {
  return userSettings(state)[OPTION_MAP.activateXmas];
}
export function allowedUsersForRoutes(state) {
  return state.allowedUsersForRoutes;
}
export function isXmas(state) {
  return Date.now() < new Date('01/02/2020').getTime();
}
export function adminMode(state) {
  return state.adminMode;
}
export function uptime(state) {
  return Date.now() - state.launchedAt;
}
export function launchedAt(state) {
  return state.launchedAt;
}
export function justConfirmedEmail(state) {
  return state.justConfirmedEmail;
}
export function loaded(state) {
  return state.loaded;
}
export function uiLoaderText(state) {
  return state.uiLoaderText;
}
export function almostLoaded(state) {
  return state.almostLoaded;
}
export function releaseNotes(state) {
  return state.releaseNotes;
}
