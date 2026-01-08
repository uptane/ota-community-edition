<template>
  <div>
    <div ref="chartDiv" class="mnh-30em chartDiv"></div>
    <q-dialog transition-show="scale" transition-hide="scale" :value="!!activeItem" @hide="activeItem = null">
      <q-card class="shadow-12 q-px-md q-pt-md">
        <div v-if="activeItem !== null" class="text-h6 h-divide-bottom-dashed text-center"><span class="faded">Last Seen</span> {{ activeItem.categories.categoryX }}</div>
        <div class=" mxh-30em mxw-30em overflow-auto">
          <div v-for="(device, key) in ((activeItem || {}).dataContext || {}).devices || []" :key="key">
            <device-item :device="device"></device-item>
            <q-separator class="q-my-xs"></q-separator>
          </div>
        </div>
      </q-card>
    </q-dialog>
    <div class="mt-1 mb-1 text-center text-1">{{ title }}</div>
  </div>
</template>

<script>
import { mapActions, mapGetters } from 'vuex';
import { date } from 'quasar';
import * as am4core from '@amcharts/amcharts4/core';
import * as am4charts from '@amcharts/amcharts4/charts';
import am4themes_animated from '@amcharts/amcharts4/themes/animated';
import am4themes_dark from '@amcharts/amcharts4/themes/dark';
import am4themes_material from '@amcharts/amcharts4/themes/material';
import am4themes_frozen from '@amcharts/amcharts4/themes/frozen';
import am4themes_dataviz from '@amcharts/amcharts4/themes/dataviz';
import am4themes_kelly from '@amcharts/amcharts4/themes/kelly';
import am4themes_spiritedaway from '@amcharts/amcharts4/themes/spiritedaway';
import am4themes_moonrisekingdom from '@amcharts/amcharts4/themes/moonrisekingdom';
import ChartPlaceholder from '../common/ChartPlaceholder.vue';
import DeviceItem from '../devices/DeviceItem.vue';

export default {
  components: { ChartPlaceholder, DeviceItem },
  name: 'FleetOSPackages',
  props: {
    fleet: {
      required: true,
    },
    title: {
      type: String,
      default: 'Devices Online Status Overview',
    },
    secondary: {
      type: Boolean,
      default: false,
    },
    animateChart: {
      type: Boolean,
      default: true,
    },
    showIn3D: {
      type: Boolean,
      default: true,
    },
  },
  data() {
    return {
      activeItem: null,
      fleetDevices: [],
    };
  },
  mounted() {
    this.fetchFleetDevices({ fleetId: this.fleetId })
      .then((resp) => {
        this.fleetDevices = resp.values;
      })
      .finally(() => {
        this.plotChart();
      });
  },
  computed: {
    ...mapGetters({
      fleetsById: 'fleets/fleetsById',
      devicesByUuid: 'devices/devicesByUuid',
      packagesByHash: 'packages/packagesByHash',
    }),
    fleetId() {
      return this.fleet.id;
    },
    fleetPackages() {
      const primaryPackages = [];
      let applicationPackages = [];
      this.fleetDevices.map((device) => {
        const packages = (device.installedTargets || []).map((target) => this.packagesByHash[(target.checksum || {}).hash] || {});
        primaryPackages.push(packages.find((f) => f.isOSPackage));
        applicationPackages = applicationPackages.concat(packages.filter((f) => f.isApplicationPackage));
      });
      return _.groupBy(this.secondary ? applicationPackages : primaryPackages, 'hash');
    },
    fleetDevicesLastSeen() {
      const dateRanges = {
        '< 10min': 10 * 60 * 1000,
        'last hour': 60 * 60 * 1000,
        'last day': 24 * 60 * 60 * 1000,
        'last week': 7 * 24 * 60 * 60 * 1000,
        'last month': 30 * 24 * 60 * 60 * 1000,
        'more than a month': 365 * 24 * 60 * 60 * 1000,
      };
      const lastSeen = this.fleetDevices.map((device) => {
        const key = Date.now() - new Date(device.lastSeen).getTime();
        let seen = 'never';
        switch (true) {
          case key < dateRanges['< 10min']:
            seen = '< 10min';
            break;
          case key < dateRanges['last hour']:
            seen = 'Last hour';
            break;
          case key < dateRanges['last day']:
            seen = 'Last day';
            break;
          case key < dateRanges['last week']:
            seen = 'Last week';
            break;
          case key < dateRanges['last month']:
            seen = 'Last month';
            break;
          default:
            seen = 'More than a month';
        }
        return { device, seen };
      });
      const grouped = _.groupBy(lastSeen, 'seen');
      const mapped = _.map(grouped, (g) => {
        const p = g[0] || {};
        return { ...p, seen: p.seen || 'Unknown', count: g.length, devices: g.map((f) => f.device) };
      });
      const addToMap = (text) => {
        if (!mapped.find((f) => f.seen === text)) {
          mapped.push({ seen: text, count: 0, devices: [] });
        }
      };
      ['< 10min', 'Last hour', 'Last day', 'Last week', 'Last month', 'More than a month'].forEach(addToMap);
      return mapped;
    },
  },
  methods: {
    ...mapActions({
      fetchFleetDevices: 'fleets/fetchFleetDevices',
    }),
    plotChart() {
      this.chart = am4core.create(this.$refs.chartDiv, am4charts.XYChart);

      const data = this.fleetDevicesLastSeen;
      this.chart.data = data;
      const categoryAxis = this.chart.xAxes.push(new am4charts.CategoryAxis());
      categoryAxis.dataFields.category = 'seen';
      categoryAxis.title.text = 'Last Seen';

      const valueAxis = this.chart.yAxes.push(new am4charts.ValueAxis());
      valueAxis.dataFields.value = 'count';
      valueAxis.title.text = 'Device Count';
      const series = this.chart.series.push(new am4charts[this.showIn3D ? 'ColumnSeries3D' : 'ColumnSeries']());
      valueAxis.title.fill = series.stroke;
      categoryAxis.title.fill = series.stroke;
      valueAxis.min = 0;
      const max = _.maxBy(data, (d) => d.count).count + 1;
      valueAxis.max = max;
      valueAxis.numberFormatter = new am4core.NumberFormatter();
      valueAxis.numberFormatter.numberFormat = '#';
      valueAxis.adjustLabelPrecision = false;
      valueAxis.renderer.labels.template.adapter.add('text', function(text, target) {
        return (text || '').match(/\./) ? '' : text;
      });
      series.name = 'Last Seen';
      series.columns.template.tooltipText = 'Series: {name}\nCategory: {categoryX}\nDevices: {valueY}\n[bold]Click to view devices in this category[/]';
      series.dataFields.valueY = 'count';
      series.dataFields.categoryX = 'seen';
      series.columns.template.events.on(
        'hit',
        (ev) => {
          this.activeItem = ev.target.dataItem;
        },
        this,
      );
    },
    refresh() {
      this.plotChart();
    },
  },
  watch: {
    fleetId() {
      this.refresh();
    },
    showIn3D() {
      this.refresh();
    },
  },
};
</script>

<style></style>
