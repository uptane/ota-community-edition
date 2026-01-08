import axios from 'axios';
import ApiService from '../../services/api.service';
import {
  API_PACKAGES,
  API_PACKAGES_COUNT_INSTALLED_ECUS,
  API_PACKAGES_DELEGATIONS,
  API_UPLOAD_PACKAGE,
  API_PACKAGES_GET_CONTENT,
  API_PACKAGES_GET_DESCRIPTIONS,
  API_PACKAGES_CREATE_DESCRIPTION,
  API_USER_REPO_TARGETS,
  API_USER_REPO_SET_COMPATIBILITY,
  API_PACKAGES_REFRESH_REMOTE_DELEGATIONS,
  API_PACKAGES_METADATA_DELEGATIONS,
  API_USER_REPO_TRUSTED_DELEGATIONS_KEYS,
  API_USER_REPO_TRUSTED_DELEGATIONS,
  API_USER_REPO_UPLOAD_DELEGATIONS_METADATA,
  API_USER_REPO_ADD_DELEGATIONS_METADATA_URL,
  API_USER_REPO_DELETE_TRUSTED_DELEGATION,
  API_USER_REPO_TRUSTED_DELEGATION_INFO,
  API_USER_REPO_ALL_TRUSTED_DELEGATION_INFO,
  API_PACKAGES_STATIC_DELTAS,
} from '../../config';

import { calculateKeyId } from '../../utils/Common';

const _packageURI = (filename, name, version, hardwareIds) => {
  return API_UPLOAD_PACKAGE.replace('{filename}', filename) + '?name=' + encodeURIComponent(name) + '&version=' + encodeURIComponent(version) + '&hardwareIds=' + hardwareIds;
};
const _preparePackages = (context, data, delegationInfo) => {
  const packages = data.signed.targets;
  const branchMap = { 0: 'master', 4: 'zeus', 5: 'dunfell' };
  const parsedPackages = _.keyBy(
    _.map(packages, (pkg, filepath) => {
      pkg = { ...pkg, ...pkg.custom };
      pkg.source = delegationInfo.source;
      pkg.isToradexPackage = delegationInfo.source === 'toradex';
      pkg.rawName = pkg.name;
      pkg.id = filepath;
      pkg.buildType = delegationInfo.buildType;
      pkg.metadata = (pkg.custom || {}).ostreeMetadata || {};
      pkg.versionName = pkg.version.match(/^[a-fA-F0-9]{64}$/) && pkg.commitSubject ? pkg.commitSubject : pkg.version;
      pkg.hasSize = pkg.length > 0;
      pkg.targetLength = pkg.length;
      pkg.hash = pkg.hashes.sha256;
      pkg.packageHash = pkg.hash;
      pkg.filepathAndHash = `${filepath}-${pkg.hash}`;
      pkg.uuid = pkg.hash; // alias for pkg.hash
      pkg.userUploaded = delegationInfo.source === 'user';
      pkg.isCustom = pkg.buildType === 'custom' || !_.startsWith(delegationInfo.name, 'tdx-');
      pkg.isBinary = pkg.targetFormat === 'BINARY';
      pkg.isOStree = pkg.targetFormat === 'OSTREE';
      pkg.isApplicationPackage = pkg.hardwareIds[0] === 'docker-compose';
      pkg.isOSPackage = pkg.targetFormat === 'OSTREE';
      pkg.compatibleWith = _.map(pkg.compatibleWith, (c) => ({ sha256: c.sha256 || c.hash }));
      const tagNumber = +(pkg.name || '').substring(0, 1);
      if ([0, 4, 5].indexOf(tagNumber) !== -1) {
        const tagName = (pkg.metadata['oe.distro-codename'] || branchMap[tagNumber]) + '/';
        pkg.name = (pkg.name || '').replace(/^([0,4,5]\/)/, tagName);
      }
      pkg.packageName = pkg.name; // alias for pkg.name
      pkg.sourceDelegation = delegationInfo.name;
      pkg.isExpired = delegationInfo.isExpired;
      return { ...pkg, delegationInfo, filepath, ...pkg.hashes };
    }),
    'filepathAndHash',
  );
  const group = _.groupBy(parsedPackages, (pkg) => {
    return pkg.name;
  });
  _.each(group, (g) => {
    const latest = _.maxBy(g, (m) => m.createdAt);
    const oldest = _.minBy(g, (m) => m.createdAt);
    parsedPackages[latest.filepathAndHash].latest = true;
    parsedPackages[oldest.filepathAndHash].oldest = true;
  });
  return _.sortBy(parsedPackages, ['name', 'createdAt']).reverse();
};

const fetchPackagesCountInstalledEcus = (context, filepaths) => {
  return new Promise((resolve, reject) => {
    ApiService.postResource(API_PACKAGES_COUNT_INSTALLED_ECUS, { filepaths })
      .then((data) => {
        resolve(data);
      })
      .catch((e) => {
        reject(e);
      });
  });
};

export function fetchPackages(context, { silent } = {}) {
  const { commit, state, dispatch } = context;
  return new Promise((resolve, reject) => {
    if (!silent) commit('ui/setLoadingPackages', true, { root: true });
    ApiService.getResource(API_PACKAGES)
      .then((targets) => {
        const stateDelegations = _.cloneDeep(state.delegations);
        let delegations = targets.signed.delegations.roles.map((role) => {
          const roleData = {
            ...stateDelegations.find((d) => d.key === role.name),
            ...role,
          };
          roleData.name = roleData.name || roleData.key;
          roleData.uri = `${API_PACKAGES_DELEGATIONS}/${roleData.name}.json`;
          return roleData;
        });
        const delgationUris = delegations.map((d) => d.uri);
        ApiService.multiSourceGet(
          delgationUris,
          {
            ...ApiService.defaultOptions,
            validateStatus: function(status) {
              return (status >= 200 && status < 300) || status === 304 || status === 404; // default
            },
          },
          true,
        )
          .then(async (respArray) => {
            let headerArray = respArray.map((resp) => resp.headers);
            delegations = delegations.map((delegation, index) => {
              delegation.isRemote = !!headerArray[index]['x-ats-delegation-last-fetched-at'];
              return delegation;
            });

            let dataArray = respArray.map((resp) => (resp.status === 404 ? { signed: { targets: {} } } : resp.data));
            let result;
            const pkgDistributionProcessComplete = (last) => {
              if (last) {
                const packages = _.keyBy(result, 'name');
                commit('packages/setPackages', packages, { root: true });
                // Now that we have the packages, we can fetch the static deltas
                dispatch('packages/getStaticDeltas', {}, { root: true });
                commit('ui/setLoadingPackages', false, { root: true });
                resolve(packages);
              }
            };
            let infoResp = {};
            try {
              infoResp = await ApiService.getResource(API_USER_REPO_ALL_TRUSTED_DELEGATION_INFO);
            } catch (e) {}
            delegations = _.map(delegations, (delegation, index) => {
              const infoData = infoResp[delegation.name] || {};
              delegation.isExpired = new Date(dataArray[index].signed.expires).getTime() < Date.now(); // compare with current time in UTC

              return { ...delegation, ...infoData, label: infoData.friendlyName || delegation.label };
            });
            delegations.unshift(stateDelegations.find((d) => d.value === 'custom'));
            delegations = delegations.map((d) => ({ ...d, name: d.name || d.key }));
            commit('packages/setDelegations', _.cloneDeep(delegations), { root: true });
            dataArray.unshift(targets);
            dataArray = dataArray.map((d) => (typeof d === 'string' ? { signed: { targets: {} } } : d));
            result = delegations.map((d, i) => {
              try {
                d.packages = _preparePackages(context, dataArray[i], _.cloneDeep(d));
                fetchPackagesCountInstalledEcus(context, _.map(d.packages, 'filepath'))
                  .then((data) => {
                    _.each(data, (count, filepath) => {
                      d.packages.find((f) => f.filepath === filepath).installedOnEcus = count;
                    });
                  })
                  .catch((e) => {})
                  .finally(() => {
                    pkgDistributionProcessComplete(i == delegations.length - 1);
                  });
              } catch (err) {
                warn(err);
              }
              return d;
            });
          })
          .catch((err) => {
            logError('Error occured while fetching delegations: ', err);
            reject(err);
          })
          .finally(() => {});
      })
      .catch(reject);
  });
}

export function saveSelectedDelegations({ dispatch, commit, state }, selectedDelegations) {
  commit('packages/setSelectedDelegationSources', selectedDelegations, { root: true });
  return dispatch('ui/setUserOption', { selectedDelegationSources: { ...selectedDelegations } }, { root: true });
}

export function createPackage({ commit, dispatch, state }, { data, formData, hardwareIds }) {
  const packagesUploading = [];
  return new Promise((resolve, reject) => {
    let source = axios.CancelToken.source();
    let length = packagesUploading.push({
      status: null,
      size: 0,
      uploaded: 0,
      progress: 0,
      upSpeed: 0,
      package: {
        name: data.packageName,
        version: data.version,
      },
    });
    const uploadObj = packagesUploading[length - 1];
    uploadObj.startTime = new Date().getTime();

    const config = {
      onUploadProgress: (progressEvent) => {
        let currentTime = new Date().getTime();
        let lastUpTime = uploadObj.lastUpTime || currentTime;
        let upSpeed = ((progressEvent.loaded - uploadObj.uploaded) * 1000) / ((currentTime - lastUpTime) * 1024);
        uploadObj.progress = (progressEvent.loaded * 100) / progressEvent.total;
        uploadObj.size = progressEvent.total;
        uploadObj.uploaded = progressEvent.loaded;
        uploadObj.upSpeed = upSpeed;
        uploadObj.lastUpTime = currentTime;
        const dateDif = currentTime - uploadObj.startTime;
        const timeRem = (uploadObj.size - uploadObj.uploaded) / upSpeed;
        uploadObj.totalTime = {
          d: Math.floor(dateDif / (1000 * 60 * 60 * 24)),
          h: Math.floor(dateDif / (1000 * 60 * 60)),
          m: Math.floor(dateDif / (1000 * 60)),
          s: Math.floor(dateDif / 1000),
        };
        uploadObj.timeRemaining = {
          d: Math.floor(timeRem / (1000 * 60 * 60 * 24)),
          h: Math.floor(timeRem / (1000 * 60 * 60)),
          m: Math.floor(timeRem / (1000 * 60)),
          s: Math.floor(timeRem / 1000),
        };
        commit('packages/setPackagesUploading', packagesUploading, { root: true });
      },
      cancelToken: source.token,
      headers: { 'Content-Type': 'application/octet-stream' },
    };
    const filename = data.packageName + '-' + data.version;
    axios
      .put(_packageURI(filename, data.packageName, data.version, hardwareIds), formData, config)
      .then((response) => {
        uploadObj.status = 'success';
        dispatch('packages/fetchPackages', {}, { root: true });
        resolve(response.data);
      })
      .catch((error) => {
        uploadObj.status = 'error';
        reject(error);
      });
    uploadObj.source = source;
  });
}
export function refreshDelegations({ dispatch, commit, state }, { delegation }) {
  if (typeof delegation === 'string') {
    delegation = state.delegations.find((d) => d.name === delegation);
  }
  return new Promise((resolve, reject) => {
    let promise;
    if (delegation.isRemote) {
      promise = dispatch('packages/refreshRemoteDelegations', { delegation }, { root: true });
    } else {
      promise = dispatch('packages/refreshLocalDelegations', { delegation }, { root: true });
    }
    promise
      .then(() => {
        dispatch('packages/fetchPackages', { silent: true }, { root: true }).finally(() => {
          resolve();
        });
      })
      .catch(reject);
  });
}
export function refreshLocalDelegations({ commit, state }, { delegation }) {
  const getUrl = `${API_PACKAGES_METADATA_DELEGATIONS}/${delegation.name}.json`;
  const putUrl = `${API_PACKAGES_DELEGATIONS}/${delegation.name}.json`;
  return new Promise((resolve, reject) => {
    ApiService.getResource(getUrl)
      .then((gotData) => {
        ApiService.putResource(putUrl, gotData)
          .then((putResp) => {
            console.log('Delegation updated');
            resolve(putResp);
          })
          .catch(reject);
      })
      .catch(reject);
  });
}
export function refreshRemoteDelegations({ commit, state }, { delegation }) {
  const url = `${API_PACKAGES_REFRESH_REMOTE_DELEGATIONS}`.replace('{delegation_name}', delegation.name);
  return new Promise((resolve, reject) => {
    ApiService.putResource(url, delegation)
      .then(resolve)
      .catch(reject);
  });
}
export function getPackageContent({ commit, state }, { filepath }) {
  const url = `${API_PACKAGES_GET_CONTENT}${filepath}`;
  return new Promise((resolve, reject) => {
    const options = {
      validateStatus: function(status) {
        return (status >= 200 && status < 300) || status === 304 || status === 400; // default
      },
    };
    ApiService.getResource(url, options, true)
      .then((response) => {
        ApiService.getResource(response.request.responseURL, {
          bypassAuthIntercept: true,
          headers: {
            'Content-Type': 'application/json',
          },
        })
          .then(resolve)
          .catch(reject);
      })
      .catch((err) => {
        reject(err);
      });
  });
}

export function deletePackage({ dispatch }, packageId) {
  const url = `${API_USER_REPO_TARGETS}${packageId}`;
  return new Promise((resolve, reject) => {
    const options = {
      validateStatus: function(status) {
        return (status >= 200 && status < 300) || status === 304 || status === 400; // default
      },
    };
    ApiService.deleteResource(url, options, true)
      .then((response) => {
        dispatch('fetchPackages');
        resolve(response);
      })
      .catch((err) => {
        reject(err);
      });
  });
}

export function setCompatibility({ dispatch }, { filepath, compatibilities }) {
  return new Promise((resolve, reject) => {
    const url = `${API_USER_REPO_SET_COMPATIBILITY}${filepath}`;
    const options = {
      validateStatus: function(status) {
        return (status >= 200 && status < 300) || status === 304 || status === 400; // default
      },
    };
    ApiService.patchResource(url, { compatibleWith: compatibilities }, options, true)
      .then((response) => {
        // dispatch('fetchPackages');
        resolve(response);
      })
      .catch((err) => {
        reject(err);
      });
  });
}
export function saveDescription({ dispatch }, { packageId, description }) {
  const url = `${API_PACKAGES_CREATE_DESCRIPTION}/${packageId}`;
  return new Promise((resolve, reject) => {
    const options = {
      validateStatus: function(status) {
        return (status >= 200 && status < 300) || status === 304 || status === 400; // default
      },
    };
    ApiService.putResource(url, { comment: description }, options)
      .then((data) => {
        resolve(data);
      })
      .catch((err) => {
        reject(err);
      });
  });
}
export function getDescription({ dispatch }, packageId) {
  const url = `${API_PACKAGES_GET_DESCRIPTIONS}/${packageId}`;
  return new Promise((resolve, reject) => {
    const options = {
      validateStatus: function(status) {
        return (status >= 200 && status < 300) || status === 304 || status === 400; // default
      },
    };
    ApiService.getResource(url, options)
      .then((data) => {
        resolve(data);
      })
      .catch((err) => {
        reject(err);
      });
  });
}
export function getAllDescriptions({ dispatch }) {
  const url = `${API_PACKAGES_GET_DESCRIPTIONS}`;
  return new Promise((resolve, reject) => {
    const options = {
      validateStatus: function(status) {
        return (status >= 200 && status < 300) || status === 304 || status === 400; // default
      },
    };
    ApiService.getResource(url, options)
      .then((data) => {
        resolve(data);
      })
      .catch((err) => {
        reject(err);
      });
  });
}

export function deleteDelegation({ dispatch }, { name }) {
  return new Promise(async (resolve, reject) => {
    ApiService.deleteResource(API_USER_REPO_DELETE_TRUSTED_DELEGATION.replace('{delegation_name}', name))
      .then((a) => {
        dispatch('packages/getTrustedDelegations', {}, { root: true }).finally(() => {
          dispatch('packages/fetchPackages', {}, { root: true }).finally(resolve);
        });
      })
      .catch(reject);
  });
}
export function saveDelegations({ dispatch }, delegations) {
  return new Promise((resolve, reject) => {
    ApiService.putResource(API_USER_REPO_TRUSTED_DELEGATIONS, delegations)
      .then(() => {
        dispatch('packages/getTrustedDelegations', {}, { root: true }).finally(() => {
          dispatch('packages/fetchPackages', {}, { root: true }).finally(resolve);
        });
      })
      .catch(reject);
  });
}
export function getTrustedDelegations({ dispatch, commit }, delegation) {
  return new Promise(async (resolve, reject) => {
    ApiService.getResource(API_USER_REPO_TRUSTED_DELEGATIONS)
      .then((trustedDelegations) => {
        ApiService.getResource(API_USER_REPO_ALL_TRUSTED_DELEGATION_INFO)
          .then((infoObject) => {
            trustedDelegations = trustedDelegations.map((d) => {
              return { ...d, ...infoObject[d.name] };
            });
          })
          .catch((err) => {})
          .finally(() => {
            commit('packages/setTrustedDelegations', trustedDelegations, { root: true });
            resolve();
          });
      })
      .catch(reject);
  });
}
export function getTrustedKeys({ dispatch, commit }, delegation) {
  return new Promise(async (resolve, reject) => {
    ApiService.getResource(API_USER_REPO_TRUSTED_DELEGATIONS_KEYS)
      .then((keys) => {
        const mapped = keys.map((a) => {
          return {
            ...a,
            keyid: calculateKeyId((a.keyval || {}).public, a.keytype),
          };
        });
        resolve(mapped);
      })
      .catch(reject);
  });
}

export async function addTrustedKeys({ dispatch, state }, keys) {
  let keyAdded = false; // flag to check if any key was added
  let trustedKeys = [],
    keyids = []; // variables to store the keys and keyids
  try {
    trustedKeys = await dispatch('packages/getTrustedKeys', {}, { root: true });
  } catch (err) {
    throw { error: err, message: 'Could not verify trusted delegation' };
  }
  keys.forEach((key) => {
    const keyid = key.keyid;
    // Check if key is already added to trusted keys
    if (
      !trustedKeys.find((k) => {
        // We need to check if the keyid is the same, so calculate the keyid from the public key
        const id = calculateKeyId((k.keyval || {}).public, k.keytype);
        return id === keyid;
      })
    ) {
      trustedKeys.push({
        keyval: key.keyval,
        keytype: key.keytype,
      });
      keyAdded = true; // set flag to true since the key was added
    }
    keyids.push(keyid);
  });
  // If the flag is true, then we need to save the keys to the server
  if (keyAdded) {
    try {
      await ApiService.putResource(API_USER_REPO_TRUSTED_DELEGATIONS_KEYS, trustedKeys);
    } catch (e) {
      throw { error: e, message: 'Could not verify trusted delegation' };
    }
  }
  // Return the keyids to the caller
  return keyids;
}
export function addTrustedDelegation({ dispatch, state }, trustedDelegation) {
  // Get trusted delegations
  let trustedDelegations = [...state.trustedDelegations];
  // Add new trusted delegation
  trustedDelegations.push(trustedDelegation);
  // Save updated trusted delegations list
  return new Promise((resolve, reject) => {
    dispatch('packages/saveDelegations', trustedDelegations, { root: true })
      .then((data) => {
        resolve(data);
      })
      .catch(reject);
  });
}
export function setDelegationInfo({ dispatch, state }, delegation) {
  return new Promise((resolve, reject) => {
    ApiService.patchResource(API_USER_REPO_TRUSTED_DELEGATION_INFO.replace('{delegation_name}', delegation.name), {
      friendlyName: delegation.friendlyName,
    })
      .then(resolve)
      .catch((err) => {
        let data = err.response.data;
        reject(data);
      });
  });
}
export function addDelegationMetadata({ dispatch, state }, delegation) {
  return new Promise((resolve, reject) => {
    let promise;
    if (delegation.type === 'url') {
      promise = ApiService.putResource(
        API_USER_REPO_ADD_DELEGATIONS_METADATA_URL.replace('{delegation_name}', delegation.name),
        {
          delegationName: delegation.name,
          uri: delegation.remoteUri,
          friendlyName: delegation.friendlyName,
        },
        ApiService.defaultOptions,
        true,
      );
    } else {
      promise = ApiService.putResource(
        API_USER_REPO_UPLOAD_DELEGATIONS_METADATA.replace('{delegation_name}', delegation.name) + '.json',
        delegation.metadata,
        {
          headers: {
            'Content-Type': 'application/json',
          },
        },
        true,
      );
    }
    promise.then(resolve).catch((err) => {
      let data = err.response.data;
      reject(data);
    });
  });
}
export function getDelegationMetadata({ dispatch, state }, delegation_name) {
  return new Promise((resolve, reject) => {
    return ApiService.getResource(API_USER_REPO_UPLOAD_DELEGATIONS_METADATA.replace('{delegation_name}', delegation_name) + '.json')
      .then(resolve)
      .catch(reject);
  });
}
export function updateDelegation({ dispatch, state }, delegation) {
  return new Promise(async (resolve, reject) => {
    // Declare variables and get stored trusted delegations
    let keyids = [],
      trustedDelegations = state.trustedDelegations;

    // Check if the delegation is already in the list, we can only update it if it is
    if (!trustedDelegations.find((d) => d.name === delegation.name)) {
      return reject({ message: 'Delegation with that name does not exist' });
    }

    // Add the keys to the list of trusted keys, the method will return the keyids that were added
    dispatch('packages/addTrustedKeys', delegation.keys, { root: true })
      .then((_keyids) => {
        keyids = _keyids;
        let trustedDelegation = trustedDelegations.find((d) => d.name === delegation.name);
        trustedDelegation = {
          ...(trustedDelegation || {}),
          // We want to make sure that the keys are unique, so we don't need to add them again
          keyids: _.uniq(trustedDelegation.keyids.concat(keyids)),
          paths: delegation.paths,
          threshold: delegation.threshold,
          terminating: true,
        };
        // Replace the old delegation with the updated one
        trustedDelegations.splice(trustedDelegations.findIndex((a) => a.name === delegation.name), 1, trustedDelegation);

        // Save the updated list of trusted delegations
        dispatch('packages/saveDelegations', trustedDelegations, { root: true })
          .then(() => {
            dispatch('packages/addDelegationMetadata', delegation, { root: true })
              .then(resolve)
              .catch((err) => {
                const message = err.description || err;
                reject({
                  message: 'Delegation changes saved but could not save metadata: ' + message,
                });
              });
          })
          .catch(reject);
      })
      .catch(reject);
    // }
  });
}
export function addDelegation({ dispatch, state }, delegation) {
  return new Promise(async (resolve, reject) => {
    // Decalre variables and get pre-fetched trusted delegations from store
    let keyids = [],
      trustedDelegations = state.trustedDelegations;
    // We can't have duplicate delegations (same name)
    if (trustedDelegations.find((d) => d.name === delegation.name)) {
      return reject({ message: 'Delegation with that name already exists' });
    }

    // Add trusted keys and get their keyids
    dispatch('packages/addTrustedKeys', delegation.keys, { root: true })
      .then((_keyids) => {
        keyids = _keyids;
        // Add new trusted delegation
        dispatch(
          'packages/addTrustedDelegation',
          {
            name: delegation.name,
            keyids,
            paths: delegation.paths,
            threshold: delegation.threshold,
            terminating: true,
          },
          { root: true },
        )
          .then(() => {
            // Add metadata to new trusted delegation
            dispatch('packages/addDelegationMetadata', delegation, { root: true })
              .then(resolve)
              .catch((err) => {
                // At this point we have a new trusted delegation, but we couldn't save the metadata
                const message = err.description || err;
                reject({
                  partiallyCreated: true,
                  message: 'Delegation added but could not add metadata: ' + message + '. You can try adding the metadata again by modifying this delegation from the list.',
                });
              })
              .finally(() => {
                // refresh the list of trusted delegations
                dispatch('packages/getTrustedDelegations', {}, { root: true }).catch(() => {});
                dispatch('packages/fetchPackages', { root: true }).catch(() => {});
              });
          })
          .catch((err2) => {
            reject(err2);
          });
      })
      .catch(reject);
    // }
  });
}

/**
 * Get static delta for all packages in the user namespace
 * @param {Object} context
 */
export function getStaticDeltas(context) {
  const { commit } = context;
  return new Promise((resolve, reject) => {
    ApiService.getResource(API_PACKAGES_STATIC_DELTAS)
      .then((data) => {
        /* This part of the code expects the reponse `data` has the following format:
         {
             limit: 10,
              offset: 0,
              total: 3,
              values: [
                {
                  to: 'package-hash-1',
                  from: 'package-hash-2',
                  size: 29329,
                },
                {
                  to: 'package-hash-3',
                  from: 'package-hash-1',
                  size: 29329,
                },
                ...
              ]
          }
          The following code is to convert the above format to the format that is expected by the store.
          which is an object with the following format:
          {
            'package-hash-1': {
              from: [
                {
                  hash: 'package-hash-2',
                  size: 29329,
                },
                ...
              ],
              to: [
                {
                  hash: 'package-hash-3',
                  size: 29329,
                },
                ...
              ]
            },
            'package-hash-2': {
              from: [
                {
                  hash: 'package-hash-1',
                  size: 29329,
                },
                ...
              ]
            },
            'package-hash-3': {
              to: [
                {
                  hash: 'package-hash-1',
                  size: 29329,
                },
                ...
              ]
            },
            ...
            },

          */
        const staticDeltas = {};
        data.values.forEach((delta) => {
          staticDeltas[delta.from] = staticDeltas[delta.from] || { from: [], to: [] };
          staticDeltas[delta.from].from.push({ hash: delta.to, size: delta.size });
          staticDeltas[delta.to] = staticDeltas[delta.to] || { from: [], to: [] };
          staticDeltas[delta.to].to.push({ hash: delta.from, size: delta.size });
        });

        commit('packages/setDeltas', staticDeltas, { root: true });
        resolve(staticDeltas);
      })
      .catch((e) => {
        reject(e);
      });
  });
}
