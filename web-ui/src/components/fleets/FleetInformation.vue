<template>
  <div>
    <fleet-devices ref="fleetDevices" expanded :fleet="fleet"></fleet-devices>
  </div>
</template>

<script>
import FleetDevices from './FleetDevices.vue';

export default {
  components: { FleetDevices },
  name: 'FleetInformation',
  props: {
    fleet: {
      type: Object,
      required: true,
    },
    expanded: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      filter: '',
      loading: true,
    };
  },
  computed: {},
  methods: {
    refresh() {
      this.$refs.fleetDevices && this.$refs.fleetDevices.refresh();
    },
  },
};
</script>

<style></style>
