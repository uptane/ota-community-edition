import Vue from 'vue';
import { localStorage } from '../../utils/local-storage';
import { OPTION_MAP } from '../../config/user_options';
import { Screen } from 'quasar';

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
  // __q_objt|{"02f91153-ccc2-40f8-b3d1-60ce7da600ce":{"uuid":"02f91153-ccc2-40f8-b3d1-60ce7da600ce","deviceName":"bens-desk-apalis-imx6-02","deviceId":"test-device-id-02f91153", "updateStartedAt": 1561742594677}}
  Vue.set(state, 'devicesWithUpdateInProgress', localStorage.getItem('devicesWithUpdateInProgress') || {});
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
export function uiLoaderType(state) {
  return state.uiLoaderType;
}
export function almostLoaded(state) {
  return state.almostLoaded;
}
export function releaseNotes(state) {
  return state.releaseNotes;
}
export function releaseNotesMd(state) {
  return state.releaseNotesMd;
}
export function tabs(state) {
  return state.tabs;
}
export function currentTab(state) {
  return state.currentTab;
}
export function wsStatus(state) {
  return state.wsStatus;
}
export function showOnboardingWalkthrough(state) {
  return state.showOnboardingWalkthrough;
}

export function parentDivSize(state) {
  return state.parentDivSize;
}
export function leftMenuSize(state) {
  return state.leftMenuSize;
}
export function rightMenuSize(state) {
  return state.rightMenuSize;
}
export function devicesDivSize(state) {
  return state.devicesDivSize;
}
export function deviceQuickViewHeaderWidth(state) {
  return state.parentDivSize.width - state.leftMenuSize.width - state.rightMenuSize.width - state.devicesDivSize.width;
}
export function deviceQuickViewHeaderLeft(state) {
  return state.parentDivSize.width - state.leftMenuSize.width - state.rightMenuSize.width;
}
export function deviceQuickViewWidth(state) {
  return state.parentDivSize.width - state.leftMenuSize.width - state.rightMenuSize.width - state.devicesDivSize.width - 20;
}
export function deviceQuickViewLeft(state) {
  return state.leftMenuSize.width + state.devicesDivSize.width - 0;
}
export function appSubHeaderWidth(state) {
  return Screen.gt.sm ? state.parentDivSize.width - state.leftMenuSize.width - state.rightMenuSize.width : state.parentDivSize.width - state.rightMenuSize.width;
}
export function appSubHeaderLeft(state) {
  return Screen.gt.sm ? state.leftMenuSize.width : 0;
}
export function appSubHeaderPaddingRight(state) {
  return state.leftMenuSize.width;
}
export function currentPageDimensions(state) {
  return state.currentPageDimensions;
}
