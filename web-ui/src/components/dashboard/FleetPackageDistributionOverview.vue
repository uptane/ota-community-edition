<template>
  <q-card class="p-1">
    <div class="row w-100 mb-2">
      <div class="col">
        <h6 class="m-0">Fleet Package Distribution Overview</h6>
      </div>
      <div class="col-auto">
        <q-btn dense flat no-caps color="secondary">View all devices</q-btn>
      </div>
    </div>
    <div class="row pb-1">
      <div class="col">Packages</div>
      <div class="col-auto"># of Devices</div>
    </div>
    <div :key="status.name" v-for="status in statusGroups" class="row h-divide-top" style="padding: .3rem 0;">
      <div class="col ">
        <div class="row w-90">
          <div class="col-auto">
            <package-icon size="1.5rem" style="padding-right: .2rem;"></package-icon>
          </div>
          <div class="col">
            <div class="ellipsis">dunfell/apalis-imx6/torizon-upstream-rt/torizon-core-podman/nightly</div>
            <div style="padding-top: .0rem;" class="text-caption ellipsis">
              <package-version-icon size="0.80rem"></package-version-icon>
              <span class="opacity-50">5.2.0-devel-20210307+build.239 (Nightly)</span>
            </div>
          </div>
        </div>
      </div>
      <div class="col-auto flex  flex-center">
        <q-badge class="pl-1 pr-1 text-1  flex flex-center" style="padding-top: .3rem; padding-bottom: .3rem; width: 3rem" :color="$q.dark.isActive ? 'blue-grey-8' : 'grey-4'" :text-color="$q.dark.isActive ? 'white' : 'black'">{{ status.count + '' }}</q-badge>
      </div>
    </div>
  </q-card>
</template>

<script>
import PackageIcon from '../packages/PackageIcon.vue';
import PackageVersionIcon from '../packages/PackageVersionIcon.vue';
export default {
  components: { PackageIcon, PackageVersionIcon },
  name: 'FleetPackageDistributionOverview',
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
