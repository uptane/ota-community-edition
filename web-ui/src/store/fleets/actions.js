import { API_FLEET_DEVICES_SEARCH, API_GROUPS_ADD_DEVICE, API_GROUPS_CREATE, API_GROUPS_DELETE, API_GROUPS_FETCH, API_GROUPS_HIBERNATE, API_GROUPS_REMOVE_DEVICE, API_GROUPS_RENAME } from '../../config';
import ApiService from '../../services/api.service';
import _ from 'lodash';

export function createFleet({ dispatch }, fleet) {
  return new Promise((resolve, reject) => {
    // Newer versions of the api use "name" instead of "groupName"
    // Sending both "name" and "groupName" for backwards compatibility
    fleet.name = fleet.name || fleet.groupName;
    return ApiService.postResource(API_GROUPS_CREATE, fleet)
      .then((data) => {
        dispatch('fleets/fetchFleets', {}, { root: true });
        resolve(data);
      })
      .catch(reject);
  });
}
export function renameFleet({ dispatch }, { id, name }) {
  return new Promise((resolve, reject) => {
    return ApiService.putResource(API_GROUPS_RENAME + '/' + id + '/rename?groupName=' + name)
      .then((data) => {
        dispatch('fleets/fetchFleets', {}, { root: true });
        resolve(data);
      })
      .catch(reject);
  });
}
export function deleteFleet({ dispatch }, id) {
  return new Promise((resolve, reject) => {
    return ApiService.deleteResource(API_GROUPS_DELETE + '/' + id)
      .then((data) => {
        dispatch('fleets/fetchFleets', {}, { root: true });
        resolve(data);
      })
      .catch(reject);
  });
}
export function fetchFleets({ commit, state, dispatch, getters, rootGetters }, params = {}) {
  return new Promise((resolve, reject) => {
    // Get default pagination parameters from state
    const defaultOffset = 0;
    const defaultLimit = 10;
    const defaultSort = { name: 'asc' };

    const { filter = '', offset = defaultOffset, limit = defaultLimit, sort = defaultSort, storeResult = true, withPaginationResponse = true } = params;
    let url = `${API_GROUPS_FETCH}?nameContains=${filter}&limit=${limit}&offset=${offset}`;

    //  Ensure that the sort field is valid
    if (sort) {
      let columns = rootGetters['fleets/columns'] || [];
      let column = columns.find((f) => f.name === Object.keys(sort)[0] || f.id === Object.keys(sort)[0]);
      if (column && column.sortable) {
        let sortBy = column.field || '';
        url += `&sortBy=${(sortBy || '').toLowerCase()}&sortDirection=${Object.values(sort)[0]}`;
      }
    }

    let responseTotal = 0;
    let responseLimit = limit;
    let responseOffset = offset;

    const saveFleets = (fleets) => {
      fleets = fleets.map((fleet) => {
        fleet.deviceIds = (fleet.devices || []).map((device) => device.uuid);
        return fleet;
      });
      if (storeResult) {
        commit('fleets/setFleets', fleets, { root: true });
      }
      let response = fleets;
      if (withPaginationResponse) {
        response = {
          values: fleets,
          total: responseTotal,
          limit: responseLimit,
          offset: responseOffset,
        };
      }
      resolve(response);
    };
    this.fleetsOffset = 0;
    const deviceLimit = 0;
    ApiService.getResource(url)
      .then((fleetsData) => {
        const fleets = fleetsData.values;
        responseTotal = fleetsData.total;
        responseLimit = fleetsData.limit;
        responseOffset = fleetsData.offset;
        const fleedDevicesUrls = fleets.map((fleet) => API_FLEET_DEVICES_SEARCH + '?groupId=' + fleet.id + '&limit=' + deviceLimit);
        ApiService.multiSourceGet(fleedDevicesUrls)
          .then((devicesArray) => {
            for (let i = 0; i < devicesArray.length; i++) {
              fleets[i].devices = [];
              fleets[i].deviceCount = devicesArray[i].total;
            }
            saveFleets(fleets);
          })
          .catch((err) => {
            saveFleets(fleets);
          });
      })
      .catch(reject);
  });
}

export async function fetchFleet({ commit, state, dispatch, getters, rootGetters }, param) {
  if (!param) return { code: 'invalid_parameter' };
  let id = param;
  let forceFetch = false;
  if (typeof id === 'object') {
    id = param.id;
    forceFetch = param.forceFetch;
  }

  const fleets = rootGetters['fleets/fleets'] || [];
  let fleet = fleets.find((fleet) => fleet.id === id);
  if (!fleet || forceFetch) {
    try {
      const fleetResp = await ApiService.getResource(API_GROUPS_FETCH + '/' + id);
      fleet = fleetResp;
      const devicesResp = await dispatch('fetchFleetDevices', { fleetId: id, limit: 500 });
      fleet.devices = devicesResp.values || [];
      fleet.deviceIds = (fleet.devices || []).map((device) => device.uuid);
      fleet.deviceCount = devicesResp.total;
    } catch (e) {}
  }
  return fleet;
}

export async function fetchFleetDevices({ commit, state, dispatch, getters, rootGetters }, options = { fleetId: '', offset: 0, limit: 10 }) {
  const { fleetId, offset, limit } = options;
  if (!fleetId) return { code: 'invalid_parameter' };
  const resp = await dispatch('devices/fetchDevices', { filter: '', groupId: fleetId, offset, limit, storeResult: false }, { root: true });
  return resp;
}

export function addDeviceToFleet({ dispatch, rootGetters }, { fleetId, deviceUuid, skipRefresh }) {
  return new Promise((resolve, reject) => {
    ApiService.postResource(API_GROUPS_ADD_DEVICE + '/' + fleetId + '/devices/' + deviceUuid, {})
      .then((data) => {
        if (!skipRefresh) {
          dispatch('fleets/fetchFleets', {}, { root: true });
        }
        resolve(data);
      })
      .catch(reject);
  });
}
export function removeDeviceFromFleet({ dispatch }, { fleetId, deviceUuid, skipRefresh }) {
  return new Promise((resolve, reject) => {
    ApiService.deleteResource(API_GROUPS_REMOVE_DEVICE + '/' + fleetId + '/devices/' + deviceUuid)
      .then((data) => {
        if (!skipRefresh) {
          dispatch('fleets/fetchFleets', {}, { root: true });
        }
        resolve(data);
      })
      .catch(reject);
  });
}

export function getNext({ state }, id) {
  const fleets = state.fleets;
  let index =
    fleets.findIndex((fleet) => {
      return fleet.id === id;
    }) + 1;
  if (index > fleets.length - 1) index = 0;
  return Promise.resolve(fleets[index]);
}

export function getPrevious({ state }, id) {
  const fleets = state.fleets;
  let index =
    fleets.findIndex((fleet) => {
      return fleet.id === id;
    }) - 1;
  if (index < 0) index = fleets.length - 1;
  return Promise.resolve(fleets[index]);
}

export function setDevicesHibernationState({ dispatch, commit, getters }, { uuid, state }) {
  return new Promise((resolve, reject) => {
    ApiService.postResource(API_GROUPS_HIBERNATE.replace(':uuid', uuid), { status: state })
      .then(() => {
        dispatch('devices/fetchDevices', {}, { root: true }).finally(() => {
          resolve();
        });
      })
      .catch((err) => {
        reject(err);
      });
  });
}
