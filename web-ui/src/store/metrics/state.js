const toMb = (val) => {
  const memFactor = Math.pow(2, 10);
  return (val / memFactor).toFixed(2);
};

const showZeroSwap = (series) => {
  return ((series.find((s) => s.name === 'swap_total') || {}).points || []).some((a) => a[1] > 0);
};
const defaultCharts = [
  {
    title: 'Memory Usage',
    unit: 'MB',
    valueParser: 'toMegaBytes',
    min: 0,
    max: 8000,
    noDataSeriesValue: 0,
    noDataSeriesName: 'mem_free',
    stackSeries: false,
    noDataFunc: (series, point) => series.name === 'mem_total' && point[1] === 0,
    metrics: {
      mem_free: { label: 'Mem Free', name: 'mem_free', show: true, source: 'metrics' },
      mem_used: { label: 'Mem Used', name: 'mem_used', show: true, source: 'metrics' },
    },
  },
  {
    title: 'CPU Usage',
    unit: '%',
    isCustom: true,
    showPerCoreBreakdown: false,
    min: 0,
    max: 100,
    noDataSeriesValue: 0,
    noDataSeriesName: 'cpu_p',
    stackSeries: false,
    // valueParser: (val) => val,
    metrics: {
      user_p: { label: 'User', name: 'user_p', show: true, source: 'custom-metrics' },
      system_p: { label: 'System', name: 'system_p', show: true, source: 'custom-metrics' },
    },
  },
  {
    title: 'CPU Temperature',
    unit: '°C',
    min: 0,
    max: 120,
    noDataSeriesValue: 0,
    noDataSeriesName: 'temp',
    stackSeries: false,
    // valueParser: (val) => val,
    metrics: {
      temp: { label: 'CPU Temperature', name: 'temp', show: true, source: 'metrics' },
    },
  },
  {
    title: 'Docker Service Status',
    unit: ' ',
    min: 0,
    stackSeries: false,
    // valueParser: '(val) => val',
    metrics: {
      docker_alive: { label: 'Status', name: 'docker_alive', show: true, source: 'metrics' },
    },
  },
];

export default {
  customCharts: null,
  defaultCharts, // format: [{title, unit, min, max, metrics: [{label, name, show}]}]
  chartValueParsers: {
    toMegaBytes: {
      label: 'Convert To Megabytes',
      value: (val) => toMb(val),
    },
    toFixed: {
      label: 'Round to 2 decimal places (e.g. 1.2345 -> 1.23)',
      value: (val) => val.toFixed(2),
    },
    toFahrenheit: {
      label: 'Convert to Fahrenheit',
      value: (val) => {
        const output = val * 1.8 + 32;
        return output.toFixed(2);
      },
    },
    toCelcius: {
      label: 'Convert to Celcius',
      value: (val) => {
        const output = (val - 32) / 1.8;
        return output.toFixed(2);
      },
    },
    None: {
      label: 'None',
      value: (val) => val,
    },
  },
};
