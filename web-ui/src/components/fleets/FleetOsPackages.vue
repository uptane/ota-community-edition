<template>
  <div class="row">
    <div ref="chartDiv" class="mnh-30em mnw-20em col-8"></div>
    <div ref="legend" class="legend col-3">
      <div v-for="(item, index) in legendItems" class="legend-item row items-center h-divide-bottom-dashed" :id="'legend-item-' + index" @mouseover="hoverSlice(index)" @mouseout="blurSlice(index)" :key="'legend-item-' + index" @click.stop="viewDevices(item)">
        <div
          class="legend-marker col-auto"
          @click.stop="toggleSlice(index)"
          :style="{
            background: chart.colors.getIndex(index),
          }"
        ></div>
        <div
          class="col ellipsis"
          :style="{
            color: chart.colors.getIndex(index),
          }"
        >
          {{ item.category }}
        </div>
        <div class="legend-value col-12 ml-2">{{ item.value + (item.value > 1 ? ' devices' : ' device') + ' | ' + Math.round(item.values.value.percent * 100) / 100 + '%' }}</div>
        <tooltip>
          <div
            class="
            row"
          >
            <div
              class="col-12"
              :style="{
                color: chart.colors.getIndex(index),
              }"
            >
              {{ item.category }}
            </div>
            <div class="legend-value col-12">{{ item.value + (item.value > 1 ? ' devices' : ' device') + ' | ' + Math.round(item.values.value.percent * 100) / 100 + '%' }}</div>
            <div class="col-12 text-center q-py-xs q-mt-xs h-divide-top-dotted">Click to view devices in this category</div>
          </div>
        </tooltip>
      </div>
    </div>
    <q-dialog transition-show="scale" transition-hide="scale" :value="!!activeItem" @hide="activeItem = null">
      <q-card class="shadow-12 q-px-md q-pt-md">
        <div v-if="activeItem !== null" class="text-h6 h-divide-bottom-dashed text-center"><span class="faded">Devices in </span> {{ activeItem.category }}</div>
        <div class=" mxh-30em mxw-30em overflow-auto">
          <div v-for="(device, key) in ((activeItem || {}).dataContext || {}).devices || []" :key="key">
            <device-item :device="device"></device-item>
            <q-separator class="q-my-xs"></q-separator>
          </div>
        </div>
      </q-card>
    </q-dialog>
    <div class="col-12 mt-1 mb-1 text-center text-1">{{ title }}</div>
  </div>
</template>

<script>
import { mapActions, mapGetters } from 'vuex';
import * as am4core from '@amcharts/amcharts4/core';
import * as am4charts from '@amcharts/amcharts4/charts';
import ChartPlaceholder from '../common/ChartPlaceholder.vue';
import Tooltip from '../common/Tooltip.vue';
import DeviceItem from '../devices/DeviceItem.vue';

export default {
  components: { ChartPlaceholder, Tooltip, DeviceItem },
  name: 'FleetOSPackages',
  props: {
    fleet: {
      required: true,
    },
    title: {
      type: String,
      default: 'Packages Distribution',
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
    useDonuts: {
      type: Boolean,
      default: true,
    },
  },
  data() {
    return {
      legendItems: [],
      chart: {},
      pieSeries: {},
      activeItem: null,
      fleetDevices: [],
    };
  },
  mounted() {
    this.fetchFleetDevices({ fleetId: this.fleetId })
      .then((resp) => {
        this.fleetDevices = resp.values;
      })
      .catch(() => {})
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
      const max = 6;
      const primaryPackages = [];
      let applicationPackages = [];
      this.fleetDevices.map((device) => {
        const packages = (device.installedTargets || []).map((target) => ({ ...(this.packagesByHash[(target.checksum || {}).hash] || {}), device }));
        primaryPackages.push(packages.find((f) => f.isOSPackage));
        applicationPackages = applicationPackages.concat(packages.filter((f) => f.isApplicationPackage));
      });
      const group = _.groupBy(this.secondary ? applicationPackages : primaryPackages, 'hash');
      const sorted = _.sortBy(
        _.map(group, (pkg) => {
          const p = pkg[0] || {};
          return { ...p, name: p.name || 'Unknown', count: pkg.length, devices: pkg.map((f) => (f || {}).device) };
        }),
        'count',
      ).reverse();
      // Ensure we don't return more than max items and other. So we'll just return the top max items anything else will be grouped under 'other'
      const sliced = _.takeWhile(sorted, function(value, index, arr) {
        return index < max;
      });
      if (max < sorted.length) {
        const other = _.drop(sorted, max);
        sliced.push({ name: 'Other', count: other.reduce((a, b) => a + b.count, 0), devices: other.reduce((a, b) => a.concat(b.devices), []) });
      }
      return sliced;
    },
  },
  methods: {
    ...mapActions({
      fetchFleetDevices: 'fleets/fetchFleetDevices',
    }),
    plotChart() {
      this.chart = am4core.create(this.$refs.chartDiv, am4charts.PieChart3D);
      this.chart.data = this.fleetPackages;
      const Series = this.showIn3D ? am4charts.PieSeries3D : am4charts.PieSeries;
      this.pieSeries = this.chart.series.push(new Series());
      this.pieSeries.dataFields.value = 'count';
      this.pieSeries.dataFields.category = 'name';
      this.pieSeries.labels.template.disabled = true;
      this.pieSeries.ticks.template.disabled = true;
      this.pieSeries.labels.template.text = '{category}: {value.value}';
      this.pieSeries.slices.template.tooltipText = '{category}: {value.value}';
      this.chart.radius = am4core.percent(90);
      if (this.useDonuts) this.chart.innerRadius = am4core.percent(40);

      this.chart.radius = am4core.percent(95);

      // Create custom legend
      this.chart.events.on('ready', (event) => {
        // populate our custom legend when chart renders
        this.chart.customLegend = this.$refs.legend;
        this.legendItems = this.pieSeries.dataItems;
      });
      this.pieSeries.slices.template.events.on(
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
    viewDevices(item) {
      this.activeItem = item;
    },
    toggleSlice(item) {
      var slice = this.pieSeries.dataItems.getIndex(item);
      if (slice.visible) {
        slice.hide();
      } else {
        slice.show();
      }
    },

    hoverSlice(item) {
      var slice = this.pieSeries.slices.getIndex(item);
      slice.isHover = true;
    },
    blurSlice(item) {
      var slice = this.pieSeries.slices.getIndex(item);
      slice.isHover = false;
    },
  },
  watch: {
    fleetId() {
      this.refresh();
    },
    showIn3D() {
      this.refresh();
    },
    useDonuts() {
      this.refresh();
    },
  },
};
</script>

<style>
.legend .legend-item {
  margin: 10px;
  font-size: 15px;
  cursor: pointer;
}

.legend .legend-item .legend-value {
  font-size: 12px;
  font-weight: normal;
  margin-left: 22px;
}

.legend .legend-item .legend-marker {
  display: inline-block;
  width: 12px;
  height: 12px;
  border: 1px solid #ccc;
  margin-right: 10px;
}

.legend .legend-item.disabled .legend-marker {
  opacity: 0.5;
  background: #ddd;
}
</style>
