jest.mock('axios');
import axios from 'axios';
import ApiService from '../../../../src/services/api.service';
import { assert } from 'chai';

describe('API-Service', () => {
  beforeEach(() => {
    // axios.get.mockClear()
  });
  afterEach(() => {
    jest.clearAllMocks();
  });
  describe('getResource', () => {
    it('should call a get method of axios with the given URL and options', async () => {
      const url = 'test-endpoint',
        options = { test: true, headers: { someHeader: true } };
      axios.get.mockResolvedValue({ data: true });
      await ApiService.getResource(url, options);
      expect(axios.get.mock.calls.length).toBe(1);
      expect(axios.get).toHaveBeenCalledWith(url, options);
    });
    it('should resolve with response data if the request was successful', async () => {
      const url = 'test-endpoint',
        options = { test: true, headers: { someHeader: true } },
        resp = { data: { success: true } };
      axios.get.mockResolvedValue(resp);
      const actual = await ApiService.getResource(url, options);
      expect(actual).toEqual(resp.data);
    });
    it('should reject with error if the request was unsuccessful', async () => {
      const url = 'test-endpoint',
        options = { test: true, headers: { someHeader: true } },
        resp = { data: { success: true } };
      axios.get.mockRejectedValue(resp);
      try {
        await ApiService.getResource(url, options);
        assert.fail(`Should not have resolved`);
      } catch (err) {
        expect(err).toEqual(resp);
      }
    });
  });
  describe('multiSourceGet', () => {
    it('should call a get method of axios with the given URLs and options', async () => {
      const urls = ['test-endpoint', 'test-endpoint2', 'test-endpoint3', 'test-endpoint4'],
        options = { test: true, headers: { someHeader: true } };
      axios.get.mockResolvedValue({ data: true });
      await ApiService.multiSourceGet(urls, options);
      expect(axios.get.mock.calls.length).toBe(4);
      urls.forEach((url) => {
        expect(axios.get).toHaveBeenCalledWith(url, options);
      });
    });
    it('should fail if urls is not an array or an empty array', async () => {
      const urls = 'test-endpoint',
        options = { test: true, headers: { someHeader: true } };
      axios.get.mockResolvedValue({ data: true });
      try {
        await ApiService.multiSourceGet(urls, options);
        assert.fail();
      } catch (err) {
        expect(axios.get.mock.calls.length).toBe(0);
        expect(err).toBe('URLs must be an array of at least one url');
      }
    });
    it('should resolve with response data if all the requests were successful', async () => {
      const urls = ['test-endpoint', 'test-endpoint2', 'test-endpoint3', 'test-endpoint4'];
      const options = { test: true, headers: { someHeader: true } };
      const dataArray = [{ success: true }, { done: 'yes' }, { ready: '5' }, { count: 5 }];
      const resps = dataArray.map((a) => {
        return { data: { ...a } };
      });
      resps.forEach((a) => {
        axios.get.mockResolvedValueOnce(a);
      });
      const actual = await ApiService.multiSourceGet(urls, options);
      expect(actual).toEqual(dataArray);
    });
    it('should reject with error if any of the requests was unsuccessful', async () => {
      const urls = ['test-endpoint', 'test-endpoint2', 'test-endpoint3', 'test-endpoint4'],
        options = { test: true, headers: { someHeader: true } },
        resp = { data: { success: true } };
      axios.get.mockRejectedValue(resp);
      try {
        await ApiService.multiSourceGet(urls, options);
        assert.fail(`Should not have resolved`);
      } catch (err) {
        expect(err).toEqual(resp);
      }
    });
  });

  describe('postResource', () => {
    it('should call a post method of axios with the given URL, data and options', async () => {
      const url = 'test-endpoint',
        data = { testData: true },
        options = { test: true, headers: { someHeader: true } };
      axios.post.mockResolvedValue({ data: true });
      await ApiService.postResource(url, data, options);
      expect(axios.post.mock.calls.length).toBe(1);
      expect(axios.post).toHaveBeenCalledWith(url, data, options);
    });
    it('should resolve with response data if the request was successful', async () => {
      const url = 'test-endpoint',
        data = { testData: true },
        options = { test: true, headers: { someHeader: true } },
        resp = { data: { success: true } };
      axios.post.mockResolvedValue(resp);
      const actual = await ApiService.postResource(url, data, options);
      expect(actual).toEqual(resp.data);
    });
    it('should reject with error if the request was unsuccessful', async () => {
      const url = 'test-endpoint',
        data = { testData: true },
        options = { test: true, headers: { someHeader: true } },
        resp = { data: { success: true } };
      axios.post.mockRejectedValue(resp);
      try {
        await ApiService.postResource(url, data, options);
        assert.fail(`Should not have resolved`);
      } catch (err) {
        expect(err).toEqual(resp);
      }
    });
  });
  describe('putResource', () => {
    it('should call a post method of axios with the given URL, data and options', async () => {
      const url = 'test-endpoint',
        data = { testData: true },
        options = { test: true, headers: { someHeader: true } };
      axios.put.mockResolvedValue({ data: true });
      await ApiService.putResource(url, data, options);
      expect(axios.put.mock.calls.length).toBe(1);
      expect(axios.put).toHaveBeenCalledWith(url, data, options);
    });
    it('should resolve with response data if the request was successful', async () => {
      const url = 'test-endpoint',
        data = { testData: true },
        options = { test: true, headers: { someHeader: true } },
        resp = { data: { success: true } };
      axios.put.mockResolvedValue(resp);
      const actual = await ApiService.putResource(url, data, options);
      expect(actual).toEqual(resp.data);
    });
    it('should reject with error if the request was unsuccessful', async () => {
      const url = 'test-endpoint',
        data = { testData: true },
        options = { test: true, headers: { someHeader: true } },
        resp = { data: { success: true } };
      axios.put.mockRejectedValue(resp);
      try {
        await ApiService.putResource(url, data, options);
        assert.fail(`Should not have resolved`);
      } catch (err) {
        expect(err).toEqual(resp);
      }
    });
  });
  describe('deleteResource', () => {
    it('should call a post method of axios with the given URL, data and options', async () => {
      const url = 'test-endpoint',
        options = { test: true, headers: { someHeader: true } };
      axios.delete.mockResolvedValue({ data: true });
      await ApiService.deleteResource(url, options);
      expect(axios.delete.mock.calls.length).toBe(1);
      expect(axios.delete).toHaveBeenCalledWith(url, options);
    });
    it('should resolve with response data if the request was successful', async () => {
      const url = 'test-endpoint',
        options = { test: true, headers: { someHeader: true } },
        resp = { data: { success: true } };
      axios.delete.mockResolvedValue(resp);
      const actual = await ApiService.deleteResource(url, options);
      expect(actual).toEqual(resp.data);
    });
    it('should reject with error if the request was unsuccessful', async () => {
      const url = 'test-endpoint',
        options = { test: true, headers: { someHeader: true } },
        resp = { data: { success: true } };
      axios.delete.mockRejectedValue(resp);
      try {
        await ApiService.deleteResource(url, options);
        assert.fail(`Should not have resolved`);
      } catch (err) {
        expect(err).toEqual(resp);
      }
    });
  });
});
