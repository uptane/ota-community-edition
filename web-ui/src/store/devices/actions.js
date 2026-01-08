import _ from 'lodash';

import ApiService from '../../services/api.service';
import {
  API_DEVICES_SEARCH,
  API_DEVICES_NETWORK_INFO,
  API_DEVICES_DEVICE_DETAILS,
  API_DEVICES_DIRECTOR_DEVICE,
  API_DEVICES_DELETE,
  API_CANCEL_MULTI_TARGET_UPDATE,
  API_CANCEL_IN_FLIGHT_UPDATE,
  API_FETCH_DEVICE_CORE_UPDATE,
  API_SEND_DEVICE_UPDATE,
  API_FETCH_MULTI_TARGET_UPDATES,
  API_FETCH_DEVICE_UPDATE_INSTALATION_REPORTS,
  API_FETCH_DEVICE_UPDATE_EVENTS,
  API_DIRECTOR_DEVICES_INSTALLED_PACKAGES,
  API_DEVICES_UPDATE_DATA,
  API_DEVICES_HIBERNATE,
  API_DEVICES_PROVISIONING_CODE_CLAIM,
  API_GROUPS_DETAIL,
  API_CANCEL_UPDATE_NEW,
  API_GET_UPDATES_NEW,
} from '../../config';

import { extend, LocalStorage, Notify } from 'quasar';

import { WS_DATA_REQUEST } from '../../constants';

function parseHardwareType(device) {
  if (device.hardwareType) {
    return device.hardwareType;
  }
  const hwIdArray = (device.deviceId || '').split('-');
  const altHardwareType = hwIdArray.slice(0, hwIdArray.length - 2).join('-');
  return altHardwareType;
}

function _populateStatusData(device) {
  device = device || {};
  const defDate = new Date('01/01/2001');
  const lastSeenDate = new Date((device || {}).lastSeen || defDate);
  const lastSeen = Date.now() - lastSeenDate.getTime();

  const data = {
    show: (device || {}).deviceStatus && (device || {}).deviceStatus !== 'NotSeen',
    text: `${Math.round(lastSeen / (60 * 1000))}m`,
    summary: 'not-seen',
  };
  const cutOff = 18;
  data.summary = 'not-seen';
  data.colorClass = 'bg-grey-2 text-black';
  data.color = 'negative';
  data.textColor = 'black';
  if (device.hibernated || (device.device || {}).hibernated) {
    data.text = 'Zzz';
    data.colorClass = 'bg-grey-8 text-white';
    data.color = 'grey-8';
    data.textColor = 'white';
    data.summary = `This device is in hibernation mode`;
  } else if (lastSeen < Math.round((cutOff / 3) * 60 * 1000)) {
    data.colorClass = 'bg-positive text-white';
    data.color = 'green-8';
    data.textColor = 'white';
    let min = Math.round(lastSeen / (60 * 1000));
    if (min < 1) {
      data.summary = `Reported online less than a minute ago`;
    } else if (min === 1) {
      data.summary = `Reported online about a minute ago`;
    } else {
      data.summary = `Reported online about ${min} minutes ago`;
    }
  } else if (lastSeen < Math.round((cutOff / 2) * 60 * 1000) && lastSeen >= Math.round((cutOff / 3) * 60 * 1000)) {
    data.color = 'orange-8';
    data.textColor = 'white';
    data.colorClass = 'bg-warning text-white';
    data.summary = `Last seen online about ${Math.round(lastSeen / (60 * 1000))} minutes ago`;
  } else if (lastSeen >= (cutOff / 2) * 60 * 1000) {
    data.colorClass = 'bg-negative text-white';
    data.color = 'negative';
    data.textColor = 'white';
    data.icon = 'mdi-cloud-alert';
    data.summary = `This device hasn't been online for a while.`;
  } else if (!data.show) {
    data.colorClass = 'bg-negative text-white';
    data.summary = `This device was never seen online`;
    data.color = 'negative';
    data.textColor = 'white';
    data.text = '???';
  }
  return data;
}

function _prepareUpdateObject(updateData) {
  // }
  let targets = [];
  updateData.toVersion.hardwareIds.forEach((hwId) => {
    const update = {
      hardwareType: hwId,
      to: {
        target: updateData.toVersion.filepath,
        checksum: {
          method: 'sha256',
          hash: updateData.toVersion.packageHash || updateData.toVersion.version,
        },
        targetLength: updateData.toVersion.targetLength,
      },
      targetFormat: updateData.toVersion.targetFormat,
      generateDiff: false,
    };
    if (updateData.toVersion.buildType !== 'custom') {
      update.delegatedRole = updateData.toVersion.buildType;
    }
    targets.push(update);
  });
  return {
    targets,
    updateDevices: updateData.updateDevices,
  };
}

function _prepareDevices({ state, getters }, devicesSort = 'desc') {
  let devices = state.devices;
}
export function createNewDeviceEntry({ commit, dispatch }, deviceData) {
  commit('devices/addNewDevice', deviceData, { root: true });
  dispatch('devices/fetchDevice', { uuid: deviceData.uuid }, { root: true });
}
export function updateDeviceStatus({ commit }, deviceData) {
  commit('devices/updateSingleDevice', { ...deviceData }, { root: true });
}
export function removeDeviceWithUpdateInProgress({ commit }, uuid) {
  return commit('ui/removeDeviceWithUpdateInProgress', uuid);
}
export function getNextDevice({ state }, id) {
  const devices = state.devices;
  let index =
    devices.findIndex((device) => {
      return device.uuid === id;
    }) + 1;
  if (index > devices.length - 1) index = 0;
  return Promise.resolve(devices[index]);
}

export function getPreviousDevice({ state }, id) {
  const devices = state.devices;
  let index =
    devices.findIndex((device) => {
      return device.uuid === id;
    }) - 1;
  if (index < 0) index = devices.length - 1;
  return Promise.resolve(devices[index]);
}

export function getDeviceStatusData(context, device) {
  return new Promise((resolve, reject) => {
    resolve(_populateStatusData(device));
  });
}
export function fetchDeviceUpdates({ state }, id) {
  return new Promise((resolve, reject) => {
    return ApiService.getResource(`${API_FETCH_DEVICE_CORE_UPDATE}/${id}/updates/`)
      .then((data) => {
        const updates = data;
        // const device = state.devices.find(d => d.deviceId === id)
        resolve(updates);
      })
      .catch((error) => {
        reject(error);
      });
  });
}
export function fetchDeviceNetworkInfo({ state }, options = { id: 'null', isFromWs: false }) {
  let { id, isFromWs } = options;
  return new Promise((resolve, reject) => {
    let activeDevice = state.device;
    if (!isFromWs || (isFromWs && activeDevice.uuid === id)) {
      return ApiService.getResource(API_DEVICES_NETWORK_INFO + '/' + id + '/system_info/network')
        .then((data) => {
          resolve(data);
        })
        .catch((error) => {
          reject(error);
        });
    }
  });
}
export function fetchDeviceSystemInfo({ state }, deviceUuid) {
  return new Promise((resolve, reject) => {
    return ApiService.getResource(API_DEVICES_NETWORK_INFO + '/' + deviceUuid + '/system_info')
      .then((data) => {
        resolve(data);
      })
      .catch((error) => {
        reject(error);
      });
  });
}

export function requestDeviceUpdate({ dispatch, commit }, payload) {
  return new Promise((resolve, reject) => {
    let updateObject = _prepareUpdateObject(payload);
    // log('Update data: ', updateObject);
    return ApiService.postResource(API_SEND_DEVICE_UPDATE, updateObject)
      .then((data) => {
        (updateObject.updateDevices || []).forEach((uuid) => {
          commit('devices/clearUpdateInstallationEventsForDevice', { uuid }, { root: true });
        });
        resolve(data);
      })
      .catch((error) => {
        reject(error);
      });
  });
}

function buildDeviceUrl({ filter, groupId, offset, limit, sort, additionalQueries, rootGetters }) {
  const search = filter ? `nameContains=${filter}&` : '';
  let apiAddress = `${API_DEVICES_SEARCH}?${search}limit=${limit}`;
  if (!filter) apiAddress += `&offset=${offset}`;
  if (groupId && groupId === 'ungrouped') apiAddress += `&ungrouped=true`;
  else if (groupId) apiAddress += `&groupId=${groupId}`;
  // add sorting (sort is an object with key = column name and value = asc/desc)
  if (sort) {
    let columns = rootGetters['devices/columns'];
    let column = columns.find((f) => f.name === Object.keys(sort)[0] || f.id === Object.keys(sort)[0]);
    if (column && column.sortable) {
      let sortBy = column.field;
      apiAddress += `&sortBy=${(sortBy || '').toLowerCase()}&sortDirection=${Object.values(sort)[0]}`;
    }
  }
  if (additionalQueries) {
    Object.keys(additionalQueries).forEach((key) => {
      apiAddress += `&${key}=${additionalQueries[key]}`;
    });
  }
  return apiAddress;
}
export async function fetchDevicesByUuids({ commit, state, dispatch, getters, rootGetters }, options = { deviceUuids: [] }) {
  let { deviceUuids } = options;
  let promises = deviceUuids.map((deviceUuid) => dispatch('devices/fetchDevice', deviceUuid, { root: true }));
  try {
    let devices = await Promise.all(promises);
    return devices;
  } catch (error) {
    throw error;
  }
}

export async function fetchDeviceCount({ commit, state, dispatch, getters, rootGetters }, options = { additionalQueries: {} }) {
  let url = buildDeviceUrl({ filter: '', groupId: '', offset: 0, limit: 0, sort: { name: 'asc' }, additionalQueries: options.additionalQueries, rootGetters });
  try {
    let respData = await ApiService.getResource(url);
    return respData.total;
  } catch (error) {
    logError('Unable to fetch devices count', error);
    return 0;
  }
}

const ensureDeviceOffset = (offset, limit, total) => {
  // Ensure that the offset is never higher than orqual to  the total number of devices
  if (total > 0 && offset >= total) {
    offset = total - limit;
  }

  // Ensure that the offset is never negative
  if (offset < 0) {
    offset = 0;
  }
  return offset;
};
export async function fetchDevices({ commit, state, dispatch, getters, rootGetters }, options = { filter: '', groupId: '', offset: null, limit: null, storeResult: true, sort: { name: 'asc' }, additionalQueries: {} }) {
  let { groupId, filter, offset, limit, sort, storeResult, withPaginationResponse = true, additionalQueries } = options;
  filter = filter || '';
  let devicesOffset = typeof offset === 'undefined' ? getters['offset'] : offset;
  devicesOffset = devicesOffset || 0; // Additonal check for null
  let devicesLimit = limit || getters['limit'];
  let devicesTotal = getters['total'];

  // Ensure that the offset is never higher than orqual to  the total number of devices
  devicesOffset = ensureDeviceOffset(devicesOffset, devicesLimit, devicesTotal);

  sort = sort || getters['sort'];
  if (typeof storeResult === 'undefined') storeResult = true;

  groupId = groupId || (rootGetters['fleets/selectedFleet'] || {}).id;

  filter = filter.toLowerCase();
  let apiAddress = buildDeviceUrl({ filter, groupId, offset: devicesOffset, limit: devicesLimit, sort, additionalQueries, rootGetters });
  try {
    let respData = await ApiService.getResource(apiAddress);
    // if offset is bigger than total and this is not a search, we need to fetch first page
    // if (respData.total <= devicesOffset && filter === '') {
    //   devicesOffset = 0;
    //   let apiPageAddress = buildDeviceUrl({ filter, groupId, offset: devicesOffset, limit: devicesLimit, sort, rootGetters });

    //   respData = await ApiService.getResource(apiPageAddress);
    // }
    let values = respData.values || [];
    devicesOffset = ensureDeviceOffset(respData.offset, respData.limit, respData.total);
    devicesLimit = respData.limit;
    devicesTotal = respData.total;

    let deviceIds = values.map((m) => m.uuid);
    let shellSessions = [];
    let installedTargets = [];
    try {
      shellSessions = await dispatch('remoteAccess/fetchAllSessions', { uuids: deviceIds }, { root: true });
    } catch (err) {
      logError('Unable to fetch devices shell sessions', err);
    }
    try {
      installedTargets = await dispatch('devices/getInstalledTargets', deviceIds, { root: true });
    } catch (err) {
      logError('Unable to fetch targets for devices', err);
    }

    let devices = values.map((m) => {
      try {
        m.shellSession = shellSessions.find((f) => f.deviceUuid === m.uuid) || {};
        m.installedTargets = installedTargets.values[m.uuid] || {};
      } catch (e) {}
      return extend(m, { hardwareType: m.hardwareType || parseHardwareType(m) });
    });

    devices = devices.map((m, i) => {
      let newProp = {};
      return { ...m, ...newProp };
    });
    let deviceList = devices;
    if (storeResult) {
      commit('devices/setDevices', deviceList, { root: true });
    }

    _prepareDevices({ state, getters });
    if (state.deviceListRefreshRate && state.deviceListRefreshRate > 0) {
    }
    if (withPaginationResponse) {
      return { values: deviceList, offset: devicesOffset, limit: devicesLimit, total: devicesTotal };
    }
    return deviceList;
  } catch (error) {
    logError('Unable to fetch devices', error);
    return [];
  }
}

export async function fetchDevice({ commit, state, dispatch, getters }, data, saveInStore = true) {
  let id = data;
  let failIfDirectoryNotFound = false;
  if (_.isObject(data)) {
    id = data.uuid;
    failIfDirectoryNotFound = data.failIfDirectoryNotFound;
  }
  const [legacy, director] = await ApiService.multiSourceGet([
    {
      url: API_DEVICES_DEVICE_DETAILS + '/' + id + '?status=true',
      options: {
        validateStatus: function(status) {
          return (status >= 200 && status < 300) || status === 304; // default
        },
      },
    },
    {
      url: API_DEVICES_DIRECTOR_DEVICE + '/' + id,
      options: {
        validateStatus: function(status) {
          if (failIfDirectoryNotFound) {
            return status === 200;
          } else {
            return status >= 200 || status === 404;
          }
        },
      },
    },
  ]);
  let device = legacy;
  if (!director.code) {
    device.isDirector = true;

    device.components = director.map((s) => {
      const isBaseOS = s.primary;
      const isApplication = s.hardwareId === 'docker-compose';
      const isBootloader = _.endsWith(s.hardwareId, 'bootloader');
      const isRemoteAccess = _.includes(s.hardwareId, 'remote-access');
      let order = 10;
      if (isBaseOS) order = 0;
      if (isApplication) order = 1;
      if (isBootloader) order = 2;
      if (isRemoteAccess) order = 3;
      return { ...s, order, isBaseOS, isApplication, isBootloader, isRemoteAccess };
    });

    let primary = _.filter(director, (data, index) => {
      return (data || {}).primary;
    });
    let secondary = _.filter(director, (data, index) => {
      return !(data || {}).primary;
    });
    device.directorAttributes = {
      primary: _.first(primary),
      secondary: secondary,
    };
    device.hardwareType = (_.first(primary) || {}).hardwareId;
  }
  if (!device.hardwareType) {
    device.hardwareType = parseHardwareType(device);
  }

  const updateDevice = (data) => {
    try {
      updatedDevice = { ...updatedDevice, ...data };
    } catch (e) {}
  };

  let sessionInfo, fleetsResp, deviceNetworkInfo;
  let updatedDevice = device;
  try {
    sessionInfo = await dispatch('remoteAccess/fetchSessionInfoForDevice', { uuid: updatedDevice.uuid }, { root: true });
    updateDevice({ sessionInfo });
  } catch (e) {}

  try {
    fleetsResp = await dispatch('devices/fetchDeviceFleets', updatedDevice.uuid, { root: true });
    updateDevice({ fleets: fleetsResp.values });
  } catch (e) {}

  try {
    deviceNetworkInfo = await dispatch('devices/fetchDeviceNetworkInfo', { id: updatedDevice.uuid }, { root: true });
    updateDevice({ networkInfo: deviceNetworkInfo });
  } catch (e) {}

  // Update the device in the store
  commit('devices/updateSingleDevice', updateDevice, { root: true });

  if (saveInStore) {
    commit('devices/setDevice', extend({}, updatedDevice, device), { root: true });
  }

  return updatedDevice;
}

export async function fetchDeviceFleets({ commit, state, dispatch, getters }, id) {
  const fleetResponse = await ApiService.getResource(API_DEVICES_DEVICE_DETAILS + '/' + id + '/groups');
  const fleetData = fleetResponse.values || [];
  // Fetch fleet details
  const fleetDetails = await Promise.all(
    fleetData.map((fleetId) => {
      const detail = ApiService.getResource(API_GROUPS_DETAIL + '/' + fleetId);
      return detail;
    }),
  );

  return { ...fleetResponse, values: fleetDetails };
}

export function claimProvisioningCode({ commit, state, dispatch, getters }, { code }) {
  return ApiService.postResource(API_DEVICES_PROVISIONING_CODE_CLAIM, { provisionCode: code });
}
export function getProvisioningCodeStatus({ commit, state, dispatch, getters }, { code }) {
  return ApiService.getResource(API_DEVICES_PROVISIONING_CODE_CLAIM + '?provisionCode=' + code);
}

export function cancelUpdates(context, { deviceUuids }, force = false) {
  return new Promise((resolve, reject) => {
    deviceUuids = deviceUuids || [];
    // Get updates for multiple devices by UUIDs in separate requests
    const getPromises = deviceUuids.map((uuid) => {
      return ApiService.getResource(API_GET_UPDATES_NEW.replace(':device-id', uuid), {}, { ...ApiService.defaultOptions });
    });
    Promise.all(getPromises)
      .then((responses) => {
        // responses is an array of responses for each device UUID
        let cancelPromises = [];
        responses.map((response, deviceIndex) => {
          const updatesToCancel = response.data.values || [];
          const nonCancelableUpdates = ['Completed', 'Cancelled'];
          const cancellableUpdates = updatesToCancel.filter((update) => {
            return !nonCancelableUpdates.includes(update.status);
          });
          cancelPromises = [
            ...cancelPromises,
            ...cancellableUpdates.map((update) => {
              const url = API_CANCEL_UPDATE_NEW.replace(':device-id', deviceUuids[deviceIndex]).replace(':update-id', update.updateId);
              return ApiService.patchResource(url, {}, { ...ApiService.defaultOptions, headers: force ? { 'x-trx-force': 'true' } : {} });
            }),
          ];
        });

        Promise.all(cancelPromises)
          .then((results) => {
            resolve(results);
          })
          .catch((err) => {
            reject(err);
          });
      })
      .catch((err) => {
        reject(err);
      });
  });
}
export function cancelInFlightUpdate(context, { deviceUuid }) {
  return cancelUpdates(context, { deviceUuids: [deviceUuid] }, true);
}
export function getUpdateStatus(context, deviceUuids) {
  return new Promise((resolve, reject) => {
    ApiService.getResource(API_FETCH_MULTI_TARGET_UPDATES + '/' + (deviceUuids || []).join(','))
      .then((data) => {
        const mappedData = data.map((m, i) => {
          return { ...m, deviceUuid: deviceUuids[i] };
        });
        resolve(_.keyBy(mappedData, (e) => e.deviceUuid));
      })
      .catch((err) => {
        reject(err);
      });
  });
}
export function getUpdateInstallationReports({ commit }, deviceUuid) {
  return new Promise((resolve, reject) => {
    ApiService.getResource(API_FETCH_DEVICE_UPDATE_INSTALATION_REPORTS.replace('{deviceUuid}', deviceUuid))
      .then((data) => {
        const values = data.values.map((m) => {
          const ecus = _.values(
            _.map(m.ecuReports, (value, key) => {
              value.ecuId = key;
              return value;
            }),
          );
          return { ...m, ecus };
        });
        commit('devices/setUpdateInstallationHistoryForDevice', { uuid: deviceUuid, events: values }, { root: true });
        resolve(values);
      })
      .catch((err) => {
        reject(err);
      });
  });
}
export function getUpdateEvents({ commit }, deviceUuid) {
  return new Promise((resolve, reject) => {
    ApiService.getResource(API_FETCH_DEVICE_UPDATE_EVENTS.replace('{deviceUuid}', deviceUuid))
      .then((data) => {
        commit('devices/setUpdateInstallationEventsForDevice', { uuid: deviceUuid, events: data }, { root: true });
        resolve(data);
      })
      .catch((err) => {
        reject(err);
      });
  });
}

export function deleteDevice({ commit, state, dispatch, getters }, id) {
  return new Promise((resolve, reject) => {
    return ApiService.deleteResource(API_DEVICES_DELETE + '/' + id)
      .then((data) => {
        let deviceList = state.devices;
        deviceList = deviceList.filter((f) => f.uuid !== id);
        commit('devices/setDevices', deviceList, { root: true });
        _prepareDevices({ state, getters });
        resolve(data);
      })
      .catch((error) => {
        reject(error);
      });
  });
}
export function updateDeviceData({ commit, state, dispatch, getters }, device) {
  return new Promise((resolve, reject) => {
    commit('devices/updateSingleDevice', device, { root: true });
    resolve(device);
  });
}
export function showDeviceUpdatedNotification({ state }, deviceUuid) {
  const device = state.devices.find((a) => a.uuid === deviceUuid) || {};
  let message = `${device.deviceName} was successfully updated`;
  Notify.create({ message, color: 'positive' });
}
export function showDeviceUpdatedFailedNotification({ state }, deviceUuid) {
  const device = state.devices.find((a) => a.uuid === deviceUuid) || {};
  let message = `Update failed for ${device.deviceName}`;
  Notify.create({ message, color: 'negative' });
}
export function renameDevice({ state, commit }, { id, data }) {
  return patchDevice({ state, commit }, { uuid: id, data });
}
export function fetchDirectorInfo({}, deviceUuid) {
  return new Promise((resolve, reject) => {
    return ApiService.getResource(API_DEVICES_DIRECTOR_DEVICE + '/' + deviceUuid)
      .then((data) => {
        resolve(data);
      })
      .catch((err) => {
        reject(err);
      });
  });
}
export function getDescription({}, deviceUuid) {
  return new Promise((resolve, reject) => {
    reject(new Error('Not implemented'));
  });
}
export function patchDevice({ state, commit }, { uuid, data }) {
  return new Promise((resolve, reject) => {
    let deviceData = state.devices.find((d) => d.uuid === uuid) || {};
    // Device name is required, so we need to make sure is not empty.
    // If it is, we will use the device name from the device data
    deviceData = { deviceName: deviceData.deviceName, ...data };
    return ApiService.patchResource(API_DEVICES_UPDATE_DATA + '/' + uuid, deviceData, {
      headers: { 'Content-Type': 'application/json' },
    })
      .then((resp) => {
        // let activeDevice = state.device
        // let device = _.find(deviceList, device => device.uuid === uuid);
        //     device.deviceName = data.deviceName;
        //     commit('devices/setDevices', deviceList.splice(deviceList.findIndex(d => d.uuid === device.uuid), 1, device), { root: true })
        // // fetchDevicesCount();
        resolve(resp);
      })
      .catch((error) => {
        reject(error);
      });
  });
}

export function setDeviceHibernationState({ dispatch, commit, getters }, { uuid, state }) {
  return new Promise((resolve, reject) => {
    ApiService.postResource(API_DEVICES_HIBERNATE.replace(':uuid', uuid), { status: state })
      .then(() => {
        commit('devices/updateSingleDevice', { uuid, hibernated: state }, { root: true });
        resolve();
      })
      .catch((err) => {
        reject(err);
      });
  });
}

/**
 * Get a list of installed targets for a list of device UUIDs
 * @param {Array} deviceUuids - List of device UUIDs
 * @returns {Promise} - List of installed targets
 */
export function getInstalledTargets({ commit }, deviceUuids) {
  return new Promise((resolve, reject) => {
    ApiService.postResource(API_DIRECTOR_DEVICES_INSTALLED_PACKAGES, deviceUuids)
      .then((data) => {
        resolve(data);
      })
      .catch((err) => {
        reject(err);
      });
  });
}
