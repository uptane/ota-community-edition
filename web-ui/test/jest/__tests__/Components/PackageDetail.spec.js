/* eslint-disable */
/**
 * @jest-environment jsdom
 */

import { mount, createLocalVue, shallowMount } from '@vue/test-utils';

import store from '../../../../src/store';
import PackageDetail from '../../../../src/components/packages/PackageDetail';
import { createSandbox } from 'sinon';
import VueRouter from 'vue-router';
import { QChip } from 'quasar';

import * as All from 'quasar';

const { Quasar, format, date } = All;

const components = Object.keys(All).reduce((object, key) => {
  const val = All[key];
  if (val && val.component && val.component.name != null) {
    object[key] = val;
  }
  return object;
}, {});

const sandbox = createSandbox();
describe.skip('PackageDetail Component', () => {
  const localVue = createLocalVue();
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
    $format: format,
    $date: date,
  };
  const stubs = {
    timeago: { template: '<div></div> ' },
    'q-dialog': { template: '<div></div> ' },
    'q-card-section': { template: '<div></div> ' },
    'q-ajax-bar': { template: '<div></div> ' },
  };
  localVue.use(Quasar, {
    store,
    VueRouter,
    components,
    mocks,
  });

  const router = new VueRouter();
  let propsData = {
    packageData: {},
  };

  let wrapper, vm;
  let hardwareIds = ['super-cool-id'];
  let dependencies = ['super-awesome-dep'];
  const defaultVersion = {
    createdAt: Date.now() - 200000,
    updatedAt: Date.now(),
    targetLength: Math.random() + 2000,
    installedOnEcus: 2,
    hardwareIds: hardwareIds,
    dependencies: dependencies,
    packageHash: 'some-really-nice-long-hassshhhh',
    id: {
      version: '2.1.2',
      name: 'test-name',
    },
  };
  const resetTest = (props) => {
    wrapper = mount(PackageDetail, {
      store,
      propsData: props || propsData,
      router,
      localVue,
      // stubs,
      mocks,
    });
    vm = wrapper.vm;

    vm.$q = {};
    vm.$q.dialog = () => {
      return new Promise();
    };
  };
  resetTest();
  beforeEach(() => {
    resetTest();
    // wrapper.setProps(propsData);
  });
  afterEach(() => {
    // wrapper.setProps(defaultValues.props);
    sandbox.restore();
  });
  // it('passes the sanity check and creates a wrapper', () => {
  //   expect(wrapper.isVueInstance()).toBe(true);
  // });
  it('Initializes `packageData` to an empty object', () => {
    expect(vm.packageData).to.deep.equal({});
  });
  describe('Markup', () => {
    describe('Header Wrapper', () => {
      it('Exists', () => {
        expect(wrapper.find('.header-wrapper').exists()).to.eq(true);
      });
      it('Contains a header with specific margin and text', () => {
        expect(wrapper.find('.header-wrapper h6').exists()).to.eq(true);
        expect(wrapper.find('.header-wrapper h6').text()).to.eq('Distribution');
        expect(wrapper.find('.header-wrapper h6').classes()).to.contain('mt-0');
        expect(wrapper.find('.header-wrapper h6').classes()).to.contain('mb-1');
      });
      it('Renders `This package has not been installed yet.` with class `opacity-40` if `installedOnEcus` property is NOT present on `packageData`', () => {
        wrapper.setProps({ packageData: { installedOnEcus: null } });
        expect(wrapper.find('.header-wrapper .not-on-ecu').exists()).to.eq(true);
        expect(wrapper.find('.header-wrapper .not-on-ecu').classes()).to.contain('opacity-40');
        expect(wrapper.find('.header-wrapper .not-on-ecu').text()).to.eq('This package has not been installed yet.');
      });
      it('Does NOT Renders `This package has not been installed yet.` with class `opacity-40` if `installedOnEcus` property is present on `packageData`', () => {
        // wrapper.setProps();
        resetTest({
          packageData: {
            versions: [{ installedOnEcus: 2, id: {} }],
          },
        });
        expect(wrapper.find('.header-wrapper .not-on-ecu').exists()).to.eq(false);
      });
      it('Renders `This package has been installed on ...` with class `opacity-90` if `installedOnEcus` property is  present on `packageData`', () => {
        resetTest({
          packageData: {
            versions: [{ installedOnEcus: 2, id: {} }],
          },
        });
        expect(wrapper.find('.header-wrapper .on-ecu').exists()).to.eq(true);
        expect(wrapper.find('.header-wrapper .on-ecu').classes()).to.contain('opacity-90');
        expect(wrapper.find('.header-wrapper .on-ecu').text()).to.contain('This package has been installed on ');
      });
      it('Renders `This package has been installed on 1 device` if `installedOnEcus` value if == 1 is  present on `packageData`', () => {
        resetTest({
          packageData: {
            versions: [{ installedOnEcus: 1, id: {} }],
          },
        });
        expect(wrapper.find('.header-wrapper .on-ecu').exists()).to.eq(true);
        expect(wrapper.find('.header-wrapper .on-ecu').text()).to.contain('This package has been installed on 1 device');
      });
      it('Renders `This package has been installed on x devices` if `installedOnEcus` value if >= 2 is  present on `packageData`', () => {
        resetTest({
          packageData: {
            versions: [{ installedOnEcus: 2, id: {} }],
          },
        });
        expect(wrapper.find('.header-wrapper .on-ecu').exists()).to.eq(true);
        expect(wrapper.find('.header-wrapper .on-ecu').text()).to.contain('This package has been installed on 2 devices');
        resetTest({
          packageData: {
            versions: [{ installedOnEcus: 3, id: {} }],
          },
        });
        expect(wrapper.find('.header-wrapper .on-ecu').text()).to.contain('This package has been installed on 3 devices');
        resetTest({
          packageData: {
            versions: [{ installedOnEcus: 1, id: {} }, { installedOnEcus: 1, id: {} }],
          },
        });
        expect(wrapper.find('.header-wrapper .on-ecu').text()).to.contain('This package has been installed on 2 devices');
      });
    });
    describe('Body Wrapper', () => {
      it('should exist', () => {
        expect(wrapper.find('.body-wrapper').exists()).toBe(true);
      });
      it('should have class mt-4', () => {
        expect(wrapper.find('.body-wrapper.mt-4').exists()).toBe(true);
      });
      it('should have h6 header element with text `All versions` and css classes `mt-0 mb-1`', () => {
        expect(wrapper.find('.body-wrapper h6').classes()).to.deep.equal(['mt-0', 'mb-1']);
      });
      it('should render as many items as in the `version` property of `packageData` h6 header element with text `All versions` and all with css classes `mb-2 my-item`', () => {
        const version = {
          id: {
            version: '2.1.2',
            name: 'test-name',
          },
        };
        resetTest({
          packageData: {
            versions: [{ ...version, version: 2 }, { ...version, version: 3 }],
          },
        });
        expect(wrapper.findAll('.body-wrapper .my-item').length).to.equal(vm.packageData.versions.length);
        // expect(wrapper.find('.body-wrapper .my-item').classes()).to.contain(
        //   'mb-2',
        // );
        // expect(wrapper.find('.body-wrapper .my-item').classes()).to.contain(
        //   'q-item',
        // );
      });
      describe('For Each Version Item', () => {
        const setVersionProp = (v) => {
          const version = v || defaultVersion;
          wrapper.setProps({
            packageData: {
              versions: [{ ...version, version: 2 }, { ...version, version: 3 }],
            },
          });
        };
        afterEach(() => {
          hardwareIds = ['super-cool-id'];
          dependencies = ['super-awesome-dep'];
        });
        it('should wrap item contents in a label component', () => {
          setVersionProp();
          const items = wrapper.findAll('.body-wrapper .my-item');
          for (let i = 0; i < items.length; i++) {
            expect(items.length).to.eq(vm.packageData.versions.length);
            const v = items.at(i);
            expect(v.element.children[0].className).toBe('q-item__label');
          }
        });
        it('should have a div element with class `row`', () => {
          setVersionProp();
          const items = wrapper.findAll('.body-wrapper .my-item');
          for (let i = 0; i < items.length; i++) {
            expect(items.length).to.eq(vm.packageData.versions.length);
            const v = items.at(i);
            expect(v.find('.q-item__label div.row').exists()).toBe(true);
          }
        });
        it('should have a div element with class `col-auto` within a div with `row` class', () => {
          setVersionProp();
          const items = wrapper.findAll('.body-wrapper .my-item');
          for (let i = 0; i < items.length; i++) {
            expect(items.length).to.eq(vm.packageData.versions.length);
            const v = items.at(i);
            expect(v.find('div.row div.col-auto').exists()).toBe(true);
          }
        });
        describe('Div.col element', () => {
          it('should have a div.q-item-tile element with class `label` and `ellipsis`', () => {
            setVersionProp();
            const items = wrapper.findAll('.body-wrapper .my-item');
            for (let i = 0; i < items.length; i++) {
              expect(items.length).to.eq(vm.packageData.versions.length);
              const v = items.at(i);
              expect(v.find('div.row div.col-auto div.q-item-tile.label.ellipsis').exists()).toBe(true);
            }
          });
          it('should have a div.q-item-tile element with class `label`, `ellipsis` and `mxw-90` with content `Hash {{version.id.version}}', () => {
            setVersionProp();
            const items = wrapper.findAll('.body-wrapper .my-item');
            for (let i = 0; i < items.length; i++) {
              expect(items.length).to.eq(vm.packageData.versions.length);
              const v = items.at(i);
              expect(v.find('div.row div.col-auto  div.q-item-tile.label.ellipsis.mxw-90').exists()).toBe(true);
              expect(v.find('div.row div.col-auto div.q-item-tile.label.ellipsis.mxw-90').text()).toBe(`Hash: ${vm.packageData.versions[i].id.version}`);
            }
          });
          it('should have a div.q-item-tile element with class `label`, `ellipsis` and `mxw-90` with content `{{version.commitSubject}}', () => {
            setVersionProp({
              createdAt: Date.now() - 200000,
              updatedAt: Date.now(),
              targetLength: Math.random() + 2000,
              installedOnEcus: 2,
              hardwareIds: hardwareIds,
              dependencies: dependencies,
              packageHash: 'some-really-nice-long-hassshhhh',
              commitSubject: 'Some cool text',
              id: {
                version: '2.1.2',
                name: 'test-name',
              },
            });
            const items = wrapper.findAll('.body-wrapper .my-item');
            for (let i = 0; i < items.length; i++) {
              const v = items.at(i);
              expect(v.find('div.row div.col-auto div.q-item-tile.label.ellipsis.mxw-90').text()).toBe(`${vm.packageData.versions[i].commitSubject}`);
            }
          });

          setVersionProp();
          const items = wrapper.findAll('.body-wrapper .my-item');
          for (let i = 0; i < items.length; i++) {
            [
              {
                selector: 'created-at',
                propKey: `createdAt`,
                label: 'Created at',
                text: vm.$date.formatDate(vm.packageData.versions[i].createdAt, 'ddd MMM DD YYYY, h:mm:ss A'),
              },
              {
                selector: 'updated-at',
                label: 'Updated at',
                propKey: `updatedAt`,
                text: vm.$date.formatDate(vm.packageData.versions[i].updatedAt, 'ddd MMM DD YYYY, h:mm:ss A'),
              },
              {
                selector: 'hash',
                propKey: `packageHash`,
                label: 'Hash',
                text: vm.packageData.versions[i].packageHash,
              },
              {
                selector: 'size',
                label: 'Size',
                propKey: `targetLength`,
                text: vm.$format.humanStorageSize(vm.packageData.versions[i].targetLength),
              },
            ].forEach((d, x) => {
              it(`should have a div.q-item-tile element with class 'sublabel' and 'pt-1' with content '${d.label}: {{version.${d.propKey}}}'`, () => {
                const v = items.at(i);
                expect(v.find('div.row div.col-auto div.q-item-tile.sublabel.pt-1.' + d.selector).exists()).toBe(true);
                expect(v.find('div.row div.col-auto div.q-item-tile.sublabel.pt-1.' + d.selector + '  span.pr-1').text()).toBe(`${d.label}:`);
                expect(v.find('div.row div.col-auto div.q-item-tile.sublabel.pt-1.' + d.selector).text()).toBe(`${d.label}: ${d.text}`);
              });
            });
            describe('Installed On HW', () => {
              it('should exist', () => {
                setVersionProp();
                expect(wrapper.find('div.row div.col-12.installed-on div.q-item-tile.label').exists()).toBe(true);
              });
              it('should have the text `Installed on {{ver.installedOnEcus}} device for a single device`', () => {
                setVersionProp({ ...defaultVersion, installedOnEcus: 1 });
                expect(wrapper.find('div.row div.col-12.installed-on div.q-item-tile.label').text()).toBe(`Installed on ${vm.packageData.versions[i].installedOnEcus} device`);
              });
              it('should have the text `Installed on {{ver.installedOnEcus}} devices for more than one device`', () => {
                setVersionProp({ ...defaultVersion, installedOnEcus: 2 });
                expect(wrapper.find('div.row div.col-12.installed-on div.q-item-tile.label').text()).toBe(`Installed on ${vm.packageData.versions[i].installedOnEcus} devices`);
              });
              it('should have the text `Installed on {version.installedOnEcus} device(s)`', () => {
                setVersionProp();
                expect(wrapper.find('div.row div.col-12.installed-on div.q-item-tile.sublabel.pt-1 div.hw-sh.pr-1').text()).toBe(`Hardware ids:`);
              });
              it('should render `None` if no `hardware ids` present', () => {
                hardwareIds = [];
                setVersionProp({ ...defaultVersion, hardwareIds: [] });
                expect(wrapper.find('div.row div.col-12.installed-on div.q-item-tile.sublabel.pt-1 div.no-hw.pr-1').text()).toBe(`None`);
              });
              it('should render the `hardware ids` for all hardware-ids in `version.harwareIds`', () => {
                setVersionProp();
                expect(wrapper.find('div.row div.col-12.installed-on div.q-item-tile.sublabel.pt-1 div.q-chip--dense').text()).toBe(`super-cool-id`);
              });
              // describe('Dependencies:', () => {
              //   it('should render `Dependencies` sub header', () => {
              //       setVersionProp();
              //     expect(wrapper.find('div.row div.col-auto.dependencies div.q-item-tile.label').text()).toBe(`Dependencies`);
              //   });
              //   it('should render `None` if no dependencies present', () => {
              //     dependencies = [];
              //       setVersionProp();
              //     expect(wrapper.find('div.row div.col-auto.dependencies div.q-item-tile.sublabel.pt-1 div.no-dep.pr-1').text()).toBe(`None`);
              //   });
              //   it('should render all dependencies present', () => {
              //       setVersionProp();
              //     expect(wrapper.find('div.row div.col-auto.dependencies div.q-item-tile.sublabel.pt-1 div.q-chip--dense').text()).toBe(`super-awesome-dep`);
              //   });
              // });
            });
          }
        });
      });
    });
  });
});
