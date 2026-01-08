/* eslint-disable */
/**
 * @jest-environment jsdom
 */

import Vuex from 'vuex';
import { mount, createLocalVue, shallowMount } from '@vue/test-utils';
import $ from 'jquery';
import { storeModules } from '../mocks/store';
import DeviceItem from '../../../../src/components/devices/DeviceItem';
import { createSandbox } from 'sinon';
import * as All from 'quasar';

const { Quasar, LocalStorage, Screen, format } = All;

const components = Object.keys(All).reduce((object, key) => {
  const val = All[key];
  if (val && val.component && val.component.name != null) {
    object[key] = val;
  }
  return object;
}, {});

const sandbox = createSandbox();
describe.skip('Device Item Component', () => {
  const localVue = createLocalVue();
  localVue.use(Vuex);
  const store = new Vuex.Store({
    modules: storeModules,
  });
  const stubs = {
    timeago: { template: '<div></div> ' },
    'q-menu': { template: '<div><device-menu></device-menu></div>' },
    'device-menu': { template: '<div></div> ' },
    'q-card-section': { template: '<div></div> ' },
    'update-status-indicator': { template: '<div></div> ' },
    'online-badge': { template: '<div></div> ' },
  };
  localVue.use(Quasar, { components, directives: All, plugins: { LocalStorage, Screen } });
  const mocks = {
    $date: {
      formatDate: () => {},
    },
    $router: {
      push: () => {},
    },
    $route: {
      params: {},
    },
    $events: {
      $emit: () => {},
      $on: () => {},
    },
    platform: {
      is: {},
    },
  };
  let wrapper, vm, $parent;

  const menuItems = [
    {
      text: 'View devices',
      action: () => {},
    },
  ];
  const resetTest = (props) => {
    wrapper = mount(DeviceItem, {
      propsData: props || {},
      store,
      localVue,
      mocks,
      stubs,
    });
    vm = wrapper.vm;

    vm.$events = {
      $emit: () => {},
      $on: () => {},
    };
    vm.$q = {};
    vm.$q.dialog = () => {
      return new Promise();
    };
    $parent = $parent = $(wrapper.find('.device-item-wrapper').element);
  };
  resetTest();
  beforeEach(() => {
    resetTest();
    sandbox.restore();
  });
  // it('passes the sanity check and creates a wrapper', () => {
  //   expect(wrapper.isVueInstance()).toBe(true);
  // });
  it('Set default device object to {} (empty non-null)', () => {
    expect(vm.device).to.deep.equal({});
  });
  it('calls showDeviceDatail() when clicked', () => {
    const spy = spyOn(vm, 'showDeviceDatail');
    $parent.trigger('click');
    expect(spy).toHaveBeenCalled();
  });
  it('Contains a q-item child', () => {
    expect($parent.find('.q-item').length).to.eq(1);
  });
  it('Contains a q-item-section child width the q-item that holds the icon image', () => {
    const item = $parent.find('.q-item .q-item__section--avatar');
    expect(item.find('img').length).to.eq(1);
    expect(item.find('img').attr('src')).to.eq('statics/svg/icons/som.svg');
  });

  it('Contains an image avatar with correct size src url', () => {
    expect($parent.find('img').attr('src')).to.eq('statics/svg/icons/som.svg');
  });
  describe('Computed:', () => {});

  describe('devicesUpdating:', () => {
    it('returns an object matching currect device if found', () => {
      const device = { uuid: 'uuid-234' };
      let getters = {
        'ui/devicesWithUpdateInProgress': () => {
          return {
            [device.uuid]: device,
          };
        },
      };
      let str = new Vuex.Store({
        getters,
      });
      const wrp = shallowMount(DeviceItem, {
        store: str,
        localVue,
        propsData: {
          device: device,
        },
        stubs,
        mocks,
      });
      expect(wrp.vm.deviceUpdating).toBe(device);
    });
    it('returns a falsy value if device is not found', () => {
      const device = { uuid: 'uuid-234' };
      let getters = {
        'ui/devicesWithUpdateInProgress': () => {
          return {
            'somethin-different': device,
          };
        },
      };
      let str = new Vuex.Store({
        getters,
      });
      const wrp = shallowMount(DeviceItem, {
        store: str,
        localVue,
        propsData: {
          device: device,
        },
        stubs,
        mocks,
      });
      expect(wrp.vm.deviceUpdating).toBeFalsy();
    });
    // it('shows "updating..." text and a spinner loader if deviceUpdating is avaliable', ()=>{
    //   sandbox.stub(DeviceItem.computed, 'deviceUpdating').returns({'test-uuid':{test: true}});
    //     const wrp = mount(DeviceItem, { store, localVue ,
    //       stubs: componentStubs});
    //     // expect($(wrp.find('.device-item-wrapper').element).find('.updating-text').length).toBe(1);
    //     console.log('ELEm', $(wrp.find('.device-item-wrapper').element).find('.updating-text').text().trim());
    //     expect($(wrp.find('.device-item-wrapper').element).find('.updating-text').text().trim()).toBe('Updating...');
    //     expect($(wrp.find('.device-item-wrapper').element).find('.updating-text .q-spinner').length).toBe(1);
    // });
    it('hides "updating..." text and a spinner loader if devicesUpdating is NOT available', () => {
      sandbox.stub(DeviceItem.computed, 'deviceUpdating').returns(undefined);
      const wrp = mount(DeviceItem, {
        store,
        localVue,
        stubs,
        propsData: {
          device: {
            uuid: 'test-uuid',
          },
        },
      });
      expect($(wrp.find('.device-item-wrapper').element).find('.updating-text').length).toBe(0);
      // expect($(wrp.find('.device-item-wrapper').element).find('.updating-text .q-icon').text()).toBe('Updating...');
      // expect($(wrp.find('.device-item-wrapper').element).find('.updating-text .q-spinner').length).toBe(1);
    });
  });

  describe('device info', () => {
    it('shows device name as the main text', () => {
      const prop = {
        device: {
          deviceName: 'My Test Device',
          deviceId: 'some unique device id',
        },
      };
      resetTest(prop);
      expect($parent.find('.label.device-name').text()).toBe(prop.device.deviceName);
    });
    it('shows device id as the main text', () => {
      const prop = {
        device: {
          deviceName: 'My Test Device',
          deviceId: 'some unique device id',
        },
      };
      resetTest(prop);
      expect($parent.find('.sublabel.device-id').text()).toBe('ID: ' + prop.device.deviceId);
    });
    it('SHOWS device last seen status as "Never" if deviceStatus is "NotSeen" and ViewType NOT equal to "thin" and layoutType NOT equal to "list"', () => {
      const prop = {
        device: {
          deviceStatus: 'NotSeen',
        },
        viewType: 'thick',
        layoutType: 'cards',
      };
      resetTest(prop);
      expect($parent.find('.sublabel.last-seen').text()).toBe('Last seen: Never');
    });
    // it('HIDES device last seen status as "Never" if deviceStatus is "NotSeen" and ViewType equal to "thin"', ()=>{
    //   const prop = {
    //     device:{
    //       deviceStatus: 'NotSeen',
    //     },
    //     viewType: 'thin',
    //     layoutType: 'list',
    //   };
    //     resetTest(prop);
    //     // vm.$nextTick();
    //     expect($parent.find('.sublabel.last-seen').text()).toBe('');
    //     // prop.viewType = 'thick';
    //     // prop.layoutType = 'list';
    //     // resetTest(prop);
    //     // expect($parent.find('.sublabel.last-seen').text()).toBe('');
    // });
    it('shows device last seen status as "time ago" is deviceStatus is anything but "NotSeen"', () => {
      const prop = {
        device: {
          deviceStatus: 'Seen',
          lastSeen: Date.now() - 2 * 60 * 1000,
        },
      };
      sandbox.stub(DeviceItem.computed, 'deviceUpdating').returns(undefined);
      const wrp = shallowMount(DeviceItem, {
        store,
        localVue,
        stubs: {
          timeago: { template: '<span>' + prop.device.lastSeen + ' min ago</span>' },
          'q-menu': { template: '<div></div> ' },
        },
        mocks,
      });
      wrp.setProps(prop);
      expect(
        $(wrp.find('.device-item-wrapper').element)
          .find('.sublabel.last-seen')
          .text()
          .replace(/\n/g, ' ')
          .replace(/\s+/g, ' '),
      ).toBe('Last seen: ' + wrp.vm.device.lastSeen + ' min ago');
    });
  });

  describe('Methods', () => {
    describe('showDeviceDatail', () => {
      it('emits a "component:show-device-detail:open" event with device data', () => {
        const spy = spyOn(vm.$events, '$emit');
        vm.showDeviceDatail();
        expect(spy).toHaveBeenCalledWith('component:show-device-detail:open', vm.device);
      });
    });
  });
  describe('Markup', () => {
    describe('Options section', () => {
      it('should prevent click event propagtion', () => {
        const el = $(wrapper).find('option-section');
      });
    });
  });

  it('renders correctly', () => {
    expect(wrapper.html()).toMatchSnapshot();
  });
});
