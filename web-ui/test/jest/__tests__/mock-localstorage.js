import Vue from 'vue';

class MockLocalStorage {
  constructor() {
    this.locallyStoredData = {};
  }
  getItem(key) {
    return this.locallyStoredData[key];
  }
  set(key, value) {
    Vue.set(this.locallyStoredData, key, value);
  }
}
export const mockLocalStorage = new MockLocalStorage();
