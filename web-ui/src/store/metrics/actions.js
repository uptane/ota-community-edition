import _ from 'lodash';

import ApiService from '../../services/api.service';
import { API_DEVICE_METRICS_FETCH, API_DEVICE_METRICS_NAMES_FETCH, API_FLEET_METRICS_FETCH, API_FLEET_METRICS_NAMES_FETCH } from '../../config';

export function fetchListOfMetrics({ state, commit }) {
  return new Promise((resolve, reject) => {
    return ApiService.multiSourceGet([`${API_DEVICE_METRICS_NAMES_FETCH}/metric-names`, `${API_DEVICE_METRICS_NAMES_FETCH}/custom-metrics/metric-names`])
      .then((resultArray) => {
        const data = (resultArray[0].values || [])
          .map((item) => {
            return { name: item, source: 'metrics' };
          })
          .concat(
            (resultArray[1].values || []).map((item) => {
              return { name: item, source: 'custom-metrics' };
            }),
          );
        resolve(data);
      })
      .catch((error) => {
        reject(error);
      });
  });
}
export function fetchListOfFleetMetrics({ state, commit }) {
  return new Promise((resolve, reject) => {
    return ApiService.getResource([`${API_FLEET_METRICS_NAMES_FETCH}`])
      .then((result) => {
        resolve(data);
      })
      .catch((error) => {
        reject(error);
      });
  });
}
export function fetchDeviceMetrics({ state, commit }, { deviceUUID, from, to, metrics }) {
  const metricGroups = _.groupBy(metrics, 'source');
  const metricUrls = Object.keys(metricGroups).map((source) => {
    const metricNames = metricGroups[source].map((item) => item.name).join(',');
    return `${API_DEVICE_METRICS_FETCH}/${deviceUUID}/${source}?metrics=${metricNames}&from=${from}&to=${to}`;
  });
  return new Promise((resolve, reject) => {
    return ApiService.multiSourceGet(metricUrls)
      .then((metrics) => {
        const metricData = { series: [] };
        metrics.forEach((metric) => {
          metricData.series = metricData.series.concat(metric.series || []);
        });
        resolve(metricData);
      })
      .catch((error) => {
        reject(error);
      });
  });
}

export function fetchDeviceMetricsCSV({ state, commit }, { deviceUUID, from, to, metrics, dataType, total_buckets, raw_datapoints, filename }) {
  const metricGroups = _.groupBy(metrics, 'source');
  let metricNames = [];
  Object.keys(metricGroups).forEach((source) => {
    metricNames.push(metricGroups[source].map((item) => item.name));
  });
  const url = `${API_DEVICE_METRICS_FETCH}/${deviceUUID}/detailed-metrics?metrics=${metricNames.join(',')}&from=${from}&to=${to}&${dataType === 'raw' ? 'raw_datapoints=' + raw_datapoints : 'total_buckets=' + total_buckets}`;
  return new Promise((resolve, reject) => {
    return ApiService.getResource(url, {
      headers: {
        Accept: 'text/csv',
        responseType: 'blob',
      },
    })
      .then((metrics) => {
        resolve(metrics);
      })
      .catch((error) => {
        reject(error);
      });
  });
}
export function fetchFleetMetricsCSV({ state, commit }, { deviceUUIDs, from, to, metrics, dataType, total_buckets, raw_datapoints }) {
  const metricGroups = _.groupBy(metrics, 'source');
  let metricNames = [];
  Object.keys(metricGroups).forEach((source) => {
    metricNames = metricNames.concat(metricGroups[source].map((item) => item.name));
  });
  const query = {
    from: from,
    to: to,
    metrics: metricNames,
  };
  if (dataType === 'raw') {
    query.datapointCount = raw_datapoints;
  } else {
    query.totalBuckets = total_buckets;
  }

  return new Promise((resolve, reject) => {
    return ApiService.postResource(
      `${API_FLEET_METRICS_FETCH}/reports`,
      {
        devices: [...deviceUUIDs],
        query,
      },
      {
        headers: {
          Accept: 'text/csv',
        },
      },
    )
      .then((metrics) => {
        resolve(metrics);
      })
      .catch((error) => {
        reject(error);
      });
  });
}
export function fetchFleetMetrics({ state, commit }, { deviceUUIDs, from, to, metricNames }) {
  return new Promise((resolve, reject) => {
    return ApiService.postResource(API_FLEET_METRICS_FETCH, { devices: deviceUUIDs, from, to, metrics: metricNames })
      .then((metrics) => {
        resolve(metrics);
      })
      .catch((error) => {
        reject(error);
      });
  });
}
export function fetchFleetMetricOutliers({ dispatch, state, commit }, { deviceUUIDs, from, to, metricNames, aggregation }) {
  return new Promise((resolve, reject) => {
    return ApiService.postResource(API_FLEET_METRICS_FETCH + '/outliers', { devices: deviceUUIDs, from, to, metrics: metricNames, limit: 10, aggregation })
      .then(async (data) => {
        let deviceUuids = {};
        let metrics = {};
        (data.values || []).forEach((item) => {
          metrics[item.metricName] = item;
          (item.outliers || []).forEach((o) => {
            deviceUuids[o.deviceId] = o.deviceId;
          });
        });
        let devices = await dispatch('devices/fetchDevicesByUuids', { deviceUuids: Object.keys(deviceUuids) }, { root: true });
        let parsed = Object.values(metrics).map((item) => {
          return {
            ...item,
            outliers: item.outliers.map((o) => {
              return {
                ...o,
                device: devices.find((f) => f.uuid === o.deviceId),
              };
            }),
          };
        });
        resolve(parsed);
      })
      .catch((error) => {
        reject(error);
      });
  });
}
