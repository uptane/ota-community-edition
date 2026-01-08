export function packagesUploading(state) {
  return state.packagesUploading;
}
export function packages(state) {
  return state.packages;
}

export function packagesById(state) {
  return _.keyBy(packagesInAllSources(state), 'filepath');
}
export function packagesByIdAndHash(state) {
  return _.keyBy(packagesInAllSources(state), 'filepathAndHash');
}
export function packagesByHash(state) {
  return _.keyBy(packagesInAllSources(state), 'hash');
}
export function torizonPackagesByHash(state) {
  return _.keyBy(_.filter(packagesInAllSources(state), (f) => f.source !== 'user'), 'hash');
}
export function userPackagesByHash(state) {
  return _.keyBy(_.filter(packagesInAllSources(state), (f) => f.source === 'user'), 'hash');
}

export function delegations(state) {
  return state.delegations;
}
export function packageSourceOptions(state) {
  return _.map(state.delegations, (m) => ({ ...m, val: m.name || m.value, label: m.label || m.name }));
}
export function selectedDelegationSources(state, getters, rootState) {
  return state.selectedDelegationSources;
}
export function defaultSelectedDelegationSources(state, getters, rootState) {
  return state.defaultSelectedDelegationSources;
}
export function selectedDelegations(state, getters, rootState) {
  return selectedDelegationSources(state, getters, rootState)
    .map((d) => {
      const delegation = state.packages[d];
      return delegation;
    })
    .filter((d) => d);
}

export function packageGroupsInSelectedSources(state, getters, rootState) {
  const grouped = _.groupBy(packagesInSelectedSources(state, getters, rootState), 'rawName');
  return _.map(grouped, (v, k) => {
    const parent = _.cloneDeep(_.first(v));
    parent.versions = v;
    return parent;
  });
}
export function packageGroupsInAllSources(state, getters, rootState) {
  const grouped = _.groupBy(packagesInAllSources(state, getters, rootState), 'rawName');
  return _.map(grouped, (v, k) => {
    const versions = _.sortBy(v, 'createdAt').reverse();
    const parent = _.cloneDeep(_.first(versions));
    parent.versions = versions;
    return parent;
  });
}

export function packagesInAllSources(state, getters, rootState) {
  const packs = _.reduce(
    state.packages,
    (acc, d) => {
      if (!_.isEmpty(d.packages)) {
        acc = [...acc, ...d.packages];
      }
      return acc;
    },
    [],
  );
  return packs;
}
export function packagesInSelectedSources(state, getters, rootState) {
  const delegations = selectedDelegations(state, getters, rootState);
  const selectedPackages = _.reduce(
    delegations,
    (acc, d) => {
      if (!_.isEmpty(d.packages)) {
        acc = [...acc, ...d.packages];
      }
      return acc;
    },
    [],
  );
  return selectedPackages;
}
export function trustedDelegations(state, getters, rootState) {
  return state.trustedDelegations;
}

export function deltas(state) {
  return state.deltas;
}
