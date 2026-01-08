<template>
  <div>
    <fleets-for-device> </fleets-for-device>
  </div>
</template>

<script>
import FleetsForDevice from '../components/fleets/FleetsForDevice';

export default {
  name: 'FleetManagerPage',
  components: {
    FleetsForDevice,
  },
  data() {
    return {};
  },
  watch: {},
  computed: {
    pageTitle: {
      get() {
        return this.$store.getters['ui/currentPageTitle'];
      },
      set(val) {
        return this.$store.commit('ui/setCurrentPageTitle', val);
      },
    },
  },
  mounted() {
    this.pageTitle = 'Fleet Manager';
  },
  created() {},
};
</script>
