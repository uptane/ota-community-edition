/* eslint-disable */
/**
 * @jest-environment jsdom
 */

import { mount, createLocalVue, shallowMount } from '@vue/test-utils';
import store from '../../../../src/store';
import Devices from '../../../../src/pages/Devices';
import { createSandbox } from 'sinon';
import Vue from 'vue';
import VueRouter from 'vue-router';
import * as All from 'quasar';

// import langEn from 'quasar/lang/en-us' // change to any language you wish! => this breaks wallaby :(
const { Quasar, date, format, SessionStorage } = All;

const components = Object.keys(All).reduce((object, key) => {
  const val = All[key];
  if (val && val.component && val.component.name != null) {
    object[key] = val;
  }
  return object;
}, {});
const stubs = {
  devices: { template: '<div></div> ' },
  'feature-teaser': { template: '<div></div> ' },
};

const sandbox = createSandbox();
describe('Devices Page', () => {
  const localVue = createLocalVue();
  const testState = {};
  let commitFunction = (key, value) => {
    testState[key] = value;
  };
  let getters = {
    'ui/isDarkTheme': testState['ui/setIsDarkTheme'],
    'ui/isDashboardPage': testState['ui/setIsDashboardPage'],
    'ui/currentPageTitle': testState['ui/setCurrentPageTitle'],
    'fleets/selectedFleet': testState['fleets/setSelectedFleet'],
  };
  const $store = {
    getters,
    commit: commitFunction,
  };
  const deviceGrp = {
    showDialog: () => {},
  };
  const mocks = {
    // $date: {
    //   formatDate: () => {},
    // },
    // $router: {
    //   push: () => {},
    //   replace: () => {},
    // },
    // $route: {
    //   path: 'some-nice-path',
    //   params: {},
    //   query: { name: 'testPackage' },
    // },
    $events: {
      $emit: () => {},
      $on: () => {},
    },
    // $format: format,
    $redrawVueMasonry: () => {},
    // $refs: {
    //   deviceGrp
    // },
    // $date: date,
    $store,
    // $q: {
    //   sessionStorage: {
    //     getItem: () => { },
    //     set: () => { },
    //   },
    //   dialog: () => {
    //     return new Promise();
    //   },
    //   platform: {
    //     is: {},
    //   },
    //   screen: {
    //     lt: {
    //       md: true,
    //       sm: true,
    //       md: true,
    //       xs: true,
    //     },
    //     gt: {
    //       md: true,
    //       sm: true,
    //       md: true,
    //       xs: true,
    //     },
    //   },
    // },
  };

  let viewType = 'table';
  let propsData = {
    packageData: {},
    viewType,
  };
  const router = new VueRouter();
  /** @type: {Wrapper<Vue>} */
  let wrapper;
  /** @type: Vue */
  let vm;

  // const localVue = createLocalVue()
  localVue.use(Quasar, {
    components,
    directives: All,
    plugins: { SessionStorage },
  }); // , lang: langEn

  wrapper = mount(Devices, {
    mocks,
    store,
    stubs,
    localVue,
  });
  vm = wrapper.vm;
  let onPageLoadSpy;
  let stubPageLoad = true;

  const resetTest = () => {
    propsData = {
      packageData: {},
      viewType,
    };
    sandbox.restore();
    if (stubPageLoad) {
      // onPageLoadSpy = sandbox
      //   .stub(Devices.methods, `renameFleet`)
      // .callsFake(() => {});
    }
  };
  describe('Mount Quasar', () => {
    beforeEach(() => {
      resetTest();
    });
    afterEach(() => {
      sandbox.restore();
      stubPageLoad = true;
    });
    // it('passes the sanity check and creates a wrapper', () => {
    //   expect(wrapper.isVueInstance()).toBe(true);
    // });
  });
  describe('Initialize Defaults', () => {
    it('should initialize showFleetDetail to default', () => {
      expect(vm.showFleetDetail).to.eq(true);
    });
    it('should initialize filter to default', () => {
      expect(vm.filter).to.eq('');
    });
    it('should initialize layoutType to default (`cards`)', () => {
      expect(vm.layoutType).to.eq('cards');
    });
    it('should initialize viewType to default  (`card`)', () => {
      expect(vm.viewType).to.eq('thick');
    });
  });
  describe('Created', () => {});
  describe('Mounted', () => {
    it('should set pageTitle to All Devices', () => {
      expect(vm.pageTitle).to.eq('All Devices');
    });
  });
  describe('Computed', () => {
    describe('darkTheme', () => {
      it('should set ui/isDarkTheme in store to value provided', () => {
        vm.darkTheme = false;
        expect(store.getters['ui/isDarkTheme']).to.eq(false);
        vm.darkTheme = true;
        expect(vm.$store.getters['ui/isDarkTheme']).to.eq(true);
      });
      it('should set ui/isDashboardPage in store to value provided', () => {
        vm.isDashboardPage = false;
        expect(store.getters['ui/isDashboardPage']).to.eq(false);
        vm.isDashboardPage = true;
        expect(vm.$store.getters['ui/isDashboardPage']).to.eq(true);
      });
      it('should set ui/currentPageTitle in store to value provided', () => {
        vm.pageTitle = 'Simple title';
        expect(store.getters['ui/currentPageTitle']).to.eq('Simple title');
        vm.pageTitle = 'Just another title';
        expect(vm.$store.getters['ui/currentPageTitle']).to.eq('Just another title');
      });
    });
  });
  describe('Methods', () => {
    describe('renameFleet', () => {
      // it('should call show dialog method of deviceFleet component', () => {
      //   // vm.$refs.deviceGrp = deviceGrp;
      //   // console.log(vm.$refs.deviceGrp);
      //   // const spy = spyOn(vm.$refs.deviceGrp, 'showDialog');
      //   // vm.renameFleet();
      //   // expect(spy).toHaveBeenCalledWith(vm.selectedFleet);
      // });
    });
    describe('createUpdate', () => {
      it('should emit `dialogs:create-device-update:open` event with property show = true and the boolean parameter passed in', () => {
        // vm.$refs.deviceGrp = deviceGrp;
        // console.log(vm.$refs.deviceGrp);
        const spy = spyOn(vm.$events, '$emit');
        let isFleet = null;
        vm.createUpdate(isFleet);
        expect(spy).toHaveBeenCalledWith(`dialogs:create-device-update:open`, {
          show: true,
          isFleetUpdate: false,
        });
        isFleet = true;
        vm.createUpdate(isFleet);
        expect(spy).toHaveBeenCalledWith(`dialogs:create-device-update:open`, {
          show: true,
          isFleetUpdate: true,
        });
      });
    });
    describe('toggleFleets', () => {
      it("should set value of showFleets to the boolean opposite of it's value", () => {
        vm.showFleets = false;
        vm.toggleFleets();
        expect(vm.showFleets).to.eq(true);
        vm.toggleFleets();
        expect(vm.showFleets).to.eq(false);
      });
    });
    describe('setTitle', () => {
      it('should set value of pageTitle to `All Device` if selectedFleet is null or undefined', () => {
        vm.selectedFleet = null;
        vm.setTitle();
        expect(vm.pageTitle).to.eq('All Devices');
      });
      it('should set value of pageTitle to `Devices in "${groupName}"` if selectedFleet is NOT null or undefined', () => {
        vm.selectedFleet = { groupName: 'My Fleet' };
        vm.setTitle();
        expect(vm.pageTitle).to.eq('Devices in "' + vm.selectedFleet.groupName + '"');
      });
    });
    describe('viewTypeChanged', () => {
      it('should set value of viewType to passed value', () => {
        vm.viewType = 'card';
        vm.viewTypeChanged('thin');
        expect(vm.viewType).to.eq('thin');
      });
      it('should call method $redrawVueMasonry', () => {
        vm.viewType = 'card';
        jest.useFakeTimers();
        const spy = spyOn(vm, '$redrawVueMasonry');
        vm.viewTypeChanged('thin');
        expect(vm.viewType).to.eq('thin');
        jest.runAllTimers();
        expect(spy).toBeCalled();
      });
    });
    describe('viewSizeChanged', () => {
      it('should call method viewTypeChanged', () => {
        const spy = spyOn(vm, 'viewTypeChanged');
        vm.viewSizeChanged();
        expect(spy).toBeCalled();
      });
    });
  });
  describe('Markup', () => {
    describe('Main Wrapper', () => {});
  });
});
