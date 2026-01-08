<template>
  <div style=" mxw-100">
    <h5 v-if="fleet" class="text-center pr-0 m-0"><span class="faded text-ellipsis">Devices not in </span>{{ fleet.groupName }}</h5>
    <h5 v-else class="text-center pr-0 m-0">Devices without a fleet</h5>

    <filter-input class="m-1" v-model="filter" placeholder="Filter devices"></filter-input>
    <device-item :key="index + '__' + device.uuid + '__' + device.deviceId" :device="device" v-for="(device, index) in otherDevices"></device-item>
  </div>
</template>

<script>
import { mapGetters } from 'vuex';
import FilterInput from '../common/FilterInput.vue';
import DeviceItem from '../devices/DeviceItem.vue';
export default {
  name: 'FleetOtherDevices',
  components: { DeviceItem, FilterInput },
  props: {
    fleet: {
      type: Object,
      default: () => null,
    },
  },
  data() {
    return {
      filter: '',
    };
  },
  computed: {
    ...mapGetters({
      devices: 'devices/devices',
      fleets: 'fleets/fleets',
      devicesByUuid: 'devices/devicesByUuid',
    }),
    otherDevices() {
      let otherDevices = [];
      if (this.fleet) {
        otherDevices = this.devices.filter((device) => {
          return !this.fleet.deviceIds.includes(device.uuid);
        });
      } else {
        otherDevices = this.devices.filter((device) => {
          return !this.fleets
            .map((fleet) => fleet.deviceIds)
            .flat()
            .includes(device.uuid);
        });
      }

      return otherDevices.filter((device) => {
        return !!this.filter || JSON.stringify(device).match(new RegExp(this.filter, 'i'));
      });
    },
  },
};
</script>

<style></style>
