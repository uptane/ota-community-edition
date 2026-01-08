<template>
  <div>
    <div v-if="loading" class="text-center text-1"><q-spinner-hourglass size="2em" color="secondary"></q-spinner-hourglass> Fetching fleet devices ...</div>
    <template v-else>
      <template v-if="fleetDevices && fleetDevices.length">
        <h6 class="m-0 p-0 row">
          <span class="col-auto mr-1">Devices </span>
          <!-- {{fleet.groupName}} <span class="faded">fleet </span> -->
          <span class=" col">
            <filter-input v-model="filter" dense placeholder="Filter devices" icon="search" class="mb-1 mr-1"></filter-input>
          </span>
        </h6>
        <div class="row">
          <div
            class=" pr-1 pb-1 mnw-20em"
            :class="{
              'col-xs-12 col-sm-12 col-md-4 col-lg-3 col-xl-3 pr-1 pb-1': expanded,
              'col-xs-12 col-sm-12 col-md-6 col-lg-4 col-xl-4 pr-1 pb-1': !expanded,
            }"
            v-for="device in fleetDevices"
            :key="device.id"
          >
            <device-item class="shadow-24" :device="device" :fleet="fleet" :show-actions="true" @item-click="showDeviceDatail"></device-item>
          </div>
        </div>
      </template>
      <empty v-else title="No device in this fleet yet" message="Add devices to this fleet to see them here" :actionText="'Add devices'" @on-action="showDeviceManager" :noIcon="true" :actionIcon="'playlist_add'"></empty>
    </template>
  </div>
</template>

<script>
import { mapGetters, mapActions } from 'vuex';
import FilterInput from 'src/components/common/FilterInput.vue';
import DeviceItem from 'src/components/devices/DeviceItem.vue';
import Empty from '../common/Empty.vue';

export default {
  components: {
    DeviceItem,
    FilterInput,
    Empty,
  },
  name: 'FleetDevices',
  props: {
    fleet: {
      type: Object,
      required: true,
      default: () => ({}),
    },
    expanded: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      filter: '',
      loading: false,
    };
  },
  computed: {
    ...mapGetters({
      fleets: 'fleets/fleets',
    }),
    allFleetDevices() {
      return (this.fleet || {}).devices || [];
    },
    fleetDevices() {
      try {
        return this.allFleetDevices.filter((device) => {
          if (!this.filter) return true;
          if (!device) return false;
          const regex = new RegExp(`${this.filter}`, 'gi');
          return !this.filter || JSON.stringify(device).match(regex);
        });
      } catch (e) {
        return [];
      }
    },
    devices() {
      return this.fleet.devices;
    },
  },
  methods: {
    ...mapActions({
      fetchFleetDevices: 'fleets/fetchFleetDevices',
    }),
    showDeviceManager() {
      this.$events.$emit('dialogs:fleet-devices-manager:open', { device: {}, mode: 'fleet', fleet: this.fleet });
    },
    showDeviceDatail(device) {
      this.$router.push({
        name: 'device-detail',
        params: { deviceId: device.uuid },
      });
    },
    fetchDevices() {
      this.loading = true;
      this.fetchFleetDevices({ fleetId: this.fleet.id })
        .then((devices) => {
          this.fleet.devices.values = devices;
        })
        .catch((e) => {})
        .finally(() => {
          this.loading = false;
        });
    },
    refresh() {
      this.fetchDevices();
    },
  },
  mounted() {},
};
</script>

<style></style>
