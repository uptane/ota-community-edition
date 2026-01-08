import * as actions from '../../../../../src/store/users/actions';
import ApiService from '../../../../../src/services/api.service';
import { createSandbox } from 'sinon';
import { assert } from 'chai';
import { API_USER_FETCH } from '../../../../../src/config';
jest.mock('../../../../../src/services/api.service');

let sandbox = createSandbox();
describe.skip('Users Store Actions', () => {
  beforeAll(() => {});
  afterEach(() => {
    jest.clearAllMocks();
    sandbox.restore();
  });
  describe('fetchUser', () => {
    it('should call a getResource method of ApiService with the given URL and options', async () => {
      const commit = jest.fn().mockReturnValue(null);
      const dispatch = jest.fn().mockResolvedValue({ status: 200 });
      const state = {};
      const data = { test: true };
      const spy = spyOn(ApiService, 'getResource').and.returnValue(Promise.resolve(data));
      actions
        .fetchUser({ commit, dispatch, state })
        .catch((err) => {})
        .finally(() => {
          expect(spy).toHaveBeenCalledWith(API_USER_FETCH, expect.any(Object));
        });
    });
    it('should resolve with response data if the request was successful', async () => {
      const commit = jest.fn().mockReturnValue(null);
      const state = {};
      const data = { user_id: 'test', test: true };
      const commitPath = 'users/setUserData';
      const dispatch = jest.fn().mockResolvedValue({ status: 200 });

      const spy = spyOn(ApiService, 'getResource').and.returnValue(Promise.resolve(data));
      await actions.fetchUser({ commit, dispatch, state });
      expect(commit).toHaveBeenCalledTimes(2);
      expect(commit).toHaveBeenCalledWith('ui/setUiLoaderText', 'Checking server for your config and namespace', { root: true });
      expect(commit).toHaveBeenCalledWith(commitPath, data, { root: true });
    });
    it('should reject with error if the request was unsuccessful', async () => {
      const commit = jest.fn().mockReturnValue(null);
      const state = {};
      const data = { test: true };
      const dispatch = jest.fn().mockRejectedValue(data);
      ApiService.getResource.mockRejectedValue(data);
      ApiService.postResource.mockRejectedValue(data);
      try {
        await actions.fetchUser({ commit, dispatch, state });
        assert.fail(`Should not have resolved`);
      } catch (err) {
        expect(err).toEqual(data);
      }
    });
  });
  describe.skip('userExists', () => {
    it('should call a getResource method of ApiService with the given URL and options', async () => {
      const commit = jest.fn().mockReturnValue(null);
      const dispatch = jest.fn().mockReturnValue(Promise.resolve(null));
      const state = {};
      const data = { test: true };
      const commitPath = 'users/setUserData';

      ApiService.getResource.mockResolvedValue(data);
      await actions.userExists({ commit, state, dispatch });
      expect(ApiService.getResource.mock.calls.length).toBe(1);
    });
    it('should createUser if response is empty', async () => {
      const commit = jest.fn().mockReturnValue(null);
      const state = {};
      const data = null;
      const dispatch = jest.fn().mockReturnValue(Promise.resolve(null));
      const commitPath = 'users/setUserData';

      ApiService.getResource.mockResolvedValue(data);
      await actions.userExists({ commit, state, dispatch });
      expect(dispatch).toHaveBeenCalledTimes(1);
      expect(dispatch).toHaveBeenCalledWith('createUser');
    });
    it('should try to createUser if response is does not have user_id', async () => {
      const commit = jest.fn().mockReturnValue(null);
      const state = {};
      const data = { test: 'some-id' };
      const dispatch = jest.fn().mockReturnValue(Promise.resolve(null));
      const commitPath = 'users/setUserData';

      ApiService.getResource.mockResolvedValue(data);
      await actions.userExists({ commit, state, dispatch });
      expect(dispatch).toHaveBeenCalledTimes(1);
      expect(dispatch).toHaveBeenCalledWith('createUser');
    });
    it('should try to  createUser if response is does have user_id', async () => {
      const commit = jest.fn().mockReturnValue(null);
      const state = {};
      const data = { user_id: 'some-id' };
      const dispatch = jest.fn().mockReturnValue(Promise.resolve(null));
      const commitPath = 'users/setUserData';

      ApiService.getResource.mockResolvedValue(data);
      await actions.userExists({ commit, state, dispatch });
      expect(dispatch).toHaveBeenCalledTimes(1);
      expect(dispatch).toHaveBeenCalledWith('fetchUser');
    });
    it('should try to createUser if request fails', async () => {
      expect.assertions(2);
      const commit = jest.fn().mockReturnValue(null);
      const state = {};
      const data = { user_id: 'some-id' };
      const dispatch = jest.fn().mockReturnValue(Promise.resolve(null));
      const commitPath = 'users/setUserData';
      ApiService.getResource.mockRejectedValue(data);
      try {
        await actions.userExists({ commit, state, dispatch });
        assert.fail(`Should not have resolved`);
      } catch (err) {
        expect(dispatch).toHaveBeenCalledTimes(1);
        expect(dispatch).toHaveBeenCalledWith('createUser');
      }
    });
    it('should resolve with response data if the request was successful', () => {
      const commit = jest.fn().mockReturnValue(null);
      const state = {};
      const data = { test: true };
      const dispatch = jest.fn().mockReturnValue(Promise.resolve(null));
      const commitPath = 'users/setUserData';

      ApiService.getResource.mockResolvedValue(data);
      actions.userExists({ commit, state, dispatch }).finally((a) => {
        expect(commit).toHaveBeenCalledTimes(1);
        expect(commit).toHaveBeenCalledWith(commitPath, data);
      });
    });
    it('should reject with error if the request was unsuccessful', async () => {
      const commit = jest.fn().mockReturnValue(null);
      const dispatch = jest.fn().mockReturnValue(null);
      const state = {};
      const data = { test: true };
      ApiService.getResource.mockRejectedValue(data);
      try {
        await actions.userExists({ commit, state, dispatch });
        assert.fail(`Should not have resolved`);
      } catch (err) {
        expect(err).toEqual(data);
      }
    });
  });
  describe('createUser', () => {
    it('should call a postResource method of ApiService with the given URL and options', async () => {
      const commit = jest.fn().mockReturnValue(null);
      const dispatch = jest.fn().mockReturnValue(null);
      const state = {};
      const data = { test: true };

      ApiService.postResource.mockResolvedValue(data);
      await actions.createUser({ commit, state, dispatch });
      expect(ApiService.postResource.mock.calls.length).toBe(1);
    });
    it('should fetch user and resolve with response data if the request was successful', async () => {
      const commit = jest.fn().mockReturnValue(null);
      const dispatch = jest.fn().mockReturnValue(null);
      const state = {};
      const data = { test: true };

      ApiService.postResource.mockResolvedValue(data);
      const resp = await actions.createUser({ commit, state, dispatch });
      expect(resp).to.deep.equal(data);
    });
    it('should reject with error if the request was unsuccessful', async () => {
      const commit = jest.fn().mockReturnValue(null);
      const dispatch = jest.fn().mockReturnValue(null);
      const state = {};
      const data = { test: true };
      ApiService.postResource.mockRejectedValue(data);
      try {
        await actions.createUser({ commit, state, dispatch });
        assert.fail(`Should not have resolved`);
      } catch (err) {
        expect(err).toEqual(data);
      }
    });
  });
  describe('saveMetadata', () => {
    it('should call a postResource method of ApiService with the given URL and updates', async () => {
      const commit = jest.fn().mockReturnValue(null);
      const dispatch = jest.fn().mockReturnValue(null);
      const state = {};
      const data = { test: true };

      ApiService.postResource.mockResolvedValue(data);
      await actions.saveMetadata({ commit, state, dispatch }, { updates: {} });
      expect(ApiService.postResource.mock.calls.length).toBe(1);
    });
    it('should fetch user and resolve with response data if the request was successful', async () => {
      const commit = jest.fn().mockReturnValue(null);
      const dispatch = jest.fn().mockReturnValue(null);
      const state = {};
      const data = { test: true };

      ApiService.getResource.mockResolvedValue(data);
      await actions.saveMetadata({ commit, state, dispatch }, { updates: {} });
      expect(dispatch).toHaveBeenCalledTimes(1);
      expect(dispatch).toHaveBeenCalledWith('fetchUser');
    });
    it('should reject with error if the request was unsuccessful', async () => {
      const commit = jest.fn().mockReturnValue(null);
      const dispatch = jest.fn().mockReturnValue(null);
      const state = {};
      const data = { test: true };
      ApiService.postResource.mockRejectedValue(data);
      try {
        await actions.saveMetadata({ commit, state, dispatch }, { updates: {} });
        assert.fail(`Should not have resolved`);
      } catch (err) {
        expect(err).toEqual(data);
      }
    });
  });
});
