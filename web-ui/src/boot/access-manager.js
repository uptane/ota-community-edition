/**
 * OTA Community Edition - Access Manager Boot
 * 
 * Sets up Vue prototype properties for access control.
 * In CE mode, we're always in full-access mode with no demo restrictions.
 */

const env = process.env;

// CE mode: No demo mode, no limited access
const DemoMode = false;
const UserPool = 'ce';
const WsUrl = env.WS_HOST ? `wss://${env.WS_HOST}` : '';
const CredentialsDownloadLink = env.CREDENTIALS_DOWNLOAD_LINK || '/api/provision/create-device';
const E2eMode = !!env.E2E_MODE;

export default ({ Vue }) => {
  Vue.prototype.$credentials_link = CredentialsDownloadLink;
  Vue.prototype.$e2eMode = E2eMode;
  Vue.prototype.$demoMode = DemoMode;
  Vue.prototype.$userPool = UserPool;
  Vue.prototype.$isDevUserPool = false;
  Vue.prototype.$ws_url = WsUrl;
  Vue.prototype.$isCEMode = true;
};

export { DemoMode, UserPool, WsUrl };
