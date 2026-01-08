/* eslint-disable */
/**
 * @jest-environment jsdom
 */

import { mount, createLocalVue, shallowMount } from '@vue/test-utils';

import $ from 'jquery';
import store from '../../../../src/store';
import DeviceQuickViewDialog from '../../../../src/components/devices/DeviceQuickViewDialog';
import { createSandbox } from 'sinon';
import VueRouter from 'vue-router';

import * as All from 'quasar';

const { Quasar, date, format } = All;

const components = Object.keys(All).reduce((object, key) => {
  const val = All[key];
  if (val && val.component && val.component.name != null) {
    object[key] = val;
  }
  return object;
}, {});

const sandbox = createSandbox();
describe('DeviceQuickViewDialog Component', () => {
  const localVue = createLocalVue();
  const mocks = {
    $router: {
      push: () => {},
    },
    $events: {
      $emit: () => {},
      $on: () => {},
    },
    $date: {
      formatDate: () => {},
    },
    $q: {
      screen: {
        lt: {},
        gt: {},
      },
    },
    device: {},
  };
  const stubs = {
    timeago: { template: '<div></div> ' },
    'q-dialog': { template: '<div></div> ' },
    'q-card-section': { template: '<div></div> ' },
  };
  localVue.use(Quasar, { components, store, VueRouter });

  const router = new VueRouter();

  const wrapper = mount(DeviceQuickViewDialog, {
    store,
    router,
    localVue,
    stubs,
    mocks,
  });

  const vm = wrapper.vm;
  const $parent = $(wrapper.find('.devices-wrapper').element);

  vm.$q = vm.$q || {};
  vm.$q.dialog = () => {
    return new Promise();
  };
  const defaultValues = {
    props: Object.assign({}, wrapper.props),
  };

  let fetchDevicesSpy;
  beforeEach(() => {
    wrapper.setProps(defaultValues.props);
  });
  afterEach(() => {
    wrapper.setProps(defaultValues.props);
    sandbox.restore();
  });
  // it('passes the sanity check and creates a wrapper', () => {
  //   expect(wrapper.isVueInstance()).toBe(true);
  // });
  it('Initializes device to an empty object', () => {
    expect(vm.device).to.deep.equal({});
  });
  describe('onMounted', () => {
    it('calls bindEventBusEvents', () => {
      const spy = spyOn(DeviceQuickViewDialog.methods, 'bindEventBusEvents');
      shallowMount(DeviceQuickViewDialog, {
        stubs,
        mocks,
        store,
      });
      expect(spy).toHaveBeenCalledTimes(1);
    });
  });
  it('Initializes device to an empty object', () => {
    expect(vm.device).to.deep.equal({});
  });

  describe('Methods', () => {
    describe('showFullDeviceDatail', () => {
      it('Calls router with prop.device ID', () => {
        wrapper.setData({
          device: {
            uuid: 'super-duper-id',
          },
        });
        const spy = spyOn(vm.$router, 'push');
        vm.showFullDeviceDatail();
        expect(spy).toHaveBeenCalledWith({
          name: 'device-detail',
          params: { deviceId: vm.device.uuid },
        });
      });
    });
    describe('bindEventBusEvents', () => {
      it('should bind to `component:show-device-detail:open` event', () => {
        const spy = spyOn(vm.$events, '$on');
        vm.bindEventBusEvents(true);
        expect(spy).toHaveBeenCalledWith('component:show-device-detail:open', vm.showModal);
      });
    });
    describe('showModal', () => {
      it('should set property requested to true', () => {
        vm.requested = false;
        vm.showModal({});
        expect(vm.requested).toBe(true);
      });
      it('should set property device to the device data passed to it', () => {
        const expected = { uuid: 'dest-device-uuid' };
        vm.showModal(expected);
        expect(vm.deviceUuid).to.deep.eq(expected.uuid);
      });
    });
  });
});
