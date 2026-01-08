<template>
  <span>
    <span v-if="deviceFleets && deviceFleets.length">
      <span class="faded">Current Fleets:&nbsp;</span>
      <template v-for="(deviceFleet, index) in deviceFleets">
        <span :key="deviceFleet.id"
          >{{ deviceFleet.groupName }}<span class="faded">{{ notLastItem(index) ? ' | ' : '' }}</span></span
        >
      </template>
    </span>
    <span v-else class="faded">Not in any fleet&nbsp;</span>
  </span>
</template>

<script>
import { mapGetters } from 'vuex';
export default {
  name: 'CurrentDeviceFleets',
  props: {
    device: {
      type: Object,
      required: true,
    },
  },
  computed: {
    ...mapGetters({
      fleets: 'fleets/fleets',
    }),
    deviceFleets() {
      return this.fleets.filter((f) => f.deviceIds.includes(this.device.uuid));
    },
  },
  methods: {
    notLastItem(index) {
      return index < this.deviceFleets.length - 1;
    },
  },
};
</script>

<style></style>
