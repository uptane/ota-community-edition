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
            <div class="col-12">
              <div class="row">
                <h5 class="m-0 col pl-0">
                  <div class="row">
                    <div class="col mxw-90 ellipsis">{{ fleet.groupName }} <span class="faded">fleet </span></div>
                    <div class="col-auto">
                      <q-btn flat @click="showEditDialog" color="primary" icon="edit">
                        <tooltip>Rename this fleet</tooltip>
                      </q-btn>
                    </div>
                  </div>
                </h5>
                <h5 class="m-0 pl-2 pr-2  pb-1" v-if="$q.screen.gt.sm">
                  <div class="row">
                    <div class=" col mr-0 v-divide-right">
                      <chart-controls
                        :chart-theme.sync="selectedChartTheme"
                        :animateChart.sync="animateChart"
                        :fillChartLines.sync="fillChartLines"
                        :autoUpdateChart.sync="autoUpdateChart"
                        :autoUpdateFreq.sync="autoUpdateFreq"
                        :dateFrom.sync="dateFrom"
                        :dateTo.sync="dateTo"
                        :highlightNoDataRegion.sync="highlightNoDataRegion"
                        :dateRangePreset.sync="dateRangePreset"
                        :dateRangePresetOptions="dateRangePresetOptions"
                        :is-custom-date-range.sync="isCustomDateRange"
                        :optionsVisibility="chartOptionsVisible"
                        @update:all="refresh"
                      ></chart-controls>
                    </div>
                    <div class=" col-auto  v-divide-right">
                      <q-btn color="primary" flat @click="$events.$emit('dialogs:metrics-manager:open', {})"> <q-icon name="addchart" class="mr-1"></q-icon> Customize Metrics </q-btn>
                    </div>
                    <div class=" col-auto">
                      <reload-btn :busy="loadingPackages || loadingFleetData || loading" @reload-requested="refresh" color="primary"></reload-btn>
                    </div>

                    <div class="lt-sm col-12 text-center pt-2">{{ fleet.groupName }} <span class="faded">fleet </span></div>
                  </div>
                </h5>
              </div>
            </div>
            <div class="col-12 mt-1 h-divide-top">
              <fleet-chart
                :chartTheme="selectedChartTheme"
                :animateChart="animateChart"
                :fillChartLines="fillChartLines"
                :autoUpdateChart="autoUpdateChart"
                :autoUpdateFreq="autoUpdateFreq"
                :fleet="fleet"
                :dateFrom="dateFrom"
                :dateTo="dateTo"
                :is-custom-date-range="isCustomDateRange"
                :highlightNoDataRegion.sync="highlightNoDataRegion"
                ref="chartRef"
              ></fleet-chart>
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
import { OptionsService } from '../../services/options.service';

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
    quickView: {
      type: Boolean,
      default: false,
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
      _showChartsIn3d: true,
      _useDonuts: true,
      _fillChartLines: true,
      _autoUpdateChart: true,
      _autoUpdateFreq: 60 * 1000,
      _highlightNoDataRegion: true,
      _dateRangePreset: 60 * 60 * 1000,
      dateRangePresetOptions: [{ label: 'Last hour', value: 60 * 60 * 1000 }, { label: 'Last 4 hours', value: 4 * 60 * 60 * 1000 }, { label: 'One day', value: 24 * 60 * 60 * 1000 }, { label: 'One week', value: 7 * 24 * 60 * 60 * 1000 }],
      _dateFrom: formatDate(new Date(Date.now() - 4 * 60 * 60 * 1000), 'YYYY-MM-DD HH:mm:ss'),
      _dateTo: formatDate(new Date(), 'YYYY-MM-DD HH:mm:ss'),
      _dateData: null,
      chartOptionsVisible: {
        fillChartLines: true,
        animateChart: true,
        autoUpdateChart: true,
        highlightNoDataRegion: true,
        chartTheme: false,
        dateRangeSelector: true,
        showChartsIn3d: false,
        useDonuts: false,
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
      return this.fleet.id;
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
    showChartsIn3d: {
      get() {
        return this.$data._showChartsIn3d;
      },
      set(v) {
        this.$data._showChartsIn3d = v;
        this.updateUserOption('showChartsIn3d', v);
      },
    },
    useDonuts: {
      get() {
        return this.$data._useDonuts;
      },
      set(v) {
        this.$data._useDonuts = v;
        this.updateUserOption('useDonuts', v);
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
    chartTheme() {
      return this.selectedChartTheme;
    },
    darkMode() {
      return this.$q.dark.isActive;
    },

    dateRangeData: {
      set(data) {
        this.$set(this.$data, '_dateData', data);
        this.updateUserOption('fleets:chart:date-range-data', data);
      },
      get() {
        this.$data._dateData =
          this.$data._dateData ||
          OptionsService.getSavedOptionOrDefault('fleets:chart:date-range-data', {
            from: this.$data._dateFrom,
            to: this.$data._to,
            preset: this.$data._dateRangePreset,
            isCustom: false,
          });
        return this.$data._dateData;
      },
    },
    dateRangePreset: {
      get() {
        return this.dateRangeData.preset;
      },
      set(v) {
        this.dateRangeData = { ...this.dateRangeData, preset: v };
      },
    },
    dateFrom: {
      get() {
        return this.dateRangeData.from;
      },
      set(v) {
        this.dateRangeData = { ...this.dateRangeData, from: v };
      },
    },
    dateTo: {
      get() {
        return this.dateRangeData.to;
      },
      set(v) {
        this.dateRangeData = { ...this.dateRangeData, to: v };
      },
    },
    isCustomDateRange: {
      get() {
        return this.dateRangeData.isCustom;
      },
      set(v) {
        this.dateRangeData = { ...this.dateRangeData, isCustom: v };
      },
    },
  },
  beforeDestroy() {},

  created() {},
  methods: {
    ...mapActions({
      fetchFleet: 'fleets/fetchFleet',
      fetchDevices: 'devices/fetchDevices',
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
    async setup() {
      this.pageTitle = 'Fleet Information';
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
    },
    getSavedSettingOrDefault(key, defaultValue) {
      return typeof this.userSettings[key] !== 'undefined' ? this.userSettings[key] : defaultValue;
    },
    refresh() {
      this.loadingFleetData = true;
      this.setChartTheme();
      this.loadingFleetData = false;
      _.each(this.$refs || {}, (chart) => {
        if (chart && chart.refresh && typeof chart.refresh === 'function') chart.refresh();
      });
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
