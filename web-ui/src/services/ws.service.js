import _ from 'lodash';
import { DEVICE_UPDATE_EVENT_MESSAGE_TYPES, WEB_EVENTS, WS_DATA_REQUEST } from '../constants';
import { SessionStorage } from 'quasar';

class WebSocketClient {
  constructor() {
    this.onInit = (ctx) => {};
    this.onConnect = (ctx, data) => {};
    this.onBeforeAuth = (ctx) => {};
    this.onAuthSuccess = (ctx, data) => {};
    this.onAuthFailure = (ctx, data) => {};
    this.onDisconnect = (ctx, data) => {};
    this.onReceive = (ctx, data) => {};
    this.onParseError = (ctx, err, data) => {};
    this.onBeforeReconnectAttempt = () => {};
    this.onBeforeReconnect = (ctx) => {};
    this.onBeforeAuthRetry = (ctx) => {};
    this.onBeforeDestroy = (ctx) => {};
    this.onError = (ctx, data) => {};
    this.onDestroy = (ctx) => {};
    this.tokenFactory = () => {};
  }
  init(context, wsUrl, autoReconnect = true) {
    this.context = context;
    this.wsUrl = wsUrl;
    this.autoReconnect = autoReconnect;
    this.delay = 5;
    log(`WebSocket: Initialized!`);
    if (this.onInit) this.onInit(this);
    this.connect();
    this._keepConnectionAlive();
  }
  requestData(type, device_uuid) {
    if (this.authenticated && this.connected) {
      log(`WebSocket: Requesting data...`);
      let message = {
        type: 'data_request',
        data: {
          device_uuid,
          request_type: type,
        },
      };
      this._sendMessage(message);
    }
  }
  connect() {
    log(`WebSocket: Connecting...`);
    this._resetFlags();
    this.connecting = true;
    this.websocket = new WebSocket(this.wsUrl);
    this.websocket.onopen = (args) => {
      this._openHandler(args);
    };
    this.websocket.onmessage = (args) => {
      this._messageHandler(args);
    };
    this.websocket.onclose = (args) => {
      this._closeHandler(args);
    };
    this.websocket.onerror = (args) => {
      this._errorHandler(args);
    };
  }
  destroy() {
    this._resetFlags();
    log(`WebSocket: Cleaning up`);
    this.websocket.close();
    this.websocket = null;
    if (this.onDestroy) this.onDestroy(this);
  }

  _resetFlags() {
    this.connected = false;
    this.connecting = false;
    this.authenticating = false;
    this.authenticated = false;
  }
  _keepConnectionAlive() {
    if (this.autoReconnect && !this.connected && !this.authenticating && !this.connecting && !this.authenticated) {
      console.log(`WebSocket: Connection lost, will attempt to reconnect`);
      if (this.onBeforeReconnectAttempt) this.onBeforeReconnectAttempt(this);
      if (!this.connected) {
        if (this.onBeforeReconnect) this.onBeforeReconnect(this);
        this.connect();
      } else if (!this.authenticated) {
        if (this.onBeforeAuthRetry) this.onBeforeAuthRetry(this);
        this._authenticate();
      }
    }
    setTimeout(() => {
      this._keepConnectionAlive();
    }, this.delay * 1000);
  }
  _authenticate() {
    let auth_token = '';
    if (this.onBeforeAuth) {
      auth_token = this.onBeforeAuth(this);
    }
    info('WebSocket: Connected, begining authentication');
    this.authenticating = true;
    if (!auth_token && this.tokenFactory) {
      info('WebSocket: fetching access token');
      auth_token = this.tokenFactory();
    }
    const msg = {
      type: 'authenticate',
      data: { token: auth_token },
    };
    const auth_msg = JSON.stringify(msg);
    this.websocket.send(auth_msg);
  }
  _sendMessage(message) {
    const msg_str = JSON.stringify(message);
    this.websocket.send(msg_str);
  }
  _closeHandler(msg) {
    if (this.onDisconnect) this.onDisconnect(this, msg);
    this.destroy();
    log(`WebSocket: Closed. code: ${msg.code}`);
  }
  _openHandler(msg) {
    this.connecting = false;
    this.connected = true;
    if (this.onConnect) this.onConnect(this, msg);
    this._authenticate();
  }
  _errorHandler(msg) {
    logError('WebSocket: Error - message: ', msg);
    if (this.onError) this.onError(this, msg);
    this.destroy();
  }
  _messageHandler(msg) {
    const { commit, state, dispatch } = this.context;
    try {
      const eventObj = JSON.parse(msg.data);
      let { type, data } = eventObj;
      try {
        data = JSON.parse(data);
      } catch (e) {
        logError('WebSocket: Unable to parse web event data', e);
        if (this.onParseError) this.onParseError(this, e, msg);
      }
      // console.log(`WebSocket message (${type}) data: `, data, eventObj);
      if (type === 'authFailure') {
        this.authenticating = false;
        this.authenticated = false;
        if (this.onAuthFailure) this.onAuthFailure(this, msg);
        logError(`WebSocket: Authentication failed, `, data, eventObj);
      }
      if (type === 'authSuccess') {
        this.authenticating = false;
        this.authenticated = true;
        if (this.onAuthSuccess) this.onAuthSuccess(this, msg);
        info(`WebSocket: Authenticated!`);
      }
      if (type === 'dataRequest' && _.isObject(data) && !_.isEmpty(data)) {
        if (data.key === 'requestError') {
          logError(`WS data request error: ${data.result}`);
        } else if (data.key === 'requestSuccess') {
          let requestData = JSON.parse(data.result);
          switch (data.relates_to) {
            case WS_DATA_REQUEST.DEVICE_UPDATE_INSTALLATION_EVENTS:
              let deviceData = requestData[0] || {};
              if (_.isString(deviceData.deviceUuid)) {
                commit('devices/setUpdateInstallationEventsForDevice', { uuid: deviceData.deviceUuid, events: requestData });
              }
              break;
            case WS_DATA_REQUEST.DEVICE_UPDATE_INSTALLATION_HISTORY:
              let values = (requestData || {}).values;
              let firstValue = (values || [])[0];
              if (_.isString(firstValue.deviceUuid)) {
                commit('devices/setUpdateInstallationHistoryForDevice', { uuid: firstValue.deviceUuid, events: values });
              }
              break;
            default:
              logError(`Unhandled WS data request: ${data.key}`);
              break;
          }
        } else {
          logError(`Unknown WS data request: ${data.key}`);
        }
      }
      if (Object.keys(WEB_EVENTS).find((key) => WEB_EVENTS[key] === type) && _.isObject(data) && !_.isEmpty(data)) {
        switch (type) {
          case WEB_EVENTS.DEVICE_SEEN:
            if (_.isString(data.uuid) && new Date(data.lastSeen).getTime()) {
              dispatch('devices/updateDeviceStatus', { uuid: data.uuid, lastSeen: data.lastSeen }, { root: true });
            }
            break;
          case WEB_EVENTS.DEVICE_UPDATE_STATUS:
            if (_.isString(data.device)) {
              commit('devices/updateSingleDevice', { uuid: data.device, deviceStatus: data.status }, { root: true });
              // if event is reporting that device update is complete, fetch the entire device data
              if (data.status === 'UpToDate') {
                // Fetch all devices and then fetch the detail for current device
                dispatch('devices/fetchDevices', {}, { root: true });
                dispatch('devices/fetchDevice', data.device, { root: true });
              }
            }
            break;
          case WEB_EVENTS.DEVICE_CREATED:
            if (_.isString(data.uuid)) {
              const hwIdArray = (data.deviceId || '').split('-');
              data.hardwareType = hwIdArray.slice(0, hwIdArray.length - 2).join('-');
              dispatch('devices/createNewDeviceEntry', data, { root: true });
            }
            break;
          case WEB_EVENTS.DEVICE_DELETE_REQUEST:
            // if (_.isString(data.uuid)) {
            //     commit('devices/updateSingleDevice', data, { root: true });
            // }
            break;
          case WEB_EVENTS.TUF_TARGET_ADDED:
          case WEB_EVENTS.TUF_TARGET_MODIFIED:
            dispatch('packages/fetchPackages', {}, { root: true });
            break;
          case WEB_EVENTS.PACKAGE_BLOCKLISTED:
            // softwareStore.fetchBlocklist();
            break;
          case WEB_EVENTS.DEVICE_UPDATE_STATUS:
            if (_.isString(data.device)) {
              commit('devices/updateSingleDevice', { uuid: data.device, deviceStatus: data.status }, { root: true });
            }
            break;
          case WEB_EVENTS.UPDATE_SPEC:
            if (_.isString(data.device)) {
              const getExistingSpec = () => {
                return SessionStorage.getItem('deviceUpdateSpec') || {};
              };
              const addSpec = (newSpec) => {
                SessionStorage.set('deviceUpdateSpec', { ...getExistingSpec(), [newSpec.device]: newSpec });
              };
              const removeExistingSpec = (uuid) => {
                const spec = getExistingSpec();
                delete spec[uuid];
                SessionStorage.set('deviceUpdateSpec', { ...spec });
              };
              const deviceData = { uuid: data.device, deviceStatus: data.status, updateSpec: data };
              if (data.status === 'Finished') {
                deviceData.deviceStatus === 'UpToDate';
              }
              commit('devices/updateSingleDevice', deviceData, { root: true });
              if (data.status === 'Outdated' || data.status === 'Pending') {
                addSpec(data);
              }
              if (data.status === 'Finished') {
                dispatch('devices/showDeviceUpdatedNotification', data.device, { root: true });
                removeExistingSpec(data.device);
              }
              if (data.status === 'Failed') {
                dispatch('devices/showDeviceUpdatedFailedNotification', data.device, { root: true });
                removeExistingSpec(data.device);
              }
            }
            break;
          case WEB_EVENTS.DEVICE_SYSTEM_INFO_CHANGED:
            // if (_.isString(data.uuid) ) {
            //     commit('devices/updateSingleDevice', { uuid: data.uuid, lastSeen: data.lastSeen }, { root: true });
            // }
            break;
          case WEB_EVENTS.DEVICE_EVENT_MESSAGE:
            commit('devices/addUpdateInstallationEventForDevice', { uuid: data.deviceUuid, event: data }, { root: true });
            if (data.eventType && data.eventType.id && data.eventType.id === DEVICE_UPDATE_EVENT_MESSAGE_TYPES.EcuInstallationCompleted) {
              if (data.payload.success) {
                dispatch('devices/showDeviceUpdatedNotification', data.deviceUuid, { root: true });
              }
              if (!data.payload.success) {
                dispatch('devices/showDeviceUpdatedFailedNotification', data.deviceUuid, { root: true });
              }
            }
            break;
          default:
            logError(`Unhandled event type: ${eventObj.type}`);
            break;
        }
      }
    } catch (error) {
      logError('Error parsing WebSocket message', error, msg);
      if (this.onParseError) this.onParseError(this, error, msg);
    }
  }
}

/** @type { WebSocketClient } */
const WebsocketHandler = new WebSocketClient();

export default WebsocketHandler;
