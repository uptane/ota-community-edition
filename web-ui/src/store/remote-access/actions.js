/* */
import ApiService from 'src/services/api.service';
import {
  API_REMOTE_ACCESS_ADD_USER_PUBLIC_KEY,
  API_REMOTE_ACCESS_CREATE_SESSION,
  API_REMOTE_ACCESS_GET_USER_PUBLIC_KEYS,
  API_REMOTE_ACCESS_DELETE_USER_PUBLIC_KEY,
  API_REMOTE_ACCESS_GET_SESSIONS,
  API_REMOTE_ACCESS_DELETE_SESSION,
  API_REMOTE_ACCESS_GET_DEVICE_CURRENT_SESSION_DATA,
  API_REMOTE_ACCESS_GET_ALL_SESSIONS,
  API_REMOTE_ACCESS_GET_IP_ACCEPT_LIST,
  API_REMOTE_ACCESS_ADD_IP_ACCEPT_LIST,
  API_REMOTE_ACCESS_DELETE_IP_ACCEPT_LIST,
} from '../../config';

const parseErrorMessage = (error) => {
  if (error.response) {
    if (error.response.data && Array.isArray(error.response.data)) {
      let msg = '';
      error.response.data.forEach((item, index) => {
        msg += index + 1 + ': ' + (item.message || item.msg) + ' \n';
      });
      return msg;
    } else if (error.response.data && typeof error.response.data === 'array') {
      if (error.response.data && error.response.data.message) {
        return error.response.data.message;
      } else if (error.response.data && error.response.data.error) {
        return error.response.data.error;
      } else if (error.response.data && error.response.data.error_description) {
        return error.response.data.error_description;
      } else if (error.response.data && error.response.data.error_message) {
        return error.response.data.error_message;
      } else if (error.response.data && error.response.data.error_msg) {
        return error.response.data.error_msg;
      } else if (error.response.data && error.response.data.errorText) {
        return error.response.data.errorText;
      }
    } else if (error.response.data && typeof error.response.data === 'string') {
      return error.response.data;
    }

    return error.response.statusText || 'Unknown error';
  }
  return error.message;
};

// create device ssh session
export function createSession({ commit, state, dispatch, rootGetters }, { uuid, duration }) {
  return new Promise((resolve, reject) => {
    const fetchSession = (uuid) => {
      return dispatch('remoteAccess/fetchSession', { uuid }, { root: true });
    };
    const createSession = (uuid, publicKeys) => {
      ApiService.postResource(
        API_REMOTE_ACCESS_CREATE_SESSION.replace('{uuid}', uuid),
        {
          public_keys: publicKeys,
          session_duration: duration || '1800s', // default 30 minutes
        },
        {
          useHostAccessToken: false,
        },
      )
        .then((data) => {
          fetchSession(uuid)
            .then((sessionData) => {
              resolve(sessionData);
            })
            .catch(() => {
              reject('Session created, but unable to fetch session data');
            });
        })
        .catch((error) => {
          if (error.response && error.response.status === 409) {
            // session already exists
            fetchSession(uuid)
              .then((sessionData) => {
                resolve(sessionData);
              })
              .catch(() => {
                reject('Session already exists, but unable to fetch session data');
              });
          } else {
            reject(parseErrorMessage(error));
          }
        });
    };
    let publicKeys = [];
    const keys = (state.publicKeys || {}).keys;
    if (keys && _.values(keys).length) {
      publicKeys = _.map(keys, (key) => key.pubkey);
      createSession(uuid, publicKeys);
    } else {
      dispatch('remoteAccess/fetchPublicKeys', {}, { root: true })
        .then((data) => {
          if (data && data.keys && _.values(data.keys).length) {
            publicKeys = _.map(data.keys, (key) => key.pubkey);
            createSession(uuid, publicKeys);
          } else {
            return reject('No public keys found');
          }
        })
        .catch(() => {
          return reject('Unable to fetch public keys');
        });
    }
  });
}

// fetch device ssh sessions
export function fetchSession({ commit, state, dispatch, rootGetters }, { uuid }) {
  return new Promise((resolve, reject) => {
    ApiService.getResource(API_REMOTE_ACCESS_GET_SESSIONS.replace('{uuid}', uuid), {
      useHostAccessToken: false,
    })
      .then((data) => {
        let host_name, ssh_command, port;
        host_name = data.ssh.ra_server_url.split('@')[1].split(':')[0];
        port = data.ssh.reverse_port;
        ssh_command = `ssh -p ${data.ssh.reverse_port} torizon@${host_name}`;
        resolve({
          deviceUuid: uuid,
          ...data,
          host_name,
          port,
          ssh_command,
        });
      })
      .catch((error) => {
        reject(parseErrorMessage(error));
      });
  });
}

// fetch sessions for all devices
export function fetchAllSessions({ commit, state, dispatch, rootGetters }) {
  return new Promise((resolve, reject) => {
    ApiService.getResource(API_REMOTE_ACCESS_GET_ALL_SESSIONS, {
      useHostAccessToken: false,
    })
      .then((respArray) => {
        let dataArray = respArray.map((data, index) => {
          let host_name, ssh_command, port;
          let session = data.session;
          host_name = session.ssh.ra_server_url.split('@')[1].split(':')[0];
          port = session.ssh.reverse_port;
          ssh_command = `ssh -p ${session.ssh.reverse_port} torizon@${host_name}`;
          return {
            deviceUuid: data.device_id,
            session: { ...data, host_name, port, ssh_command },
          };
        });
        resolve(dataArray);
      })
      .catch((error) => {
        reject(parseErrorMessage(error));
      });
  });
}

// fetch session info for given device uuids
export function fetchSessionInfoForDevices({ commit, state, dispatch, rootGetters }, { uuids }) {
  return new Promise((resolve, reject) => {
    const uris = _.map(uuids, (uuid) => {
      return {
        url: API_REMOTE_ACCESS_GET_DEVICE_CURRENT_SESSION_DATA.replace('{uuid}', uuid),
        options: {
          useHostAccessToken: false,
          validateStatus: function(status) {
            return (status >= 200 && status < 300) || status === 304 || status === 404;
          },
        },
      };
    });
    ApiService.multiSourceGet(uris)
      .then((dataArray) => {
        dataArray = dataArray.filter((item) => typeof item !== 'string');
        resolve(dataArray);
      })
      .catch((error) => {
        reject(parseErrorMessage(error));
      });
  });
}

// fetch session info for a given device uuid
export function fetchSessionInfoForDevice({ commit, state, dispatch, rootGetters }, { uuid }) {
  return new Promise((resolve, reject) => {
    ApiService.getResource(API_REMOTE_ACCESS_GET_DEVICE_CURRENT_SESSION_DATA.replace('{uuid}', uuid), {
      useHostAccessToken: false,
    })
      .then((data) => {
        resolve(data);
      })
      .catch((error) => {
        reject(parseErrorMessage(error));
      });
  });
}

// kill ssh session
export function killSession({ commit, state, dispatch, rootGetters }, { deviceUuid }) {
  return new Promise((resolve, reject) => {
    ApiService.deleteResource(API_REMOTE_ACCESS_DELETE_SESSION.replace('{uuid}', deviceUuid), {
      useHostAccessToken: false,
    })
      .then((data) => {
        resolve(data);
      })
      .catch((error) => {
        reject(parseErrorMessage(error));
      });
  });
}

// add public key
export function addPublicKey({ commit, state, dispatch, rootGetters }, { publicKey, name }) {
  return new Promise((resolve, reject) => {
    ApiService.postResource(
      API_REMOTE_ACCESS_ADD_USER_PUBLIC_KEY,
      [
        {
          pubkey: publicKey,
          meta: {
            name,
          },
        },
      ],
      {
        useHostAccessToken: false,
      },
    )
      .then((data) => {
        dispatch('remoteAccess/fetchPublicKeys', {}, { root: true }).finally(() => {
          resolve(data);
        });
      })
      .catch((error) => {
        reject(parseErrorMessage(error));
      });
  });
}
// fetch public keys
export function fetchPublicKeys({ commit, state, dispatch, rootGetters }, options) {
  return new Promise((resolve, reject) => {
    ApiService.getResource(API_REMOTE_ACCESS_GET_USER_PUBLIC_KEYS, {
      useHostAccessToken: false,
    })
      .then((data) => {
        commit('remoteAccess/setPublicKeys', data, { root: true });
        resolve(data);
      })
      .catch((error) => {
        reject(parseErrorMessage(error));
      });
  });
}

// delete public key
export function deletePublicKey({ commit, state, dispatch, rootGetters }, { keyId }) {
  return new Promise((resolve, reject) => {
    let key_ids = [keyId];
    ApiService.deleteResource(API_REMOTE_ACCESS_DELETE_USER_PUBLIC_KEY, {
      useHostAccessToken: false,
      data: { key_ids },
    })
      .then((data) => {
        dispatch('remoteAccess/fetchPublicKeys', {}, { root: true }).finally(() => {
          resolve(data);
        });
      })
      .catch((error) => {
        reject(parseErrorMessage(error));
      });
  });
}

export function fetchIpAcceptList({ commit, state, dispatch, rootGetters }, options) {
  return new Promise((resolve, reject) => {
    ApiService.getResource(API_REMOTE_ACCESS_GET_IP_ACCEPT_LIST, {
      useHostAccessToken: false,
    })
      .then((data) => {
        resolve(data);
      })
      .catch((error) => {
        reject(parseErrorMessage(error));
      });
  });
}

export function addIpAcceptList({ commit, state, dispatch, rootGetters }, { ips }) {
  return new Promise((resolve, reject) => {
    ApiService.postResource(
      API_REMOTE_ACCESS_ADD_IP_ACCEPT_LIST,
      {
        ips,
      },
      {
        useHostAccessToken: false,
      },
    )
      .then((data) => {
        resolve(data);
      })
      .catch((error) => {
        reject(parseErrorMessage(error));
      });
  });
}

export function deleteIpAcceptListItem({ commit, state, dispatch, rootGetters }, { ip }) {
  return new Promise((resolve, reject) => {
    ApiService.deleteResource(API_REMOTE_ACCESS_DELETE_IP_ACCEPT_LIST.replace('{ip}', ip), {
      useHostAccessToken: false,
    })
      .then((data) => {
        resolve(data);
      })
      .catch((error) => {
        reject(parseErrorMessage(error));
      });
  });
}
