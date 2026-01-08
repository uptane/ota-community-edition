import Vue from 'vue';
import store from '../store';

class Options {
  constructor() {
    this._queue = {};
    this._queueTimeout = null;
  }
  get userSettings() {
    return store.getters['ui/userSettings'];
  }
  init() {
    return store.dispatch('ui/fetchUserSettings', {}, { root: true });
  }
  _queueOptionsForSave(options, timeout = 3000, immediate = false) {
    this._queue = { ...this._queue, ...options };
    if (this._queueTimeout) {
      clearTimeout(this._queueTimeout);
    }
    if (immediate) {
      return this._saveOptionsNow(this._queue);
    }
    this._queueTimeout = setTimeout(() => {
      this._queueTimeout = null;
      this._saveOptionsNow(this._queue);
      this._queue = {};
    }, timeout);
  }

  _saveOptionsNow(queuedData) {
    return store.dispatch('ui/saveUserSettings', { ...queuedData }, { root: true });
  }

  getSavedOption(key) {
    return this.userSettings[key];
  }
  getSavedOptionOrDefault(key, defaultValue) {
    return typeof this.userSettings[key] !== 'undefined' ? this.userSettings[key] : defaultValue;
  }
  saveOption(key, value) {
    Vue.set(this.userSettings, key, value);
    return this._queueOptionsForSave({ [key]: value });
  }
  saveOptionImmediately(key, value) {
    Vue.set(this.userSettings, key, value);
    return this._queueOptionsForSave({ [key]: value }, 1, true);
  }
  saveMultipleOptions(options) {
    Object.keys(options).forEach((key) => {
      Vue.set(this.userSettings, key, options[key]);
    });
    return this._queueOptionsForSave(options);
  }
}

export const OptionsService = new Options();
