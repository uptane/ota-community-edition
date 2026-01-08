import * as All from 'quasar';
import { Store } from 'vuex-mock-store';

// create the Store mock
const store = new Store({
  state: {},
  getters: {},
});

const { Quasar, date, format } = All;

export const mocks = {
  $date: {
    formatDate: () => {},
  },
  $router: {
    push: () => {},
    replace: () => {},
  },
  $route: {
    path: 'some-nice-path',
    params: {},
    query: { name: 'testPackage' },
  },
  $events: {
    $emit: () => {},
    $on: () => {},
  },
  $format: format,
  $redrawVueMasonry: () => {},
  // $refs: {
  //   deviceGrp
  // },
  $date: date,
  $store: store,
  $q: {
    dialog: () => {
      return new Promise();
    },
    platform: {
      is: {},
    },
    dark: { isActive: false },
    screen: {
      lt: {
        md: true,
        sm: true,
        md: true,
        xs: true,
      },
      gt: {
        md: true,
        sm: true,
        md: true,
        xs: true,
      },
    },
  },
};
