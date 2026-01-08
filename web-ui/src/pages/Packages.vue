<template>
  <q-page class="packages-page" style="overflow-x: hidden;">
    <packages viewType="table"></packages>
  </q-page>
</template>

<script>
import Packages from '../components/packages/Packages';

export default {
  name: 'PagePackages',
  components: {
    Packages,
  },
  data() {
    return {};
  },
  created() {},
  mounted() {
    this.pageTitle = 'Packages';
  },
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
  methods: {},
};
</script>
