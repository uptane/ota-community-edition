/* eslint-disable */
/**
 * @jest-environment jsdom
 */

import { mount, createLocalVue, shallowMount } from '@vue/test-utils';
import MainLayout from '../../../../src/layouts/MainLayout';
import store from '../../../../src/store';
import { createSandbox } from 'sinon';
import * as All from 'quasar';

jest.mock('../../../../src/services/auth.service');
jest.mock('../../../../src/secrets', () => Object.create({}));
jest.useFakeTimers();

const { Quasar, LocalStorage, SessionStorage, Screen, format, AddressbarColor } = All;

const components = Object.keys(All).reduce((object, key) => {
  const val = All[key];
  if (val && val.component && val.component.name != null) {
    object[key] = val;
  }
  return object;
}, {});
const sandbox = createSandbox();
describe('MainLayout Component', () => {
  const localVue = createLocalVue();
  const stubs = {
    'left-menu': { template: '<div></div> ' },
    'router-view': { template: '<div></div> ' },
    'create-device-dialog': { template: '<div></div> ' },
    'create-fleet-dialog': { template: '<div></div> ' },
    'q-dialog': { template: '<div></div> ' },
  };

  const testRoute = {};
  const routes = [testRoute];
  const testRouter = {
    push: () => {},
  };

  localVue.use(Quasar, {
    store,
    components,
    directives: All,
    plugins: { SessionStorage, Screen, AddressbarColor },
    // lang: langEn
  });
  const jqMethods = {
    addClass: () => {},
    removeClass: () => {},
  };

  const wrapper = shallowMount(MainLayout, {
    store,
    localVue,
    stubs,
    mocks: {
      appVersion: {},
      $jq: () => {
        return jqMethods;
      },
      $events: {
        $emit: () => {},
        $on: () => {},
      },
      $v: {
        pkg: {
          version: {
            $touch: false,
            $error: false,
          },
          packageName: {
            $touch: false,
            $error: false,
          },
        },
      },
    },
  });

  const vm = wrapper.vm;
  const defaultValues = {
    props: Object.assign({}, wrapper.props),
  };

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
  it('Initializes isMini property to true', () => {
    expect(vm.isMini).to.equal(true);
  });
  it('Initializes miniState property to true', () => {
    expect(vm.miniState).to.equal(true);
  });
  describe('Methods', () => {
    describe('openUrl', () => {
      it('should be a valid method', () => {
        expect(typeof vm.openURL).toBe('function');
      });
    });
    describe('toggleDarkMode', () => {
      it('should toggle app dark mode', () => {
        const spy = spyOn(vm.$q.dark, 'toggle');
        vm.toggleDarkMode(true);
        expect(spy).toHaveBeenCalled();
      });
    });
  });

  describe('toggleLeftMenu', () => {
    it('should set drawer state to true if miniBar property is truthy', () => {
      vm.miniBar = true;
      vm.toggleLeftMenu(true);
      expect(vm.drawerState).toBe(true);
    });
    it('should toggle miniState property if miniBar property is truthy', () => {
      vm.miniBar = true;
      const expected = !vm.miniState;
      vm.toggleLeftMenu(true);
      expect(vm.miniState).toBe(expected);
    });
    it('should set miniState property to false if isMini property is truthy', () => {
      vm.isMini = false;
      vm.drawerState = false;
      vm.toggleLeftMenu(true);
      expect(vm.miniState).toBe(false);
    });
    it('should toggle drawerState property if miniBar property is truthy', () => {
      vm.isMini = false;
      const expected = !vm.drawerState;
      vm.toggleLeftMenu(true);
      expect(vm.drawerState).toBe(expected);
    });
  });
  describe('Computed', () => {
    describe('leftDrawerOpen', () => {
      it('should return the value of drawerState', () => {
        store.commit('ui/setIsLeftDrawerOpen', true);
        expect(vm.drawerState).toBe(true);
      });
      it('should set the value of isLeftDrawerOpen state', () => {
        vm.drawerState = false;
        expect(store.getters['ui/isLeftDrawerOpen']).toBe(false);
        vm.drawerState = true;
        expect(store.getters['ui/isLeftDrawerOpen']).toBe(true);
      });
    });
  });
});
