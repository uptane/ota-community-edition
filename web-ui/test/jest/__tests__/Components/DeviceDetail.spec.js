/* eslint-disable */
/**
 * @jest-environment jsdom
 */

import { mount, createLocalVue } from '@vue/test-utils';
import store from '../../../../src/store';
import DeviceDetail from '../../../../src/components/devices/DeviceDetail';
import { createSandbox } from 'sinon';
import * as All from 'quasar';
// import { route } from 'quasar/wrappers';
import { mountFactory, mountQuasar } from '@quasar/quasar-app-extension-testing-unit-jest';

const { Quasar, LocalStorage, format, Screen } = All;

const components = Object.keys(All).reduce((object, key) => {
  const val = All[key];
  if (val && val.component && val.component.name != null) {
    object[key] = val;
  }
  return object;
}, {});
const stubs = {
  timeago: { template: '<div></div> ' },
  'q-dialog': { template: '<div></div> ' },
  'q-card-section': { template: '<div></div> ' },
  'online-badge': { template: '<div></div> ' },
  loader: { template: '<div></div> ' },
  'os-image-updates': { template: '<div></div> ' },
  'container-image-updates': { template: '<div></div> ' },
  'device-package-versions': { template: '<div></div> ' },
  'update-status-indicator': { template: '<div></div> ' },
};
const factory = mountFactory(DeviceDetail, {
  // mount: { type: 'full' } <= uncomment this line to use `mount`; `shallowMount` is used by default as it will stub all **registered** components found into the template
  quasar: { components },
});

let vm, wrapper, defaultValues;
const setupTest = (props, data, route) => {
  const wrapper = mountQuasar(DeviceDetail, {
    quasar: {
      components,
    },
    propsData: props,
    plugins: {},
  });
  // wrapper = factory();
  // Vue.use(Quasar)
  const localVue = createLocalVue();
  localVue.use(Quasar, { components, plugins: { LocalStorage, Screen } });
  route = route || {
    params: {},
  };
  // const router = new VueRouter();
  let deviceData = null;
  data =
    data ||
    (() => {
      return {};
    });
  const mocks = {
    $date: {
      formatDate: () => {},
    },
    $route: route,
    $events: {
      $emit: () => {},
      $on: () => {},
    },
  };

  wrapper = mount(DeviceDetail, {
    store,
    propsData: props,
    localVue,
    stubs,
    mocks,
  });
  defaultValues = {
    props: Object.assign({}, wrapper.props),
  };
  vm = wrapper.vm;
};
const sandbox = createSandbox();
describe.skip('DeviceDetail Component', () => {
  beforeEach(() => {
    setupTest();
  });
  afterEach(() => {
    sandbox.restore();
  });

  describe('onMounted', () => {
    it('calls setup method', () => {
      const spy = sandbox.stub(DeviceDetail.methods, 'setup').callsFake(() => {});
      mount(DeviceDetail, {
        store,
        stubs,
        localVue,
        mocks,
      });
      expect(spy.calledOnce).toBe(true);
    });
  });
  describe('Props and Defaults', () => {
    it('deviceData defaults to null', () => {
      console.log('VM', vm);
      expect(vm.deviceData).toBe(null);
    });
    it('quickView defaults to false', () => {
      expect(vm.quickView).toBe(false);
    });
  });
  describe('Methods', () => {
    describe('setup', () => {
      it('Sets pageTitle to `Device Information`', () => {
        vm.pageTitle = 'Nothing';
        vm.setup();
        expect(vm.pageTitle).to.eq('Device Information');
      });
      it('Sets `device` property to value of `deviceData` property if `deviceData` property is truthy', () => {
        deviceData = { uuid: 'some-valid-uuid' };
        const w = mount(DeviceDetail, {
          store,
          propsData: {
            deviceData,
          },
          stubs,
          localVue,
          mocks,
        });
        w.vm.device = null;
        w.vm.setup();
        expect(w.vm.device).to.deep.eq(w.vm.deviceData);
      });
      it('Sets `device` property to value of `{}` if `deviceData` property is falsy', () => {
        // deviceData = null;
        const w = mount(DeviceDetail, {
          store,
          // propsData: {
          //   deviceData
          // },
          stubs,
          localVue,
          mocks,
        });
        w.vm.device = { d: 'something random' };
        w.vm.setup();
        expect(w.vm.device).to.deep.eq({});
        expect(w.vm.deviceData).to.deep.eq({});
      });
      it('Call `getDevice` method', () => {
        const spy = spyOn(vm, 'getDevice');
        vm.setup();
        expect(spy).toHaveBeenCalledTimes(1);
      });
    });
    describe('getDevice', () => {
      it('Sets loading flag to false', () => {
        vm.deviceUuid = null;
        vm.loading = true;
        vm.getDevice();
        expect(vm.loading).to.eq(false);
      });
      it('Must not call fetchDevice if deviceUuid is empty', () => {
        const route = { params: { deviceId: 'anything' } };
        setupTest(null, null, route);
        vm.loading = true;
        const stub = sandbox.stub(vm, 'fetchDevice').resolves({});
        const spy = spyOn(vm, 'fetchDevice');
        vm.getDevice();
        expect(spy).toHaveBeenCalledTimes(1);
      });
      it('Keeps `device` property value as long as it has `uuid`', () => {
        const sample = { uuid: 'some-valid-uuid', super: true };
        vm.device = sample;
        vm.getDevice();
        expect(vm.device).to.deep.eq(sample);
      });
      it('Sets `device` property to the value of `deviceData` property if it has no `uuid`', () => {
        const sample = { 'no-uuid': 'some-invalid-uuid', super: true };
        vm.device = sample;
        vm.getDevice();
        expect(vm.device).to.deep.eq({});
      });
    });
    //   describe('bindEventBusEvents', () => {
    //     it('should bind to `component:show-device-detail:open` event', () => {
    //       const spy = spyOn(vm.$events, '$on');
    //       vm.bindEventBusEvents(true);
    //       expect(spy).toHaveBeenCalledWith(
    //         'component:show-device-detail:open',
    //         vm.showModal,
    //       );
    //     });
    //   });
    //   describe('showModal', () => {
    //     it('should set property show to true', () => {
    //       vm.show = false;
    //       vm.showModal();
    //       expect(vm.show).toBe(true);
    //     });
    //     it('should set property device to the device data passed to it', () => {
    //       const expected = {uuid: 'dest-device-uuid'};
    //       vm.device = {};
    //       vm.showModal(expected);
    //       expect(vm.device).to.deep.eq(expected);
    //     });
    //   });
  });
});
