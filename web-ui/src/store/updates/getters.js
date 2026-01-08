export function updates(state) {
  return state.updates;
}
export function updatesWithPackages(state, getters, rootState, rootGetters) {
  const packagesByHash = rootGetters['packages/packagesByHash'];
  return state.updates.map((m) => {
    return {
      ...m,
      packages: _.sortBy(
        _.map(m.packages.targets, (target, name) => {
          const hash = target.hashes.sha256;
          const pack = packagesByHash[hash];
          return {
            name,
            hashes: target.hashes,
            hash,
            ...pack,
          };
        }),
        ['isOSPackage', 'name'],
      ).reverse(),
    };
  });
}
export function visibleColumns(state) {
  return state.visibleColumns;
}
export function columns(state) {
  return state.columns;
}
