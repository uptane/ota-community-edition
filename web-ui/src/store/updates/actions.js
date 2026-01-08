import _ from 'lodash';

import ApiService from '../../services/api.service';
import {
  API_APPLY_MULTI_TARGET_UPDATES,
  API_CANCEL_SCHEDULED_UPDATE,
  API_CREATE_MULTI_TARGET_UPDATE,
  API_CREATE_SCHEDULED_UPDATES,
  API_CREATE_SCHEDULED_UPDATES_NEW,
  API_CREATE_UPDATES_NEW,
  API_GET_MULTI_TARGET_UPDATE_INDENTIFIER,
  API_GET_SCHEDULED_UPDATES_STATUS,
  API_OFFLINE_UPDATES_CREATE,
  API_OFFLINE_UPDATES_GET,
  API_OFFLINE_UPDATES_SNAPSHOT_GET,
  API_SET_LOCKBOX_EXPIRATION,
} from '../../config';

function _prepareMtuUpdateObject(updateData) {
  let targets = {};
  updateData.ecus.forEach((ecu) => {
    const hardwareIds = ecu.hardwareIds || [ecu.hardwareId];
    hardwareIds.forEach((hardwareId) => {
      targets[hardwareId] = {
        to: {
          target: ecu.package.filepath,
          checksum: {
            method: 'sha256',
            hash: ecu.package.packageHash || ecu.package.version,
          },
          targetLength: ecu.package.targetLength,
          uri: ecu.customUri || ecu.package.uri,
        },
        targetFormat: ecu.package.targetFormat,
        generateDiff: false,
      };
      if (ecu.package.buildType !== 'custom') {
        targets[hardwareId].delegatedRole = ecu.package.buildType;
      }
      if (ecu.userDefinedCustom) {
        targets[hardwareId].to.userDefinedCustom = ecu.userDefinedCustom;
      }
    });
  });
  return {
    targets,
    devices: updateData.updateDevices,
  };
}

export function fetchUpdates({ commit, state, dispatch, getters }) {
  return new Promise((resolve, reject) => {
    let apiAddress = `${API_OFFLINE_UPDATES_SNAPSHOT_GET}`;
    return ApiService.getResource(apiAddress)
      .then((updates) => {
        let metas = _.map(updates.signed.meta, (m, filename) => {
          return {
            filename,
            name: filename.replace('.json', ''),
            updateName: filename.replace('.json', ''),
            ...m,
          };
        });
        let apiAddresses = _.map(metas, (m) => {
          return `${API_OFFLINE_UPDATES_GET}/${m.filename}`;
        });
        return ApiService.multiSourceGet(apiAddresses)
          .then((result) => {
            const lockboxes = _.map(metas, (m, i) => {
              const expired = new Date(result[i].signed.expires).getTime() < Date.now();
              const revoked = !result[i].signed.targets || _.size(result[i].signed.targets) < 1;
              return {
                ...m,
                packages: result[i].signed,
                expireAt: result[i].signed.expires,
                expires: result[i].signed.expires,
                hash: (m.hashes || {}).sha256,
                expired,
                revoked,
              };
            });
            commit('updates/setUpdates', lockboxes, { root: true });
            resolve(lockboxes);
          })
          .catch((error) => {
            reject(error);
          })
          .finally(() => {});
      })
      .catch((error) => {
        reject(error);
      });
  });
}
export function fetchUpdateDetail({ commit, state, dispatch, getters }, { updateName }) {
  return new Promise((resolve, reject) => {
    let apiAddress = `${API_OFFLINE_UPDATES_GET}/${updateName}.json`;
    return ApiService.getResource(apiAddress)
      .then((result) => {
        resolve(result.signed);
      })
      .catch((error) => {
        reject(error);
      });
  });
}

export function createUpdate({ commit, state, dispatch, getters }, updateData) {
  return new Promise((resolve, reject) => {
    const { updateName, update } = updateData;
    let apiAddress = `${API_OFFLINE_UPDATES_CREATE}/${updateName.replace(/[^a-zA-Z\d-]/g, '_')}`;
    return ApiService.postResource(apiAddress, update, ApiService.defaultOptions, true)
      .then(async (result) => {
        await dispatch('updates/fetchUpdates', {}, { root: true });
        // We don't want to hard fail here, so we'll just log the error and continue
        try {
          await dispatch('updates/resetLockboxExpiry', {}, { root: true });
        } catch (e) {
          logError('Unable to set expiration date on user lockbox', e);
        }
        resolve(result);
      })
      .catch((error) => {
        reject(error.response || error);
      });
  });
}

export function resetLockboxExpiry({ commit, state, dispatch, getters }, { updateData }) {
  return new Promise((resolve, reject) => {
    const expiries = _.map(state.updates, 'packages.expires');
    let expireAt = _.max(expiries);
    ApiService.putResource(API_SET_LOCKBOX_EXPIRATION, { expireAt })
      .then(async (result) => {
        resolve(result);
      })
      .catch((error) => {
        reject(error.response || error);
      });
  });
}
export function deleteUpdate({ commit, state, dispatch, getters }, { updateName }) {
  return new Promise((resolve, reject) => {
    let apiAddress = `${API_OFFLINE_UPDATES_CREATE}/${updateName.replace(/[^a-zA-Z\d-]/g, '_')}`;
    return ApiService.postResource(apiAddress, { values: {} })
      .then((result) => {
        dispatch('updates/fetchUpdates', {}, { root: true });
        resolve(result);
      })
      .catch((error) => {
        reject(error);
      });
  });
}

export function requestMtuUpdate({ commit }, { updateData, schedulingData }) {
  return new Promise((resolve, reject) => {
    const { scheduled, deviceUuid, fleetId } = schedulingData || {};

    // Prepare the multi-target update object
    let { targets, devices } = _prepareMtuUpdateObject(updateData);
    const payload = {
      targets,
      devices,
    };
    if (scheduled) {
      payload.scheduledFor = new Date(scheduled).toISOString();
    }
    const successful = (resp) => {
      (devices || []).forEach((uuid) => {
        commit('devices/clearUpdateInstallationEventsForDevice', { uuid }, { root: true });
      });
      return resolve({ ...resp.data, scheduled });
    };
    const failed = (err) => {
      // Unable to apply the update - reject the promise
      return reject(err.response || err);
    };
    const url = scheduled ? API_CREATE_SCHEDULED_UPDATES_NEW.replace('{deviceUuid}', deviceUuid) : API_CREATE_UPDATES_NEW;
    ApiService.postResource(url, payload, ApiService.defaultOptions, true)
      .then(successful)
      .catch(failed);
  });
}

export function getScheduledUpdateStatus({ commit }, { deviceUuid }) {
  return new Promise((resolve, reject) => {
    ApiService.getResource(API_GET_SCHEDULED_UPDATES_STATUS.replace('{deviceUuid}', deviceUuid))
      .then((result) => {
        resolve(result);
      })
      .catch((error) => {
        reject(error);
      });
  });
}

export function cancelScheduledUpdate({ commit }, { deviceUuid, updateId }) {
  return new Promise((resolve, reject) => {
    ApiService.deleteResource(API_CANCEL_SCHEDULED_UPDATE.replace('{deviceUuid}', deviceUuid).replace('{updateId}', updateId))
      .then((result) => {
        resolve(result);
      })
      .catch((error) => {
        reject(error);
      });
  });
}

export function getMtuInformation({ commit }, { mtuId }) {
  return new Promise((resolve, reject) => {
    ApiService.getResource(API_GET_MULTI_TARGET_UPDATE_INDENTIFIER + '/' + mtuId)
      .then((result) => {
        resolve(result);
      })
      .catch((error) => {
        reject(error);
      });
  });
}
