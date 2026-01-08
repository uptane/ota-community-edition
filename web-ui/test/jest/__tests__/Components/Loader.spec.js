/* eslint-disable */
/**
 * @jest-environment jsdom
 */

import { mount, createLocalVue } from '@vue/test-utils';
import Loader from '../../../../src/components/loaders/Loader';
import * as All from 'quasar';
const { Quasar } = All;

const components = Object.keys(All).reduce((object, key) => {
  const val = All[key];
  if (val && val.component && val.component.name != null) {
    object[key] = val;
  }
  return object;
}, {});

describe('Custom Loader Component', () => {
  const localVue = createLocalVue();
  localVue.use(Quasar, { components });
  const wrapper = mount(Loader, {
    localVue,
  });
  const vm = wrapper.vm;

  // it('passes the sanity check and creates a wrapper', () => {
  //   expect(wrapper.isVueInstance()).toBe(true)
  // })
  it('Set color property default to secondary', () => {
    expect(vm.color).toBe('secondary');
  });
  it('Set size property default to 2rem', () => {
    expect(vm.size).toBe('2rem');
  });
  it('Has correct css classes ', () => {
    const classes = wrapper.find('.shg-loader').attributes().class;
    expect(classes).contains('float-right');
    expect(classes).contains('q-spinner');
    expect(classes).contains('text-secondary');
  });
});
