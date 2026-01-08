/* eslint-disable */
/**
 * @jest-environment jsdom
 */

import { mount, createLocalVue, shallowMount } from '@vue/test-utils';

import _ from 'lodash';
import sinon from 'sinon';
import Vue from 'vue';
import Vuex from 'vuex';
import $ from 'jquery';
import TPackages from '../../../../src/components/packages/Packages';
import { createSandbox } from 'sinon';
import VueRouter from 'vue-router';
import { storeModules } from '../mocks/store';

import * as All from 'quasar';
import { SessionStorage } from 'quasar';

jest.mock('src/services/tdx.keycloak.client');
jest.useFakeTimers();

const { Quasar, date, format } = All;

const components = Object.keys(All).reduce((object, key) => {
  const val = All[key];
  if (val && val.component && val.component.name != null) {
    object[key] = val;
  }
  return object;
}, {});

const sandbox = createSandbox();
const originalFunc = TPackages.methods.getPackages;

const localVue = createLocalVue();
localVue.use(Vuex);

const store = new Vuex.Store({
  modules: storeModules,
});
const mocks = {
  $date: {
    formatDate: () => {},
  },
  $router: {
    push: jest.fn(),
    replace: jest.fn(),
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
  $date: date,
  $q: {
    dialog: () => {
      return new Promise();
    },
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
const stubs = {
  timeago: { template: '<div></div> ' },
  'q-dialog': { template: '<div></div> ' },
  'q-card-section': { template: '<div></div> ' },
  'q-separator': { template: '<div></div> ' },
  'q-table': { template: '<div></div> ' },
  'q-input': { template: '<div></div> ' },
  'q-spinner-hourglass': { template: '<div></div> ' },
  'q-btn': { template: '<div></div> ' },
  'q-card': { template: '<div class="q-card"></div>' },
};
localVue.use(Quasar, {
  store,
  components,
  directives: All,
  plugins: { SessionStorage },
});

let viewType = 'table';
let propsData = {
  packageData: {},
  viewType,
};
const router = new VueRouter();
let wrapper, vm;
let onPageLoadSpy;
let stubPageLoad = true;

const resetTest = (props, storeData) => {
  propsData = props || {
    packageData: {},
    viewType,
  };
  sandbox.restore();
  if (stubPageLoad) {
    onPageLoadSpy = sandbox.stub(TPackages.methods, `onPageLoad`).callsFake(() => {});
  }
  wrapper = mount(TPackages, {
    propsData,
    store: new Vuex.Store({
      modules: _.merge(storeModules, storeData),
    }),
    router,
    localVue,
    stubs,
    mocks,
  });
  vm = wrapper.vm;
};
describe('Packages Component', () => {
  beforeEach(() => {
    resetTest();
  });
  afterEach(() => {
    sandbox.restore();
    vm = null;
    wrapper = null;
    stubPageLoad = true;
  });
  // it('passes the sanity check and creates a wrapper', () => {
  //   expect(wrapper.isVueInstance()).toBe(true);
  // });
  describe('Initialize Defaults', () => {
    it('Initializes `title` to `Package List`', () => {
      expect(vm.title).to.equal('Package List');
    });
    it('Initializes `query` to an empty string', () => {
      expect(vm.query).to.equal('');
    });
    it('Initializes `limit` to 50', () => {
      expect(vm.limit).to.equal(50);
    });
    it('Initializes `viewType` to `table`', () => {
      expect(vm.viewType).to.equal('table');
    });
    it('Initializes `contentType` to `all`', () => {
      expect(vm.contentType).to.equal('all');
    });
    it('Initializes `selectedPackage` to `null`', () => {
      expect(vm.selectedPackage).to.equal(null);
    });
    it('Initializes `filter` to `null`', () => {
      expect(vm.filter).to.equal(null);
    });
  });
  describe('Created', () => {});
  describe('Mounted', () => {
    it('sets pageTitle to `Packages`', () => {
      expect(vm.pageTitle).to.eq('Packages');
    });
  });
  describe('Computed', () => {
    const testData = { test: true };
    describe('Loading', () => {
      it('should set ui/loadingPackages to provided value', () => {
        vm.loading = testData;
        expect(vm.$store.getters['ui/loadingPackages']).to.eq(testData);
      });
      it('should return the value of ui/loadingPackages', () => {
        vm.loading = true;
        vm.$store.commit('ui/setLoadingPackages', testData);
        expect(vm.loading).to.eq(testData);
      });
    });
  });
  describe('Methods', () => {
    describe('createPackage', () => {
      it('should emit EventBus event to `dialogs:create-package:open` with data', () => {
        const spy = spyOn(vm.$events, '$emit');
        const data = {
          show: true,
          existing: { pid: 'just-something-random' },
        };
        vm.createPackage(data.existing);
        expect(spy).toHaveBeenCalledWith('dialogs:create-package:open', data);
      });
    });
    describe('getPackages', () => {
      it('should `loading` property to true', () => {
        expect.assertions(1);
        vm.loading = false;
        vm.getPackages();
        expect(vm.loading).toBe(true);
      });
      it('should call `fetchPackages()`', () => {
        expect.assertions(1);
        const spy = sandbox.stub(vm, `fetchPackages`).resolves({});
        vm.getPackages();
        expect(spy.calledOnce).toBe(true);
      });
      it('should set `loading` property to false if `fetchPackages()` is resolved', () => {
        expect.assertions(1);
        TPackages.methods.getPackages = originalFunc;
        jest.useFakeTimers();
        resetTest();
        sandbox.stub(vm, `fetchPackages`).resolves({});
        vm.loading = true;
        // promise.finally is not supported so using then.catch.then instead
        vm.getPackages()
          .then()
          .catch()
          .then((a) => {
            expect(vm.loading).toBe(false);
          });
        jest.runAllTimers();
      });
    });

    describe('selectPackage', () => {
      it('should set selected packages to null if the same package is already selected', () => {
        const pkg = { id: 'testPackage', versionId: '1.2.3', filepath: 'testPackage-1.2.3' };
        vm.selectedPackage = pkg;
        vm.selectPackage(pkg);
        expect(vm.selectedPackage).to.eq(null);
      });
      it('should call router.replace with current route path property if the same package is already selected', () => {
        const pkg = { id: 'testPackage', versionId: '1.2.3', name: 'fancy' };
        const spy = spyOn(vm.$router, 'replace');
        vm.selectedPackage = { ...pkg, versionId: 'a.b.c' };
        vm.selectPackage(pkg);
        expect(spy).toHaveBeenCalledWith(vm.$route.path + '?name=' + pkg.name, expect.any(Function));
      });
      it('should set selected packages to the package provided if package is not already selected', () => {
        const pkg = { id: 'testPackage', versionId: '1.2.3' };
        vm.selectedPackage = {};
        vm.selectPackage(pkg);
        expect(vm.selectedPackage).to.eq(pkg);
      });
      it('should call router.replace with a string generated from current route path and package name package provided if package is not already selected', () => {
        const pkg = {
          id: 'testPackage',
          versionId: '1.2.3',
          name: 'test-name',
        };
        const spy = spyOn(vm.$router, 'replace');
        vm.selectedPackage = {};
        vm.selectPackage(pkg);
        expect(spy).toHaveBeenCalledWith(vm.$route.path + '?name=' + pkg.name, expect.anything());
      });
    });

    describe('viewPackageDetail', () => {
      it('should call router.push with parameters ffrom the supplied package', () => {
        const pkg = {
          name: 'testPackage',
          versionId: '1.2.3',
          name: 'test-name',
        };
        const spy = spyOn(vm.$router, 'push');
        vm.selectedPackage = {};
        vm.viewPackageDetail(pkg);
        expect(spy).toHaveBeenCalledWith({
          name: 'packages',
          query: { name: pkg.name },
        });
      });
    });
    describe('onPageLoad', () => {
      beforeAll(() => {
        stubPageLoad = false;
      });
      it('should do nothing if query.name is missing', () => {
        const pkg = {
          name: 'testPackage',
          versionId: '1.2.3',
          hardwareType: 'testPackage',
          name: 'test-name',
        };

        vm.$store.commit('packages/setPackages', [pkg]);
        vm.$route.query = {};
        vm.selectedPackage = null;
        vm.onPageLoad();
        expect(vm.selectedPackage).to.deep.eq(null);
      });
    });
    describe('paginationLabel', () => {
      it('should  return a short version of string with passed parameters when  package is selected', () => {
        const start = 123,
          end = 'abc',
          total = 'some string';
        const text = `${start} → ${end} / ${total}`;
        vm.selectedPackage = {};
        expect(vm.paginationLabel(start, end, total)).to.eq(text);
      });
      it('should return a long version of string with passed parameters when no package is selected', () => {
        const start = 123,
          end = 'abc',
          total = 'some string';
        const text = `Showing ${start}  to  ${end}  of  ${total}`;
        vm.selectedPackage = null;
        expect(vm.paginationLabel(start, end, total)).to.eq(text);
      });
    });
  });
  describe('Markup', () => {
    describe('Main Wrapper', () => {
      it('should be a div with no css class or style', () => {
        expect(wrapper.element.tagName).toBe('DIV');
        expect($(wrapper.element).attr('class')).toBeFalsy();
        expect($(wrapper.element).attr('style')).toBeFalsy();
      });
    });
    describe('Table View', () => {
      beforeEach(() => {
        viewType = 'table';
        resetTest();
      });
      it('should be a div without q-card css class', () => {
        expect($(wrapper.element).find('div.q-card.table-view').length).to.eq(0);
        expect($(wrapper.element).find('div.table-view').length).to.eq(1);
      });
    });
  });
});
