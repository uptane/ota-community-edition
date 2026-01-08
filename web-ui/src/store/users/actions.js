/* */
import ApiService from 'src/services/api.service';
import { exportFile } from 'quasar';

import {
  API_USER_FETCH,
  API_USER_CREATE,
  API_USER_UPDATE_METADATA,
  API_USER_GET_CREDENTIALS_ZIP,
  API_ACCOUNT_ACTIVATE_COMMERCIAL_ACCESS_FREE_TRIAL,
  API_LEAD_CREATE,
  API_ACCOUNT_CREATE_API_CLIENT,
  API_ACCOUNT_GET_API_CLIENTS,
  API_ACCOUNT_DELETE_API_CLIENT,
  API_ACCOUNT_UPDATE_API_CLIENT,
  API_USER_KEYS_PROVISION_ROTATE,
  API_USER_REPO_ROTATE,
  API_USER_KEYS_GARAGE_TOOLS_ROTATE,
} from '../../config';
import { AuthService } from '../../services/auth.service';
import { get } from 'underscore';
import { OptionsService } from '../../services/options.service';

// This is needed during development to test user flow
export function mockFetchUser({ dispatch, commit }) {
  // return fetchUser({ dispatch, commit })
  return new Promise((resolve, reject) => {
    commit('ui/setUiLoaderType', 'text', { root: true });
    commit('ui/setUiLoaderText', 'Creating your namespace (may take up to 30 seconds)', { root: true });
    setTimeout(() => {
      commit('ui/setUiLoaderText', 'Checking server for your config and namespace', { root: true });
      setTimeout(() => {
        resolve({});
      }, 3 * 1000);
    }, 10 * 1000);
  });
}
export function fetchUser({ dispatch, commit }) {
  return new Promise((resolve, reject) => {
    // Before we begin, let's update UI loader text
    commit('ui/setUiLoaderText', 'Checking server for your config and namespace', { root: true });

    // Save user data to the store
    const saveUserData = async (data) => {
      const userData = data || {};
      userData.user_id = userData.user_id || userData.UserID;
      commit('users/setUserData', userData, { root: true });
      try {
        await OptionsService.init();
      } catch (e) {
        // We don't want to fail the user fetch if the options service fails
      }
      return userData;
    };

    // Let's attempt to fetch tdx user data from the server
    ApiService.getResource(API_USER_FETCH, {
      useHostAccessToken: true,
    })
      .then((data) => {
        // User data was fetched successfully, let's save it
        saveUserData(data).finally(() => {
          resolve(data);
        });
      })
      .catch((err) => {
        // On err we assume the user does not exist, let's create a new user
        dispatch('users/createUser', {}, { root: true })
          .then((data) => {
            // User creation was successful, let's save the user data
            saveUserData(data).finally(() => {
              resolve(data);
            });
          })
          .catch((userCreationError) => {
            console.log('User creation failed: ', userCreationError);
            // At this point we have failed to either fetch an existing user or create a new one, let's just give it up and return the error
            reject(userCreationError);
          });
      });
  });
}
export function createUser({ commit, state, dispatch, rootGetters }, options = {}) {
  commit('ui/setUiLoaderType', 'text', { root: true });
  commit('ui/setUiLoaderText', 'Creating your namespace (may take up to 30 seconds)', { root: true });
  return new Promise((resolve, reject) => {
    ApiService.postResource(API_USER_CREATE, {}, options.axiosOptions || ApiService.defaultOptions, options.rawResponse)
      .then(async (resp) => {
        if (options && options.rawResponse && resp.status === 401) {
          throw new Error('User creation failed');
        }
        // At this point we have successfully created a new user, let's try to refresh the session
        try {
          await AuthService.refreshSession(true);
        } catch (e) {}

        // Save user data to the store
        const created = options && options.rawResponse ? (resp || {}).data : resp;
        state.userData = created;
        // Create SugarCRM lead
        const userData = rootGetters['ui/user'];
        const email = userData.email;
        const campaign_id = process.env.SUGARCRM_NEW_USER_CAMPAIGN_ID;
        const lead = { lead_source: 'Torizon', description: '', email, campaign_id };
        dispatch('users/createLead', lead, { root: true })
          .catch(console.log)
          .finally(() => {
            resolve(resp);
          });
      })
      .catch((error) => {
        reject(error);
      });
  });
}

export function signIn(context, payload) {
  return AuthService.signIn(payload);
}
export function signUp(context, payload) {
  return AuthService.signUp(payload);
}
export function signOut(context, payload) {
  return AuthService.signOut(payload);
}
export function enable2FA(context, payload) {
  return AuthService.enable2FA(payload);
}
export function changePassword(context, payload) {
  return AuthService.changePassword(payload);
}
export function switchAccessType(context, payload) {
  return AuthService.switchAccessType(payload);
}
export function getUserInfo(context, payload) {
  return new Promise((resolve, reject) => {
    AuthService.getUserInfo()
      .then((user) => {
        resolve(user);
      })
      .catch(reject);
  });
}
export function getRoles({ commit }, payload) {
  return new Promise((resolve, reject) => {
    AuthService.getUserRoles()
      .then((roles) => {
        const { all, host, guest } = roles;
        resolve(roles);
      })
      .catch(reject);
  });
}

export function createLead(context, lead) {
  return new Promise((resolve, reject) => {
    ApiService.postResource(API_LEAD_CREATE, lead)
      .then(resolve)
      .catch(reject);
  });
}
export function createCommercialAccessLead({ dispatch }, { description, email }) {
  const campaign_id = process.env.SUGARCRM_COMMERCIAL_ACCESS_CAMPAIGN_ID || '8ed9512e-b9e9-11eb-b81d-065b9161e400'; // If campaign id is not set, use campaign id from test instance
  const lead = { lead_source: 'Torizon', campaign_id, description, email };
  return dispatch('users/createLead', lead, { root: true });
}
export function createOnboardingWalkthroughLead({ dispatch, getters, rootGetters }, { description }) {
  const campaign_id = process.env.SUGARCRM_ONBOARDING_WALKTHROUGH_CAMPAIGN_ID || '6a3ec7bc-fbd8-11ed-88b9-065b9161e400'; // If campaign id is not set, use campaign id from test instance
  const userData = rootGetters['ui/user'];
  const email = userData.email;
  const lead = { lead_source: 'Torizon', campaign_id, description, email };
  return dispatch('users/createLead', lead, { root: true });
}
export function saveMetadata({ dispatch, state }, params) {
  return new Promise((resolve, reject) => {
    ApiService.postResource(API_USER_UPDATE_METADATA.replace('{userID}', (state.userData || {}).user_id), params.updates, {
      useHostAccessToken: true,
    })
      .then((updated) => {
        resolve(updated);
      })
      .catch((error) => {
        reject(error);
      });
  });
}
export function getAvatar({ dispatch, commit, state, rootGetters }) {
  return new Promise((resolve, reject) => {
    const avatar = rootGetters['ui/userSettings']['avatar'] || '';
    commit('users/setUserData', { avatar }, { root: true });
    resolve(avatar);
  });
}
export function saveAvatar({ dispatch, state, rootGetters }, avatar) {
  return new Promise((resolve, reject) => {
    dispatch('users/saveMetadata', { updates: { avatar } }, { root: true })
      .then((saved) => {
        commit('ui/setUserSettings', { ...rootGetters['ui/userSettings'], avatar }, { root: true });
        resolve(saved);
      })
      .catch((error) => {
        reject(error);
      });
  });
}

export function dowloadCredentials({ dispatch, state }, params) {
  return new Promise((resolve, reject) => {
    ApiService.getResource(API_USER_GET_CREDENTIALS_ZIP.replace('{userID}', state.userData.user_id), { responseType: 'blob' })
      .then((data) => {
        exportFile('credentials.zip', data);
        resolve(data);
      })
      .catch((error) => {
        reject(error);
      });
  });
}
export function rotateCredentials({ dispatch, state }, params) {
  return new Promise((resolve, reject) => {
    const userRepoPromise = ApiService.putResource(API_USER_REPO_ROTATE);
    const provisionPromise = ApiService.putResource(API_USER_KEYS_PROVISION_ROTATE.replace('{userID}', state.userData.user_id));
    const garageToolsPromise = ApiService.putResource(API_USER_KEYS_GARAGE_TOOLS_ROTATE.replace('{userID}', state.userData.user_id));
    const resonseObject = {
      userRepo: null,
      provision: null,
      garageTools: null,
    };
    Promise.all([provisionPromise, garageToolsPromise])
      .then((data) => {
        resonseObject.provision = data[0];
        resonseObject.garageTools = data[1];
      })
      .catch((error) => {})
      .finally(() => {
        // Attempt to rotate user repo keys even if provision or garage tools failed
        userRepoPromise
          .then((repoData) => {
            resonseObject.userRepo = repoData;
            resolve(resonseObject);
          })
          .catch((err) => {
            reject(err);
          });
      });
  });
}

export function activateCommercialAccessTrial(context, payload) {
  return new Promise((resolve, reject) => {
    ApiService.putResource(API_ACCOUNT_ACTIVATE_COMMERCIAL_ACCESS_FREE_TRIAL, { tier: 'torizon-commercial-tier', duration_days: 90 }, { headers: { 'Content-Type': 'application/json' } })
      .then((data) => {
        AuthService.refreshSession().finally(() => {
          AuthService.getUser().finally((user) => {
            resolve(data);
          });
        });
      })
      .catch(reject);
  });
}

export function createApiClient({ dispatch }, clientData) {
  return new Promise((resolve, reject) => {
    ApiService.postResource(API_ACCOUNT_CREATE_API_CLIENT, clientData, { headers: { 'Content-Type': 'application/json' } })
      .then((data) => {
        dispatch('users/getApiClients', {}, { root: true });
        resolve(data);
      })
      .catch(reject);
  });
}
export function getApiClients({ commit }) {
  return new Promise((resolve, reject) => {
    ApiService.getResource(API_ACCOUNT_GET_API_CLIENTS, { headers: { 'Content-Type': 'application/json' } })
      .then((data) => {
        commit('users/setApiClients', data, { root: true });
        resolve(data);
      })
      .catch(reject);
  });
}
export function setDefaultApiClient({ dispatch }, { client_id }) {
  return new Promise((resolve, reject) => {
    ApiService.patchResource(API_ACCOUNT_UPDATE_API_CLIENT + '/' + client_id, { client_id, default: true }, { headers: { 'Content-Type': 'application/json' } })
      .then((data) => {
        dispatch('users/getApiClients', {}, { root: true });
        resolve(data);
      })
      .catch(reject);
  });
}
export function updateApiClient({ dispatch }, client) {
  const { client_id } = client;
  delete client.secret;
  return new Promise((resolve, reject) => {
    ApiService.patchResource(API_ACCOUNT_UPDATE_API_CLIENT + '/' + client_id, client, { headers: { 'Content-Type': 'application/json' } })
      .then((data) => {
        dispatch('users/getApiClients', {}, { root: true });
        resolve(data);
      })
      .catch(reject);
  });
}
export function deleteApiClient({ dispatch }, { client_id }) {
  return new Promise((resolve, reject) => {
    ApiService.deleteResource(API_ACCOUNT_DELETE_API_CLIENT + '/' + client_id, { headers: { 'Content-Type': 'application/json' } })
      .then((data) => {
        dispatch('users/getApiClients', {}, { root: true });
        resolve(data);
      })
      .catch(reject);
  });
}
export function revokeApiClient({ dispatch }, { id }) {
  return dispatch('users/deleteApiClient', { id }, { root: true });
}
