import { API_ECUS_FETCH, API_ECUS_PUBLIC_KEY_FETCH, API_HARDWARE_IDS_FETCH } from '../../config';
import ApiService from '../../services/api.service';

export function fetchDeviceHardwareInfo({ commit }, deviceUuid) {
  return new Promise((resolve, reject) => {
    ApiService.getResource(`${API_ECUS_FETCH}/${deviceUuid}/system_info`)
      .then((info) => {
        resolve(info);
      })
      .catch(reject);
  });
}

export function fetchPublicKey({ commit }, { ecuId, deviceUuid }) {
  return new Promise((resolve, reject) => {
    ApiService.getResource(`${API_ECUS_PUBLIC_KEY_FETCH}/${deviceUuid}/ecus/public_key?ecu_serial=${ecuId}`)
      .then((key) => {
        resolve(key);
      })
      .catch(reject);
  });
}

export function fetchHardwareIds({ commit }) {
  return new Promise((resolve, reject) => {
    const currentPage = 0,
      limit = 1000;
    ApiService.getResource(`${API_HARDWARE_IDS_FETCH}` + '?limit=' + limit + '&offset=' + currentPage * limit)
      .then((ids) => {
        commit('hardware/setHardwareIds', (ids || {}).values, { root: true });
        resolve(ids);
      })
      .catch(reject);
  });
}
