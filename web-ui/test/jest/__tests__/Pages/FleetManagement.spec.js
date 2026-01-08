import Vue from 'vue';
import All from 'quasar';
import $ from 'jquery';
import { createSandbox } from 'sinon';
import { Wrapper, mount, createLocalVue, shallowMount } from '@vue/test-utils';
import FleetManager from '../../../../src/pages/FleetManager';

const { Quasar, date, format } = All;
const sandbox = createSandbox();
const mocks = {
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
};
const components = Object.keys(All).reduce((object, key) => {
  const val = All[key];
  if (val && val.component && val.component.name != null) {
    object[key] = val;
  }
  return object;
}, {});

/** @type Wrapper */
let wrapper;
/** @type Vue */
let vm;
/** @type $ */
let $parent;
let propsData = {};
describe('FleetManager', () => {
  const localVue = createLocalVue();
  const makeWrapper = (propsData = {}) => {
    localVue.use(Quasar, { components }); // , lang: langEn
    wrapper = shallowMount(FleetManager, {
      mocks,
      localVue,
      propsData,
    });
    vm = wrapper.vm;
    // $parent = jQuery(wrapper.element);
  };
  const cleanUp = (propsData = {}) => {
    wrapper = null;
    vm = null;
    // $parent = jQuery(wrapper.element);
  };
  beforeEach(() => {
    makeWrapper();
  });
  afterEach(() => {
    sandbox.restore();
    cleanUp();
  });
  // it('passes the sanity check and creates a wrapper', () => {
  //     expect(wrapper.isVueInstance()).toBe(true);
  // });

  describe('Markup', () => {
    describe('Main Content Sections', () => {
      it.todo('should render `Available fleets` and `Selected device` if  a fleet is selected and device is NOT selected');
      it.todo('should render `Available fleets` and `Selected device` if  both fleet and device are selected');
    });
    describe('Available devices', () => {
      it.todo('should have title `Available devices`');
      it.todo('should have a list item with total items that equals the number of devices in `devices` property ');
      describe('List items', () => {
        it.todo('should render device name that corresponds to `deviceName` property of the device');
        it.todo('should render device id that corresponds to `deviceId` property of the device');
        it.todo('should render number of fleets the device belongs to in the format `Belongs to n devices`');
        it.todo('should have action button with label `Add to current fleet`');
        describe('Btn Add to current fleet', () => {
          it.todo('should have positive theme');
          it.todo('should call `addToFleet` method on click with the device as parameter');
        });
      });
    });
  });
  describe('mounted', () => {
    it.todo('should fetch device if deviceId is present in url query');
    it.todo('should fetch fleet if fleetId is present in url query');
    it.todo('should fetch device and ignore fleet if both fleetId and deviceId are present in url query');
  });
  describe('Methods', () => {
    describe('selectDevice', () => {
      it.todo('should do nothing if device parameter is null');
      it.todo('should do nothing if device parameter has no id');
      it.todo('should set device parameter as selectedDevice');
      it.todo('should set selectedFleet to null');
    });
    describe('selectFleet', () => {
      it.todo('should do nothing if fleet parameter is null');
      it.todo('should do nothing if fleet parameter has no id');
      it.todo('should set fleet parameter as selectedFleet');
      it.todo('should set selectedDevice to null');
    });
  });
});
