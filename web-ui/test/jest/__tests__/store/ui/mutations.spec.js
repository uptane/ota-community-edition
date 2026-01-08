import state from '../../../../../src/store/ui/state';
import * as mutations from '../../../../../src/store/ui/mutations';
import { camelCase } from 'change-case';
import Vue from 'vue';
import All, { LocalStorage } from 'quasar';
import { createSandbox } from 'sinon';
import { mount, createLocalVue } from '@vue/test-utils';
jest.mock('../../../../../src/utils/local-storage');
// import {mockLocalStorage} from '../../mock-localstorage'

describe('UI Store Mutations', () => {
  Object.keys(state).forEach((k) => {
    it(`should set the value of ${k} to passed value`, () => {
      const expected = { test: true };
      // mutations[camelCase('set_'+k)](state, expected)
      // expect(state[k]).to.deep.eq(expected);
    });
  });

  // it('should apply device data to deviceUpdateInProgress with device uuid as key', ()=>{
  //   const data = {uuid: 'test-uuid-123-abc'};
  //     mutations['setDeviceWithUpdateInProgress'](state, data);
  //     expect(state['devicesWithUpdateInProgress'][data.uuid]).to.deep.eq(data);
  // });
  // it('should remove device data with device uuid from deviceUpdateInProgress ', ()=>{
  //   const data = {uuid: 'test-uuid-123-abc'};
  //     state['devicesWithUpdateInProgress'][data.uuid] = data;
  //     mutations['removeDeviceWithUpdateInProgress'](state, data.uuid);
  //     expect(state['devicesWithUpdateInProgress'][data.uuid]).toBe(null);
  // });
});
