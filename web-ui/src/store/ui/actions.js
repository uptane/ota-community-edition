/* */
import WebsocketHandler from '../../services/ws.service';
import { API_USER_GET_METADATA, API_APP_RELEASE_NOTES_MD } from '../../config';
import ApiService from '../../services/api.service';
import { Dark } from 'quasar';
import { WsUrl } from '../../boot/access-manager';
import { AuthService } from '../../services/auth.service';

const allowedRetries = 2;
let retries = 0;

export async function initializeUI({ commit, dispatch, state }) {
  commit('ui/setUiLoaderText', 'Initializing components', { root: true });
  try {
    await dispatch('ui/fetchReleaseNotesMd', {}, { root: true });
  } catch (e) {
    logError('Unable to fetch release notes markdown content', e);
  }
  try {
    await dispatch('users/getAvatar', {}, { root: true });
  } catch (e) {}
  commit('ui/setLoaded', true, { root: true });
}
export function connectWebSocket(context) {
  const { commit } = context;
  return new Promise((resolve, reject) => {
    WebsocketHandler.init(context, WsUrl, true);
    WebsocketHandler.onAuthFailure = (ctx, data) => {
      commit('ui/incrementWsFailureCount', 1, { root: true });
      AuthService.refreshResolvedSession()
        .then(() => {
          WebsocketHandler.connect();
        })
        .catch(() => {
          commit('ui/setWsConnected', false, { root: true });
        });
    };
    WebsocketHandler.tokenFactory = (ctx, data) => {
      return AuthService.getResolvedAccessToken();
    };
    WebsocketHandler.onAuthSuccess = (ctx, data) => {
      commit('ui/setWsFailureCount', 0, { root: true });
      commit('ui/setWsConnected', true, { root: true });
    };
    WebsocketHandler.onDisconnect = (ctx, data) => {
      commit('ui/incrementWsFailureCount', 1, { root: true });
    };
    WebsocketHandler.onError = (ctx, data) => {
      commit('ui/incrementWsFailureCount', 1, { root: true });
    };
    resolve();
  });
}
export function requestWsData(context, { type, deviceUuid }) {
  return new Promise((resolve, reject) => {
    WebsocketHandler.requestData(type, deviceUuid);
    resolve();
  });
}
var timerId = 0;
let initialFetch = true;
export function fetchUserSettings({ dispatch, commit, state, rootGetters }, {}) {
  return new Promise((resolve, reject) => {
    const userId = rootGetters['users/userData'].user_id;
    if (!userId) {
      if (retries < allowedRetries) {
        retries++;
        timerId = setTimeout(() => {
          dispatch('ui/fetchUserSettings', {}, { root: true }).finally(resolve);
        }, 2000);
        return;
      } else {
        clearTimeout(timerId);
        resolve({});
        return;
      }
    }
    ApiService.getResource(API_USER_GET_METADATA.replace('{userID}', userId), {
      useHostAccessToken: true,
    })
      .then((data) => {
        if (data.metadata && data.metadata['darkTheme'] !== undefined) {
          Dark.set(data.metadata['darkTheme']);
        }

        commit('ui/setUserSettings', { ...state.userSettings, ...data.metadata }, { root: true });
        resolve(data);
      })
      .catch((err) => {
        logError('Unable to fetch settings:', err);
        reject(err);
      });
  });
}
export function fetchReleaseNotesMd({ dispatch, commit, state, rootGetters }) {
  return new Promise((resolve, reject) => {
    ApiService.getResource(API_APP_RELEASE_NOTES_MD, { bypassAuthIntercept: true })
      .then((data) => {
        commit('ui/setReleaseNotesMd', data, { root: true });
        resolve(data);
      })
      .catch((err) => {
        logError('Unable to fetch application release notes:', err);
        reject(err);
      });
  });
}

export function saveUserSettings({ dispatch, commit, state, rootGetters }, data) {
  const userId = rootGetters['users/userData'].user_id;
  return new Promise((resolve, reject) => {
    commit('ui/setUserSettings', { ...state.userSettings, ...data }, { root: true });
    dispatch('users/saveMetadata', { updates: { ...data } }, { root: true })
      .then((update) => {
        resolve(update);
      })
      .catch((err) => {
        logError('Unable to save settings:', err);
        reject(err);
      });
  });
}
export function fetchHtmlTemplate(context, data) {
  return new Promise((resolve, reject) => {
    ApiService.getResource(data.url)
      .then((temp) => {
        resolve(temp);
      })
      .catch((err) => {
        logError('Unable to get template:', err);
        reject(err);
      });
  });
}

let savedUserSettingsTimer = null;
let userSettingsToSave = {};
export function setUserOption({ dispatch, state, commit }, data) {
  commit('ui/setUserSettings', { ...state.userSettings, ...data }, { root: true });
  userSettingsToSave = { ...userSettingsToSave, ...data };
  if (savedUserSettingsTimer) {
    clearTimeout(savedUserSettingsTimer);
  }
  savedUserSettingsTimer = setTimeout(() => {
    dispatch('ui/saveUserSettings', userSettingsToSave, { root: true });
    savedUserSettingsTimer = null;
    userSettingsToSave = {};
  }, 5000);
}
