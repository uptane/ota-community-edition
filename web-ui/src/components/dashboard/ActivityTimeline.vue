<template>
  <q-card class="p-1">
    <div class="row w-100 mb-2">
      <div class="col">
        <div class="m-0 text-1 text-weight-normal">Fleet Activity Timeline</div>
      </div>
      <div class="col-auto">
        <q-btn dense flat no-caps color="secondary">View all devices</q-btn>
      </div>
    </div>
    <q-timeline layout="loose" color="muted" class="p-1">
      <q-timeline-entry subtitle="February 22, 2021" icon="edit" side="left">
        <div class="opacity-50">Fleet was renamed to <em>Superfleet</em></div>
      </q-timeline-entry>
      <q-timeline-entry subtitle="February 22, 2021" icon="publish" side="right">
        <div>
          Update initiated for the following package:
          <div class="row w-70">
            <div class="col-auto">
              <package-icon size="1.5rem" style="padding-right: .2rem;"></package-icon>
            </div>
            <div class="col">
              <div class="ellipsis opacity-50">dunfell/apalis-imx6/torizon-upstream-rt/torizon-core-podman/nightly</div>
              <div style="padding-top: .0rem;" class="text-caption ellipsis">
                <package-version-icon size="0.80rem"></package-version-icon>
                <span class="opacity-50">5.2.0-devel-20210307+build.239 (Nightly)</span>
              </div>
            </div>
          </div>
        </div>
      </q-timeline-entry>

      <q-timeline-entry subtitle="February 21, 2021" icon="playlist_add" side="left">
        <div class="opacity-50">
          12 devices were asssigned
        </div>
      </q-timeline-entry>
      <q-timeline-entry subtitle="February 21, 2021" icon="layers" side="right">
        <div class="opacity-50">
          Fleet was created
        </div>
      </q-timeline-entry>
    </q-timeline>
  </q-card>
</template>

<script>
import PackageIcon from '../packages/PackageIcon.vue';
import PackageVersionIcon from '../packages/PackageVersionIcon.vue';
export default {
  components: { PackageVersionIcon, PackageIcon },
  name: 'ActivityTimeline',
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
  computed: {
    layout() {
      return this.$q.screen.lt.sm ? 'dense' : this.$q.screen.lt.md ? 'comfortable' : 'loose';
    },
  },
};
</script>
