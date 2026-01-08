export function fleets(state) {
  return state.fleets;
}
export function fleetsById(state) {
  return _.keyBy(state.fleets, 'id');
}
export function preparedFleets(state) {
  return state.preparedFleets;
}
export function selectedFleet(state) {
  return state.selectedFleet;
}

export function visibleColumns(state) {
  return state.visibleColumns;
}
export function columns(state) {
  return state.columns;
}
