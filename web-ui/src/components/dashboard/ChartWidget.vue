<template>
  <div class="p-1 mnh-10em">
    <canvas id="myChart" ref="chartCanvas" class="w-100 h-100"></canvas>
  </div>
</template>

<script>
import _ from 'lodash';
import { colors } from 'quasar';

export default {
  name: 'ChartWidget',
  components: {},
  props: {
    data: {
      type: Object,
      default: () => {
        return {};
      },
    },
    options: {
      type: Object,
      default: () => {
        return {
          scales: {
            xAxes: [
              {
                type: 'time',
                time: {
                  unit: 'hour',
                },
                distribution: 'series',
              },
            ],
          },
        };
      },
    },
    type: {
      type: String,
      default: 'line',
    },
  },
  data() {
    return {
      chart: null,
      view: 'list',
      filter: 'today',
      datacollection: {},
      viewMap: {
        pie: { icon: 'pie_chart' },
        donut: { icon: 'donut_large' },
        line: { icon: 'insights' },
        bar: { icon: 'insert_chart' },
        column: { icon: 'view_column' },
        list: { icon: 'list_alt' },
      },
      filterMap: {
        today: { label: 'Today' },
        'this-week': { label: 'This week' },
        'this-month': { label: 'This month' },
        'this-year': { label: 'This year' },
      },
      d3LineConfig: {
        date: {
          key: 'date',
          inputFormat: '%Y-%m-%d',
          outputFormat: '%Y-%m-%d',
        },
        values: ['cpu-1', 'cpu-2'],
        axis: {
          yTitle: false,
          xTitle: false,
          yFormat: '.0f',
          xFormat: '%Y-%m-%d',
          yTicks: 5,
          xTicks: 3,
        },
        color: {
          key: false,
          keys: false,
          scheme: false,
          current: '#1f77b4',
          default: '#AAA',
          axis: '#000',
        },
        curve: 'curveLinear',
        margin: {
          top: 20,
          right: 20,
          bottom: 20,
          left: 40,
        },
        points: {
          visibleSize: 3,
          hoverSize: 6,
        },
        tooltip: {
          labels: false,
        },
        transition: {
          duration: 350,
          ease: 'easeLinear',
        },
      },
    };
  },
  computed: {},
  mounted() {
    this.draw();
  },
  methods: {
    draw() {
      var ctx = this.$refs['chartCanvas'].getContext('2d');
      this.chart = new Chart(ctx, {
        type: this.type,
        data: this.data,
        options: this.options,
      });
    },
    update() {
      this.chart.data = this.data;
      this.chart.options = this.options;
      this.chart.update();
    },
  },
  watch: {
    data(n) {
      this.update();
    },
  },
};
</script>
