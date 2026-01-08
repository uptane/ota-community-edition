import { LocalStorage } from 'quasar';
import { API_ACCOUNT_MIGRATE, API_ACCOUNT_MIGRATED, API_ACCOUNT_REGISTER, API_ACCOUNT_SEND_EMAIL_VERIFICATION_LINK, API_ACCOUNT_SEND_FORGOT_PASSWORD_LINK } from '../../../../../src/config';
import ApiService from '../../../../../src/services/api.service';
import { TdxKeycloakClient } from '../../../../../src/services/tdx.keycloak.client';

jest.mock('../../../../../src/services/api.service');
jest.mock('quasar');
const getTestTokenString = (header, body, footer) => {
  const h = header || 'some-header-string';
  const b = body || `{"data":"something totally random as usual"}`;
  const f = footer || 'some-footer-string';
  return btoa(h) + '.' + btoa(b) + '.' + btoa(f);
};
/** @type {TdxKeycloakClient} */
let kcClient;
LocalStorage.remove = jest.fn();
LocalStorage.getItem = jest.fn();
LocalStorage.set = jest.fn();
ApiService.postResource = jest.fn();
describe('TDX Keycloak Client', () => {
  beforeEach(() => {
    kcClient = new TdxKeycloakClient();
  });
  afterEach(() => {
    jest.clearAllMocks();
  });
  describe('clearTokens', () => {
    it('should remove access_token from localStorage', () => {
      kcClient.clearTokens();
      expect(LocalStorage.remove).toHaveBeenCalledWith('tdx_kc_access_token');
    });
    it('should remove refresh_token from localStorage', () => {
      kcClient.clearTokens();
      expect(LocalStorage.remove).toHaveBeenCalledWith('tdx_kc_refresh_token');
    });
  });
  describe('getAccessToken', () => {
    it('should return access_token from localStorage', () => {
      const expected = 'something random';
      LocalStorage.getItem.mockReturnValueOnce(expected);
      const actual = kcClient.getAccessToken();
      expect(LocalStorage.getItem).toHaveBeenCalledWith('tdx_kc_access_token');
      expect(expected).equal(actual);
    });
  });
  describe('getRefreshToken', () => {
    it('should return refresh_token from localStorage', () => {
      const expected = 'something totally random';
      LocalStorage.getItem.mockReturnValueOnce(expected);
      const actual = kcClient.getRefreshToken();
      expect(LocalStorage.getItem).toHaveBeenCalledWith('tdx_kc_refresh_token');
      expect(expected).equal(actual);
    });
  });
  describe('setRefreshToken', () => {
    it('should set refresh_token into localStorage', () => {
      const token = 'something totally random';
      kcClient.setRefreshToken(token);
      expect(LocalStorage.set).toHaveBeenCalledWith('tdx_kc_refresh_token', token);
    });
  });
  describe('setAccessToken', () => {
    it('should set access_token into localStorage', () => {
      const token = 'something totally random as usual';
      kcClient.setAccessToken(token);
      expect(LocalStorage.set).toHaveBeenCalledWith('tdx_kc_access_token', token);
    });
  });
  describe('getAccessTokenData', () => {
    it('should return null if no access_token is set', () => {
      const token = getTestTokenString();
      kcClient.getAccessToken = jest.fn();
      kcClient.getAccessToken.mockReturnValueOnce(null);
      const actual = kcClient.getAccessTokenData();
      expect(kcClient.getAccessToken).toHaveBeenCalledTimes(1);
      expect(actual).equal(null);
    });
    it("should return json data claims of current users' access_token if present", () => {
      const token = getTestTokenString();
      kcClient.getAccessToken = jest.fn();
      kcClient.getAccessToken.mockReturnValueOnce(token);
      const { data } = kcClient.getAccessTokenData();
      expect(kcClient.getAccessToken).toHaveBeenCalledTimes(1);
      expect(data).equal('something totally random as usual');
    });
  });
  describe('isAccessTokenActive', () => {
    it('should return true if token has NOT expired', () => {
      const token = getTestTokenString();
      jest.spyOn(kcClient, 'accessTokenData', 'get').mockReturnValue({
        exp: Date.now() / 1000 + 60, // set to expire in 1 minute
      });
      const actual = kcClient.isAccessTokenActive();
      expect(actual).toBeTruthy();
    });
    it('should return false if token has expired', () => {
      const token = getTestTokenString();
      jest.spyOn(kcClient, 'accessTokenData', 'get').mockReturnValue({
        exp: Date.now() / 1000 - 60, // set to expire in 1 minute
      });

      const actual = kcClient.isAccessTokenActive();
      expect(actual).toBeFalsy();
    });
  });
  describe('signUp', () => {
    it('should fail with code `INVALID_DATA` if model is empty', () => {
      const fakeModel = null;
      return kcClient.signUp(fakeModel).catch((err) => {
        expect(err.code).equal('INVALID_DATA');
      });
    });
    it('should fail with code `INVALID_EMAIL` if model email is empty', () => {
      const fakeModel = {};
      return kcClient.signUp(fakeModel).catch((err) => {
        expect(err.code).equal('INVALID_EMAIL');
      });
    });
    it('should fail with code `INVALID_PASSWORD` if model password is empty', () => {
      const fakeModel = { email: 'test' };
      return kcClient.signUp(fakeModel).catch((err) => {
        expect(err.code).equal('INVALID_PASSWORD');
      });
    });
    it('should call the register endpoint with the the given data in a post request', () => {
      const fakeModel = { email: 'test', password: 'test' };
      const options = { headers: { 'Content-Type': ' application/json' } };
      ApiService.postResource.mockResolvedValueOnce({});
      return kcClient.signUp(fakeModel).then((data) => {
        expect(ApiService.postResource).toHaveBeenCalledWith(API_ACCOUNT_REGISTER, fakeModel, options);
      });
    });
  });
  describe('signIn', () => {
    it('should fail with code `INVALID_DATA` model is empty', () => {
      const fakeModel = null;
      return kcClient.signIn(fakeModel).catch((err) => {
        expect(err.code).equal('INVALID_DATA');
      });
    });
    it('should fail with code `INVALID_EMAIL` model username is empty', () => {
      const fakeModel = {};
      return kcClient.signIn(fakeModel).catch((err) => {
        expect(err.code).equal('INVALID_EMAIL');
      });
    });
    it('should fail with code `INVALID_PASSWORD` model password is empty', () => {
      const fakeModel = { email: 'test' };
      return kcClient.signIn(fakeModel).catch((err) => {
        expect(err.code).equal('INVALID_PASSWORD');
      });
    });
    it('should call the token endpoint with the the given data in a post request', () => {
      const fakeModel = { email: 'test', password: 'test', totp: '' };
      const testAccessToken = 'random string for token';
      const testRefreshToken = 'random string';
      const options = {
        bypassAuthIntercept: true,
        bypassUnauthorizeRedirect: true,
        validateStatus: function(status) {
          return (status >= 200 && status < 300) || status === 304 || status !== 401;
        },
      };
      ApiService.postResource.mockResolvedValueOnce({
        access_token: testAccessToken,
        refresh_token: testRefreshToken,
      });
      kcClient.setAccessToken = jest.fn();
      kcClient.setHostAccessToken = jest.fn();
      kcClient.setRefreshToken = jest.fn();
      const encodedModel = new URLSearchParams(fakeModel);
      return kcClient.signIn(fakeModel).then((data) => {
        expect(ApiService.postResource).toHaveBeenCalledWith(kcClient.tokenUrl, expect.anything(), expect.anything());
        expect(kcClient.setHostAccessToken).toHaveBeenCalledWith(testAccessToken);
        expect(kcClient.setRefreshToken).toHaveBeenCalledWith(testRefreshToken);
      });
    });
  });
  describe('signOut', () => {
    it('should call an endpoint to invalidate user referesh_token', () => {
      kcClient.getRefreshToken = jest.fn();
      kcClient.clearTokens = jest.fn();
      kcClient.openUrl = jest.fn();
      ApiService.postResource.mockResolvedValueOnce({});
      const kcModel = {
        token_type_hint: 'refresh_token',
        client_id: kcClient.keycloakClientId,
        token: kcClient.getRefreshToken(),
      };
      kcClient.signOut();
      expect(kcClient.openUrl).toHaveBeenCalledWith(kcClient.logoutUrl);
    });
    it('should call clear stored jwt refresh and access tokens', () => {
      kcClient.clearTokens = jest.fn();
      ApiService.postResource.mockResolvedValueOnce({});
      kcClient.signOut();
      expect(kcClient.clearTokens).toHaveBeenCalledTimes(1);
    });
  });
  describe('getUserData', () => {
    it('should return a resolved promise with token data if token is active', () => {
      const testData = { test: 'any string' };
      kcClient.isAccessTokenActive = jest.fn();
      kcClient.getCurrentUser = jest.fn();
      kcClient.isAccessTokenActive.mockReturnValueOnce(true);
      kcClient.getCurrentUser.mockReturnValueOnce(testData);
      return kcClient.getUserData().then((actual) => {
        expect(kcClient.getCurrentUser).toHaveBeenCalledTimes(1);
        expect(actual).toEqual(testData);
      });
    });
    it('should return a rejected promise with null if token is NOT active', () => {
      kcClient.isAccessTokenActive = jest.fn();
      kcClient.getCurrentUser = jest.fn();
      kcClient.isAccessTokenActive.mockReturnValueOnce(false);
      return kcClient.getUserData().catch((actual) => {
        expect(kcClient.getCurrentUser).toHaveBeenCalledTimes(0);
        expect(actual).toEqual(null);
      });
    });
  });
  describe('refreshSession', () => {
    it('should get currently stored refresh token', () => {
      kcClient.getRefreshToken = jest.fn();
      kcClient.getRefreshToken.mockReturnValue('test-token');
      ApiService.postResource = jest.fn();
      ApiService.postResource.mockResolvedValueOnce({});
      return kcClient.refreshSession().then(() => {
        expect(kcClient.getRefreshToken).toHaveBeenCalledTimes(1);
      });
    });
    it('should make a post request to token url with saved refresh_token and bypass auth intercept and redirect', () => {
      kcClient.getRefreshToken = () => 'test-token';
      ApiService.postResource = jest.fn();
      const testAccessToken = 'random string for token';
      const testRefreshToken = 'random string';
      const resp = { access_token: testAccessToken, refresh_token: testRefreshToken };
      ApiService.postResource.mockResolvedValueOnce(resp);
      kcClient.getRefreshToken = jest.fn();
      kcClient.getRefreshToken.mockReturnValueOnce(testRefreshToken);
      return kcClient.refreshSession().then(() => {
        expect(kcClient.getRefreshToken).toHaveBeenCalledTimes(1);
        expect(ApiService.postResource).toHaveBeenCalledWith(
          kcClient.tokenUrl,
          new URLSearchParams({
            grant_type: 'refresh_token',
            client_id: kcClient.keycloakClientId,
            refresh_token: testRefreshToken,
          }),
          expect.anything(),
        );
      });
    });
    it('should store access token and refresh token if request was successful', () => {
      kcClient.getRefreshToken = () => 'test-token';
      kcClient.setAccessToken = jest.fn();
      kcClient.setHostAccessToken = jest.fn();
      kcClient.setRefreshToken = jest.fn();
      ApiService.postResource = jest.fn();
      const testAccessToken = 'random string for token';
      const testRefreshToken = 'random string';
      const resp = { access_token: testAccessToken, refresh_token: testRefreshToken };
      ApiService.postResource.mockResolvedValueOnce(resp);
      return kcClient.refreshSession().then(() => {
        expect(kcClient.setHostAccessToken).toHaveBeenCalledWith(testAccessToken);
        expect(kcClient.setRefreshToken).toHaveBeenCalledWith(testRefreshToken);
      });
      // const testRefreshToken = getTestTokenString();
    });
  });
  describe('isLoggedIn', () => {
    it('should return a resolved promise with true value if token is active', () => {
      const testData = { test: 'any string' };
      kcClient.isAccessTokenActive = jest.fn();
      kcClient.refreshSession = jest.fn();
      kcClient.isAccessTokenActive.mockReturnValueOnce(true);
      kcClient.refreshSession.mockResolvedValueOnce(testData);
      return kcClient.isLoggedIn().then((actual) => {
        expect(kcClient.isAccessTokenActive).toHaveBeenCalledTimes(1);
        expect(actual).toEqual(true);
      });
    });
    it('should attempt to refresh token if token is NOT active return resolved promise with true value if refresh token succeeds', () => {
      const testData = { test: 'any string' };
      kcClient.isAccessTokenActive = jest.fn();
      kcClient.refreshSession = jest.fn();
      kcClient.isAccessTokenActive.mockReturnValueOnce(false).mockReturnValueOnce(true);
      kcClient.refreshSession.mockResolvedValueOnce(testData);
      return kcClient.isLoggedIn().then((actual) => {
        expect(kcClient.isAccessTokenActive).toHaveBeenCalledTimes(2);
        expect(kcClient.refreshSession).toHaveBeenCalledTimes(1);
      });
    });
    it('should return rejected promise with false value if refresh token fails', () => {
      const testData = { test: 'any string' };
      kcClient.isAccessTokenActive = jest.fn();
      kcClient.refreshSession = jest.fn();
      kcClient.isAccessTokenActive.mockReturnValueOnce(false);
      kcClient.refreshSession.mockResolvedValueOnce(testData);
      return kcClient.isLoggedIn().catch((actual) => {
        expect(kcClient.isAccessTokenActive).toHaveBeenCalledTimes(2);
        expect(kcClient.refreshSession).toHaveBeenCalledTimes(1);
      });
    });
  });

  describe('forgotPassword', () => {
    it('should return rejected promise with error code INVALID_EMAIL  if email provided is invalid', () => {
      const fakeEmail = null;
      ApiService.putResource = jest.fn();
      return kcClient.forgotPassword(fakeEmail).catch((err) => {
        expect(ApiService.getResource).toHaveBeenCalledTimes(0);
        expect(err.code).equal('INVALID_EMAIL');
      });
    });
    it('should make a put request to `' + API_ACCOUNT_SEND_FORGOT_PASSWORD_LINK + '`  email provided', () => {
      const fakeEmail = 'something@random.email';
      ApiService.putResource = jest.fn();
      return kcClient.forgotPassword(fakeEmail).catch((err) => {
        expect(ApiService.putResource).toHaveBeenCalledWith(API_ACCOUNT_SEND_FORGOT_PASSWORD_LINK, { email: fakeEmail }, expect.anything());
      });
    });
  });
  describe('resendConfirmationEmail', () => {
    it('should return rejected promise with error code INVALID_EMAIL  if email provided is invalid', () => {
      const fakeEmail = null;
      ApiService.putResource = jest.fn();
      return kcClient.resendConfirmationEmail(fakeEmail).catch((err) => {
        expect(ApiService.getResource).toHaveBeenCalledTimes(0);
        expect(err.code).equal('INVALID_EMAIL');
      });
    });
    it('should make a put request to `' + API_ACCOUNT_SEND_EMAIL_VERIFICATION_LINK + '`  email provided', () => {
      const fakeEmail = 'something@random.email';
      ApiService.putResource = jest.fn();
      return kcClient.resendConfirmationEmail(fakeEmail).catch((err) => {
        expect(ApiService.putResource).toHaveBeenCalledWith(API_ACCOUNT_SEND_EMAIL_VERIFICATION_LINK, { email: fakeEmail }, expect.anything());
      });
    });
  });
});
