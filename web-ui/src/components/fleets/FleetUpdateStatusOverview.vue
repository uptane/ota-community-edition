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
      default: 'Devices Update Status Overview',
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
    fleetDevicesUpdateStatus() {
      const updateStatus = this.fleetDevices.map((device) => {
        const key = device.deviceStatus;
        let state = 'Unknown';
        switch (true) {
          case key === 'Outdated':
            state = 'Update pending';
            break;
          case key === 'UpToDate':
            state = 'Up to date';
            break;
          case key === 'UpdatePending':
            state = 'In progress';
            break;
          case key === 'Failed' || key === 'Error':
            state = 'Failed';
            break;
          default:
            state = 'Unknown';
        }
        return { device, state };
      });
      const grouped = _.groupBy(updateStatus, 'state');
      const mapped = _.map(grouped, (s) => {
        const p = s[0] || {};
        return { ...p, state: p.state || 'Unknown', count: s.length, devices: s.map((f) => f.device) };
      });
      const addToMap = (text) => {
        if (!mapped.find((f) => f.state === text)) {
          mapped.push({ state: text, count: 0, devices: [] });
        }
      };
      ['Update pending', 'Up to date', 'In progress', 'Failed'].forEach(addToMap);
      return mapped;
    },
  },
  methods: {
    ...mapActions({
      fetchFleetDevices: 'fleets/fetchFleetDevices',
    }),
    plotChart() {
      this.chart = am4core.create(this.$refs.chartDiv, am4charts.PieChart3D);
      this.chart.data = this.fleetDevicesUpdateStatus;
      const Series = this.showIn3D ? am4charts.PieSeries3D : am4charts.PieSeries;
      this.pieSeries = this.chart.series.push(new Series());
      this.pieSeries.dataFields.value = 'count';
      this.pieSeries.dataFields.category = 'state';
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

<style></style>
