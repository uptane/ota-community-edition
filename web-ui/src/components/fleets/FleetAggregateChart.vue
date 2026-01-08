<template>
  <div class>
    <div v-if="loading">
      <div class="p-1 flex flex-center mnh-100vh">
        <div class="text-center">
          <div>
            <span class="opacity-90 pr-1">Loading fleet overview...</span>
            <loader class flat color="secondary" />
          </div>
        </div>
      </div>
    </div>
    <div v-if="!loading">
      <div class="row">
        <div class="col-12" v-if="!(selectedPackage && $q.screen.lt.md)">
          <div
            class="q-card row"
            :class="{
              'p-2 ': $q.screen.lt.sm,
              'p-1 ': $q.screen.gt.xs,
            }"
            style="overflow: hidden"
          >
            <div class="col-12 mt-1 h-divide-top">
              <div v-if="fleet && fleet.devices && fleet.devices.length > 0" class="row ">
                <div class="h-divide-bottom v-divide-right col-xs-12 col-sm-6 -col-md-4 col-lg-4 col-xl-3">
                  <fleet-os-packages :fleet="fleet" :showIn3D="showChartsIn3d" :useDonuts="useDonuts" title="OS Packages Distribution" ref="chartRefs.osPackages"></fleet-os-packages>
                </div>
                <div class="h-divide-bottom v-divide-right col-xs-12 col-sm-6 -col-md-4 col-lg-4 col-xl-3">
                  <fleet-os-packages secondary :fleet="fleet" :showIn3D="showChartsIn3d" :useDonuts="useDonuts" title="Application Packages Distribution" ref="chartRefs.appPackages"></fleet-os-packages>
                </div>
                <div class="h-divide-bottom v-divide-right col-xs-12 col-sm-6 -col-md-4 col-lg-4 col-xl-3">
                  <fleet-last-seen-overview secondary :fleet="fleet" :showIn3D="showChartsIn3d" ref="chartRefs.lastSeen"></fleet-last-seen-overview>
                </div>
                <div class="h-divide-bottom v-divide-right col-xs-12 col-sm-6 -col-md-4 col-lg-4 col-xl-3">
                  <fleet-update-status-overview :fleet="fleet" :showIn3D="showChartsIn3d" ref="chartRefs.updateStatus" :useDonuts="useDonuts"></fleet-update-status-overview>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { mapActions, mapGetters } from 'vuex';
import { dom, extend, date } from 'quasar';
import * as am4core from '@amcharts/amcharts4/core';

import am4themes_animated from '@amcharts/amcharts4/themes/animated';
import am4themes_dark from '@amcharts/amcharts4/themes/dark';
import am4themes_material from '@amcharts/amcharts4/themes/material';
import am4themes_frozen from '@amcharts/amcharts4/themes/frozen';
import am4themes_dataviz from '@amcharts/amcharts4/themes/dataviz';
import am4themes_kelly from '@amcharts/amcharts4/themes/kelly';
import am4themes_spiritedaway from '@amcharts/amcharts4/themes/spiritedaway';
import am4themes_moonrisekingdom from '@amcharts/amcharts4/themes/moonrisekingdom';

import Loader from 'src/components/loaders/Loader';
import ReloadBtn from 'src/components/common/ReloadBtn';
import Tooltip from 'src/components/common/Tooltip.vue';
import FleetInformation from './FleetInformation.vue';
import FleetActions from './FleetActions.vue';

import FleetChart from './FleetChart.vue';
import DateTimeRangePicker from 'src/components/common/DateTimeRangePicker.vue';
import ChartControls from 'src/components/common/ChartControls.vue';
import FleetOsPackages from './FleetOsPackages.vue';
import FleetLastSeenOverview from './FleetLastSeenOverview.vue';
import FleetUpdateStatusOverview from './FleetUpdateStatusOverview.vue';

const { height, width } = dom;
const { formatDate } = date;
export default {
  name: 'FleetDetail',
  components: {
    Loader,
    ReloadBtn,
    Tooltip,
    FleetInformation,
    FleetActions,
    FleetChart,
    DateTimeRangePicker,
    ChartControls,
    FleetOsPackages,
    FleetLastSeenOverview,
    FleetUpdateStatusOverview,
  },
  props: {
    fleet: {
      type: Object,
      default: () => {
        return {};
      },
    },
    chartTheme: {
      type: String,
      default: 'dark',
    },
    useDonuts: {
      type: Boolean,
      default: true,
    },
    showChartsIn3d: {
      type: Boolean,
      default: true,
    },
  },
  data() {
    return {
      loading: false,
      loadingUpdatedData: false,
      loadingPackages: false,
      loadingFleetData: false,
      largeHistoryView: true,
      updateInitiated: false,
      showPackageVersions: false,
      parsedPackageVersions: {},
      giveAttensionToVersionsView: false,
      selectedPackage: null,
      _selectedChartTheme: 'default',
      _animateChart: true,
      _fillChartLines: true,
      _autoUpdateChart: true,
      _autoUpdateFreq: 60 * 1000,
      _highlightNoDataRegion: true,
      _dateRangePreset: 60 * 60 * 1000,
      dateRangePresetOptions: [{ label: 'Last hour', value: 60 * 60 * 1000 }, { label: 'Last 4 hours', value: 4 * 60 * 60 * 1000 }, { label: 'One day', value: 24 * 60 * 60 * 1000 }, { label: 'One week', value: 7 * 24 * 60 * 60 * 1000 }],
      dateFrom: formatDate(new Date(Date.now() - 4 * 60 * 60 * 1000), 'YYYY-MM-DD HH:mm:ss'),
      dateTo: formatDate(new Date(), 'YYYY-MM-DD HH:mm:ss'),
      chartOptionsVisible: {
        fillChartLines: false,
        animateChart: false,
        autoUpdateChart: false,
        highlightNoDataRegion: false,
        chartTheme: true,
        dateRangeSelector: false,
        showChartsIn3d: true,
        useDonuts: true,
      },
      chartThemes: {
        dark: am4themes_dark,
        material: am4themes_material,
        frozen: am4themes_frozen,
        dataviz: am4themes_dataviz,
        kelly: am4themes_kelly,
        spiritedaway: am4themes_spiritedaway,
        moonrisekingdom: am4themes_moonrisekingdom,
      },
    };
  },
  created() {},
  mounted() {
    this.setup();
  },
  beforeDestroy() {},
  computed: {
    ...mapGetters({
      packages: 'packages/packages',
      fleets: 'fleets/fleets',
      fleetsById: 'fleets/fleetsById',
      userSettings: 'ui/userSettings',
    }),

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
    adminMode: {
      get() {
        return this.$store.getters['ui/adminMode'];
      },
      set(v) {
        this.$store.commit('ui/setAdminMode', v);
      },
    },
    installedImageData: {
      get() {
        return ((this.fleet.updates || []).find((f) => f.active) || {}).custom || {};
      },
    },
    selectedChartTheme: {
      get() {
        return this.$data._selectedChartTheme;
      },
      set(v) {
        this.$data._selectedChartTheme = v;
        this.updateUserOption('selectedChartTheme', v);
      },
    },
    animateChart: {
      get() {
        return this.$data._animateChart;
      },
      set(v) {
        this.$data._animateChart = v;
        this.updateUserOption('animateChart', v);
      },
    },
    fillChartLines: {
      get() {
        return this.$data._fillChartLines;
      },
      set(v) {
        this.$data._fillChartLines = v;
        this.updateUserOption('fillChartLines', v);
      },
    },
    autoUpdateChart: {
      get() {
        return this.$data._autoUpdateChart;
      },
      set(v) {
        this.$data._autoUpdateChart = v;
        this.updateUserOption('autoUpdateChart', v);
      },
    },
    autoUpdateFreq: {
      get() {
        return this.$data._autoUpdateFreq;
      },
      set(v) {
        this.$data._autoUpdateFreq = v;
        this.updateUserOption('autoUpdateFreq', v);
      },
    },
    highlightNoDataRegion: {
      get() {
        return this.$data._highlightNoDataRegion;
      },
      set(v) {
        this.$data._highlightNoDataRegion = v;
        this.updateUserOption('highlightNoDataRegion', v);
      },
    },
    dateRangePreset: {
      get() {
        return this.$data._dateRangePreset;
      },
      set(v) {
        this.$data._dateRangePreset = v;
        this.updateUserOption('dateRangePreset', v);
      },
    },
    isCustomDateRange() {
      return this.dateRangePresetOptions.map((a) => a.value).indexOf(this.dateRangePreset) == -1;
    },
    darkMode() {
      return this.$q.dark.isActive;
    },
  },
  beforeDestroy() {},

  created() {},
  methods: {
    ...mapActions({
      fetchFleet: 'fleets/fetchFleet',
      getPreviousFleet: 'fleets/getPreviousFleet',
      getNextFleet: 'fleets/getNextFleet',
      fetchPackages: 'packages/fetchPackages',
      saveUserSettings: 'ui/saveUserSettings',
    }),
    height,
    updateUserOption(optionKey, value) {
      this.saveUserSettings({ [optionKey]: value });
    },
    showEditDialog() {
      this.$events.$emit(`dialogs:rename-fleet:open`, {
        show: true,
        fleet: this.fleet || {},
      });
    },
    setup() {
      this.pageTitle = 'Fleet Information';
      this.getFleet();

      this.$events.$on('fleets:refresh', () => {
        this.refresh();
      });
      this.$data._selectedChartTheme = this.getSavedSettingOrDefault('selectedChartTheme', 'default');
      this.$data._animateChart = this.getSavedSettingOrDefault('animateChart', true);
      this.$data._fillChartLines = this.getSavedSettingOrDefault('animateChart', true);
      this.$data._autoUpdateChart = this.getSavedSettingOrDefault('autoUpdateChart', true);
      this.$data._autoUpdateFreq = this.getSavedSettingOrDefault('autoUpdateFreq', 60 * 1000);
      this.$data._highlightNoDataRegion = this.getSavedSettingOrDefault('highlightNoDataRegion', true);
      this.$data._dateRangePreset = this.getSavedSettingOrDefault('dateRangePreset', 24 * 60 * 60 * 1000);
      this.refresh();
    },
    getSavedSettingOrDefault(key, defaultValue) {
      return typeof this.userSettings[key] !== 'undefined' ? this.userSettings[key] : defaultValue;
    },
    refresh() {
      this.setChartTheme();
      this.getFleet();
      _.each(this.$refs || {}, (chart) => {
        if (chart.refresh && typeof chart.refresh === 'function') chart.refresh();
      });
      return Promise.resolve();
    },
    toggleSelectedPackage(update) {
      if (this.selectedPackage && this.selectedPackage.name === update.name) {
        this.selectedPackage = null;
      } else {
        this.selectedPackage = { ...update };
      }
    },
    nextFleet() {
      this.getNextFleet(this.fleetId)
        .then((d) => {
          const fleet = d || {};
          this.$router.replace({ name: 'fleet-detail', params: { fleetId: fleet.uuid } }).catch((e) => {});
          this.setup();
        })
        .catch((e) => {
          // log("DEV err", e)
        });
    },

    previousFleet() {
      this.getPreviousFleet(this.fleetId)
        .then((fleet) => {
          this.$router
            .replace({ name: 'fleet-detail', params: { fleetId: fleet.uuid } })
            .catch((e) => {})
            .finally((a) => {
              this.setup();
            });
        })
        .catch((e) => {});
    },
    fleetDeleted() {
      this.$router.replace({ name: 'fleets' }).catch((e) => {});
    },
    getFleet() {},
    setChartTheme() {
      am4core.unuseAllThemes();
      if (this.animateChart) {
        am4core.useTheme(am4themes_animated);
      }
      if (this.$q.dark.isActive) {
        am4core.useTheme(am4themes_dark);
      }
      if (this.chartTheme !== 'default') {
        am4core.useTheme(this.chartThemes[this.chartTheme]);
      }
    },
  },
  watch: {
    selectedPackage(p) {
      if (p) {
        this.giveAttensionToVersionsView = true;
        setTimeout(() => {
          this.showPackageVersions = true;
          this.giveAttensionToVersionsView = false;
        }, 200);
      } else {
        this.showPackageVersions = false;
      }
    },
    darkMode(n, o) {
      this.refresh();
    },
    chartTheme() {
      this.refresh();
    },
  },
};
</script>
