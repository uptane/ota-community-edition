/* eslint-disable */
/**
 * @jest-environment jsdom
 */

import { mount, createLocalVue, shallowMount, Wrapper } from '@vue/test-utils';
import $ from 'jquery';
import store from '../../../../src/store';
import DeviceOnlineBadge from '../../../../src/components/devices/DeviceOnlineBadge';
import { createSandbox } from 'sinon';
import { Quasar } from 'quasar';
import { components } from '../../utils/components';

const sandbox = createSandbox();
jest.useFakeTimers();
describe('Online Badge Component', () => {
  const localVue = createLocalVue();
  localVue.use(Quasar, { components });
  /** @type {Wrapper<Vue>} */
  let wrapper;
  let element;
  const resetTest = (props, data = {}) => {
    wrapper = mount(DeviceOnlineBadge, {
      propsData: props || {},
      data: () => data,
      store,
      localVue,
    });
    element = $(wrapper.element).find('div.last-seen-badge');
  };
  resetTest();
  beforeEach(() => {
    resetTest();
    sandbox.restore();
  });
  // it('passes the sanity check and creates a wrapper', () => {
  //   expect(wrapper.isVueInstance()).toBe(true);
  // });
  describe('Markup', () => {
    describe('LastSeenBadge:', () => {
      it('Shows lastSeen badge when lastSeenBadge.show=true', () => {
        expect.assertions(1);
        resetTest({}, { badgeData: { show: true } });
        expect(element.length).toBe(1);
      });
      // it('Displays text as the value in lastSeenBadge.text"', () => {
      //   const badgeData = { text: '1234 abc' };
      //   resetTest({}, { badgeData })
      //   expect(element.text()).toBe(badgeData.text);
      // });
      it("Contains color class that's present in badgeData", () => {
        const badgeData = { show: true, colorClass: 'test-class' };
        resetTest({}, { badgeData });
        expect(element.prop('class')).toContain(badgeData.colorClass);
      });
    });
  });
});
