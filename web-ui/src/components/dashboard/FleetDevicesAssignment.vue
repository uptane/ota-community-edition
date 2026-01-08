<template>
  <q-card class="p-1">
    <div class="row w-100 mb-2">
      <div class="col">
        <h6 class="m-0">Fleet Devices Assignment</h6>
      </div>
      <div class="col-auto">
        <q-btn dense flat no-caps color="secondary">View all devices</q-btn>
      </div>
    </div>
    <div class="row">
      <div class="col text-center hoverable">
        <div class="text-positive text-h1 m-0">32</div>
        <div class="text-2">Assigned</div>
      </div>
      <div class="col text-center hoverable">
        <div class="text-negative text-h1 m-0">5</div>
        <div class="text-2">Unassigned</div>
      </div>
    </div>
    <div class="text-center mt-1 pt-1 h-divide-top">
      <q-btn flat color="secondary">Manage fleets</q-btn> |
      <q-btn flat color="secondary">View all devices</q-btn>
    </div>
  </q-card>
</template>

<script>
export default {
  name: 'FleetDevicesAssignment',
  data() {
    return {
      statusGroups: [],
    };
  },
};
</script>
