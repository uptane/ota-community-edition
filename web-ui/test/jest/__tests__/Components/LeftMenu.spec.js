/* eslint-disable */
/**
 * @jest-environment jsdom
 */

import { mount, createLocalVue } from '@vue/test-utils';
import $ from 'jquery';
import store from '../../../../src/store';
import LeftMenu from '../../../../src/components/menus/LeftMenu';
import { createSandbox } from 'sinon';
import * as All from 'quasar';

jest.mock('../../../../src/services/auth.service');
jest.mock('../../../../src/secrets', () => Object.create({}));

// const myMock = jest.fn();
// console.log(myMock());
// // > undefined

// myMock
//   .mockReturnValueOnce(10)
//   .mockReturnValueOnce('x')
//   .mockReturnValue(true);

// console.log(myMock(), myMock(), myMock(), myMock());

const { Quasar } = All;

const components = Object.keys(All).reduce((object, key) => {
  const val = All[key];
  if (val && val.component && val.component.name != null) {
    object[key] = val;
  }
  return object;
}, {});
const directives = Object.keys(All).reduce((object, key) => {
  const val = All[key];
  // console.log(val.directive)
  if (val && val.directive) {
    object[key] = val;
  }
  return object;
}, {});

const sandbox = createSandbox();
describe('LeftMenu Component', () => {
  const localVue = createLocalVue();
  const componentStubs = {
    // 'router-link': '<div><slot></slot></div>',
    // 'v-ripple': { template: "<div></div> "},
    // 'q-list': { template: "<div></div> "},
  };

  const testRoute = {
    name: '',
    path: '',
  };
  let mockRouterPush = new Promise((resolve, reject) => {
    resolve({});
  });
  const routes = [testRoute];
  // localVue.use(VueRouter)
  // const testRouter = new VueRouter()
  let testRouter = {
    push: () => {
      return mockRouterPush;
    },
    resolve: () => {},
  };
  localVue.use(Quasar, {
    store,
    components,
    directives: All,
  });

  const wrapper = mount(LeftMenu, {
    store,
    localVue,
    // directives,
    // directives: {
    //   Ripple: {
    //     inserted: (el) => {},
    //   },
    // 'to':
    //     {
    //       inserted: function (el) {
    //     }
    //   }
    // },
    router: testRouter,
    stubs: componentStubs,
    mocks: {
      appVersion: {},
      $router: testRouter,
      $route: testRoute,
      $events: {
        $emit: () => {},
        $on: () => {},
      },
    },
  });

  const vm = wrapper.vm;
  const $parent = $(wrapper.find('.device-fleets-wrapper').element);

  // vm.$q = {

  // };
  // vm.$q.dialog = () => {
  //   return new Promise();
  // };
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
  it('Initializes menu items to non-empty array', () => {
    expect(vm.menuItems.length).to.be.above(0);
  });
  it('Initializes miniBar property to true', () => {
    expect(vm.miniBar).to.equal(true);
  });
  it('Initializes miniState property to true', () => {
    expect(vm.miniState).to.equal(true);
  });

  describe('MenuItems', () => {
    const el = $(wrapper.element);
    it('Renders menu items that equals length of menuItems array', () => {
      expect(el.find('.q-item').length).to.deep.equal(vm.menuItems.filter((menu) => !menu.hide || (menu.hide && !menu.hide())).length);
    });
    it('Renders corresponding text label for each menu items', () => {
      vm.menuItems
        .filter((menu) => !menu.hide || (menu.hide && !menu.hide()))
        .forEach((v, i) => {
          expect($($(el.find('.q-item')[i]).find('.q-item__label')[0]).text()).to.equal(v.label);
          expect(
            $(el.find('.q-item')[i])
              .find('.q-item__label--caption')
              .text(),
          ).to.equal(v.sublabel);
        });
    });
    it('Contains active class when it is the current path', () => {
      vm.menuItems.forEach((v, i) => {
        testRoute.name = v.routeName;
        // expect($(el.find('.q-item')[i]).hasClass('active')).to.equal(true);
      });
    });
    it('Navigates to route defined in menu route property when clicked', () => {
      const spy = spyOn(testRouter, 'push');
      vm.menuItems
        .filter((menu) => !menu.hide || (menu.hide && !menu.hide()))
        .forEach((v, i) => {
          if (!v.action && v.route) {
            el.find('.q-item')[i].click();
            expect(spy).toHaveBeenCalledWith(v.route);
          }
        });
    });
  });

  describe('Methods', () => {});
  describe('Computed', () => {
    describe('leftDrawerOpen', () => {
      it('should return the value of isLeftDrawerOpen state', () => {
        store.commit('ui/setIsLeftDrawerOpen', true);
        expect(vm.leftDrawerOpen).toBe(true);
      });
      it('should set the value of isLeftDrawerOpen state', () => {
        vm.leftDrawerOpen = false;
        expect(store.getters['ui/isLeftDrawerOpen']).toBe(false);
        vm.leftDrawerOpen = true;
        expect(store.getters['ui/isLeftDrawerOpen']).toBe(true);
      });
    });
  });
});
