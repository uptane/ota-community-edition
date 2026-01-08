<template>
  <q-layout class="fleet-detail-page">
    <q-page-container class>
      <q-page>
        <q-tab-panels v-model="currentTab" class=" bg-transparent" animated>
          <q-tab-panel class="p-0 m-0" name="fleet-information">
            <fleet-detail :fleet="fleet"></fleet-detail>
          </q-tab-panel>
          <q-tab-panel class="p-0 m-0" name="fleet-overview">
            <fleet-overview :fleet="fleet"></fleet-overview>
          </q-tab-panel>
        </q-tab-panels>
      </q-page>
    </q-page-container>
  </q-layout>
</template>

<script>
import { mapActions, mapGetters, mapMutations } from 'vuex';
import FleetDetail from '../components/fleets/FleetDetail';
import FleetOverview from '../components/fleets/FleetOverview.vue';
import Loader from '../components/loaders/Loader';

export default {
  name: 'PageFleetDetail',
  components: {
    Loader,
    FleetDetail,
    FleetOverview,
  },
  data() {
    return {
      fleet: {},
      loading: true,
    };
  },
  created() {},
  beforeDestroy() {
    this.setCurrentTab('fleet-information');
    this.setTabs(null);
  },
  mounted() {
    this.pageTitle = 'Fleet Information';
    this.getFleet();
    this.$events.$on('fleets:refresh', () => {
      this.getFleet();
    });
    this.setCurrentTab('fleet-information');
    this.setTabs([
      {
        name: 'fleet-information',
        label: 'Fleet Information',
        icon: 'info',
      },
      {
        name: 'fleet-overview',
        label: 'Fleet Overview',
        icon: 'pie_chart',
      },
    ]);
  },
  computed: {
    ...mapGetters({
      tabs: 'ui/tabs',
      currentTab: 'ui/currentTab',
    }),
    adminMode: {
      get() {
        return this.$store.getters['ui/adminMode'];
      },
      set(v) {
        this.$store.commit('ui/setAdminMode', v);
      },
    },
    pageTitle: {
      get() {
        return this.$store.getters['ui/currentPageTitle'];
      },
      set(val) {
        return this.$store.commit('ui/setCurrentPageTitle', val);
      },
    },
    fleetId() {
      return this.$route.params.fleetId;
    },
  },
  methods: {
    ...mapActions({
      fetchFleet: 'fleets/fetchFleet',
    }),
    ...mapMutations({
      setTabs: 'ui/setTabs',
      setCurrentTab: 'ui/setCurrentTab',
    }),
    getFleet() {
      this.loading = true;
      this.fetchFleet({ id: this.fleetId, forceFetch: true })
        .then((fleet) => {
          this.fleet = fleet;
        })
        .catch((err) => {})
        .finally(() => {
          this.loading = false;
        });
    },
  },
};
</script>
