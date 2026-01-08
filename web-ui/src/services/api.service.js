import axios from 'axios';
import { extend } from 'quasar';
import _ from 'underscore';

export default class ApiService {
  static get defaultOptions() {
    return {
      validateStatus: function(status) {
        return (status >= 200 && status < 300) || status === 304 || status === 401; // default
      },
    };
  }
  static getResource(url, options = ApiService.defaultOptions, rawResponse = false) {
    return new Promise((resolve, reject) => {
      return axios
        .get(url, options)
        .then((response) => {
          if (rawResponse) {
            return resolve(response);
          }
          return resolve((response || {}).data);
        })
        .catch((error) => {
          reject(error);
        });
    });
  }

  static multiSourceGet(urls, options = ApiService.defaultOptions, rawResponse = false) {
    return new Promise((resolve, reject) => {
      if (!Array.isArray(urls)) {
        return reject('URLs must be an array of at least one url');
      }
      const requests = urls.map((urlData) => {
        let url = urlData,
          urlOptions = options;
        if (_.isObject(urlData)) {
          url = urlData.url;
          urlOptions = extend(true, {}, options, urlData.options);
        }
        return axios.get(url, urlOptions);
      });
      Promise.all(requests)
        .then((responses) => {
          const dataArray = responses.map((r) => (rawResponse ? r : r.data));
          resolve(dataArray);
        })
        .catch((error) => {
          reject(error);
        });
    });
  }

  // static search (url, regex, options) {
  //     return new Promise((resolve, reject) => {
  //         reject('NOT_IMPLEMENTED')
  //     })
  // }
  static postResource(url, data, options = ApiService.defaultOptions, rawResponse = false) {
    return new Promise((resolve, reject) => {
      return axios
        .post(url, data, options)
        .then((response) => {
          if (rawResponse) {
            return resolve(response);
          }
          return resolve((response || {}).data);
        })
        .catch((error) => {
          reject(error);
        });
    });
  }
  static putResource(url, data, options = ApiService.defaultOptions, rawResponse = false) {
    return new Promise((resolve, reject) => {
      return axios
        .put(url, data, options)
        .then((response) => {
          if (rawResponse) {
            return resolve(response);
          }
          return resolve((response || {}).data);
        })
        .catch((error) => {
          reject(error);
        });
    });
  }
  static patchResource(url, data, options = ApiService.defaultOptions, rawResponse = false) {
    return new Promise((resolve, reject) => {
      return axios
        .patch(url, data, options)
        .then((response) => {
          if (rawResponse) {
            return resolve(response);
          }
          return resolve((response || {}).data);
        })
        .catch((error) => {
          reject(error);
        });
    });
  }
  static deleteResource(url, options = ApiService.defaultOptions, rawResponse = false) {
    return new Promise((resolve, reject) => {
      return axios
        .delete(url, options)
        .then((response) => {
          if (rawResponse) {
            return resolve(response);
          }
          return resolve((response || {}).data);
        })
        .catch((error) => {
          reject(error);
        });
    });
  }
}
