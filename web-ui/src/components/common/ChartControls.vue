<template>
  <div class=" row">
    <div v-if="optionsVisibility['dateRangeSelector']" class="col-auto opacity-40 text-1">
      Date range:
    </div>
    <div v-if="optionsVisibility['dateRangeSelector']" class="col-auto v-divide-right pr-1">
      <date-time-range-picker
        :from="dateFrom"
        @update:from="$emit('update:dateFrom', $event)"
        :to="dateTo"
        @update:to="$emit('update:dateTo', $event)"
        :tabbed="true"
        :button-only="$q.screen.lt.md"
        :dateRangePreset="dateRangePreset"
        @update:date-range-preset="updateDateRangePreset"
        :presetOptions="dateRangePresetOptions"
        :is-custom-date-range="isCustomDateRange"
        @update:is-custom-date-range="updateCustomDateRangeFlag"
      ></date-time-range-picker>
    </div>
    <div class="col-auto ">
      <q-btn flat icon="settings" class="" label="Chart options" color="primary">
        <q-popup-proxy transition-show="scale" transition-hide="scale">
          <div class="row q-card light-card">
            <div class="col-12 p-1 h-divide-bottom text-1">Chart Options</div>
            <div class="col p-1">
              <div v-if="optionsVisibility['fillChartLines']">
                <q-checkbox label="Fill area under line" :value="fillChartLines" @input="$emit('update:fill-chart-lines', $event)" />
              </div>
              <div v-if="optionsVisibility['animateChart']">
                <q-checkbox label="Animate charts" :value="animateChart" @input="$emit('update:animate-chart', $event)" />
              </div>
              <div v-if="optionsVisibility['showChartsIn3d']">
                <q-checkbox label="Show charts in 3D" :value="showChartsIn3d" @input="$emit('update:showChartsIn3d', $event)" />
              </div>
              <div v-if="optionsVisibility['useDonuts']">
                <q-checkbox label="Use donut for pie charts" :value="useDonuts" @input="$emit('update:useDonuts', $event)" />
              </div>
              <div v-if="optionsVisibility['autoUpdateChart']">
                <q-checkbox label="Auto update charts" :value="autoUpdateChart" @input="$emit('update:auto-update-chart', $event)" />
              </div>
              <div class="pl-3" v-if="autoUpdateChart && optionsVisibility['autoUpdateChart']">
                <q-select label="Update frequency" :value="autoUpdateFreq" :val="autoUpdateFreq" :options="updateFrequencyOptions" emit-value map-options @input="$emit('update:auto-update-freq', $event)" />
              </div>
              <div v-if="optionsVisibility['highlightNoDataRegion']">
                <q-checkbox label="Highlight no-data region" :value="highlightNoDataRegion" @input="$emit('update:highlight-no-data-region', $event)" />
              </div>
            </div>

            <div class="col p-1  v-divide-left-dashed">
              <div class="row">
                <div class="col-12">Select chart theme</div>
                <div class="col-12">
                  <q-option-group :options="chartThemes" type="radio" :value="chartTheme" @input="$emit('update:chartTheme', $event)" />
                </div>
              </div>
            </div>
          </div>
        </q-popup-proxy>
      </q-btn>
    </div>
  </div>
</template>

<script>
import DateTimeRangePicker from 'src/components/common/DateTimeRangePicker.vue';
import { date } from 'quasar';
const { formatDate } = date;
export default {
  components: { DateTimeRangePicker },
  name: 'DeviceChartControls',
  props: {
    chartTheme: {
      type: String,
      default: 'default',
    },
    animateChart: {
      type: Boolean,
      default: true,
    },
    fillChartLines: {
      type: Boolean,
      default: true,
    },
    autoUpdateChart: {
      type: Boolean,
      default: true,
    },
    autoUpdateFreq: {
      type: Number,
      default: 30 * 1000,
    },
    highlightNoDataRegion: {
      type: Boolean,
      default: true,
    },
    showChartsIn3d: {
      type: Boolean,
      default: true,
    },
    useDonuts: {
      type: Boolean,
      default: true,
    },
    dateFrom: {
      type: String,
      default: '2021-01-01 12:00',
    },
    dateTo: {
      type: String,
      default: '2021-01-01 12:30',
    },
    dateRangePreset: {
      type: Number,
      default: 60 * 60 * 1000,
    },
    dateRangePresetOptions: {
      type: Array,
      default: () => [{ label: 'Last 1 minute', value: 60 * 1000 }, { label: 'Last 30 minutes', value: 30 * 60 * 1000 }, { label: 'Last 1 hour', value: 60 * 60 * 1000 }],
    },
    optionsVisibility: {
      type: Object,
      default: () => ({
        fillChartLines: true,
        animateChart: true,
        autoUpdateChart: true,
        highlightNoDataRegion: true,
        chartTheme: true,
        dateRangeSelector: true,
        showChartsIn3d: false,
      }),
    },
    isCustomDateRange: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      updateFrequency: 60 * 1000,
      updateFrequencyOptions: [{ label: 'Every 30 seconds', value: 30 * 1000 }, { label: 'Every minute', value: 60 * 1000 }, { label: 'Every hour', value: 60 * 60 * 1000 }, { label: 'Every 4 hours', value: 4 * 60 * 60 * 1000 }],
      chartThemes: [
        {
          label: 'default',
          value: 'default',
        },
        {
          label: 'material',
          value: 'material',
        },
        {
          label: 'frozen',
          value: 'frozen',
        },
        {
          label: 'dataviz',
          value: 'dataviz',
        },
        {
          label: 'kelly',
          value: 'kelly',
        },
        {
          label: 'spiritedaway',
          value: 'spiritedaway',
        },
        {
          label: 'moonrisekingdom',
          value: 'moonrisekingdom',
        },
        {
          label: 'micro',
          value: 'micro',
        },
      ],
    };
  },
  methods: {
    updateDateRangePreset(value) {
      this.$emit('update:date-range-preset', value);
    },
    updateCustomDateRangeFlag(value) {
      this.$emit('update:is-custom-date-range', value);
    },

    updateFillChartLines(value) {
      this.$emit('update:fill-chart-lines', value);
    },
  },
};
</script>
