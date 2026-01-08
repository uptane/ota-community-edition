<template>
  <q-card class="p-1">
    <div class="row w-100 mb-2">
      <div class="col">
        <h6 class="m-0">Device Status Overview</h6>
      </div>
      <div class="col-auto">
        <q-btn dense flat no-caps color="secondary">View all devices</q-btn>
      </div>
    </div>
    <div :key="status.name" v-for="status in statusGroups" class="row  h-divide-top" style="padding: 0.25rem">
      <div class="col">{{ status.label }}</div>
      <div class="col-auto">
        <q-badge class="pl-1 pr-1 text-1 flex flex-center text-center" style="padding-top: .3rem; padding-bottom: .3rem; width: 3rem; " :color="status.color">{{ status.count }}</q-badge>
      </div>
    </div>
  </q-card>
</template>

<script>
export default {
  name: 'DeviceStatusOverview',
  data() {
    return {
      statusGroups: [
        {
          name: 'active',
          label: 'Active',
          count: 20,
          color: 'positive',
        },
        {
          name: 'inactive',
          label: 'Not activated',
          count: 5,
          color: 'blue-grey-9',
        },
        {
          name: 'update-pending',
          label: 'Update pending',
          count: 4,
          color: 'info',
        },
        {
          name: 'updating',
          label: 'Updating',
          count: 10,
          color: 'secondary',
        },
        {
          name: 'offline',
          label: 'Offline',
          count: 5,
          color: 'negative',
        },
        {
          name: 'uptodate',
          label: 'Up to date',
          count: 3,
          color: 'positive',
        },
        {
          name: 'update-failed',
          label: 'Update Failed',
          count: 1,
          color: 'negative',
        },
      ],
    };
  },
};
</script>
