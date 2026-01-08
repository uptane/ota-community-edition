import { LocalStorage } from 'quasar';

class MyLocalStorage {
  constructor() {
    this.locallyStoredData = {};
  }
  getItem(key) {
    return LocalStorage.getItem(key);
  }
  set(key, value) {
    LocalStorage.set(key, value);
  }
}
export const localStorage = new MyLocalStorage();
