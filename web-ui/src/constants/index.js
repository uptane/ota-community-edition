import * as err_codes from './error-codes';
import { web_events, update_statuses, data_requests } from './web.events';
import { device_update_event_message_types, update_states } from './device-status';

export const error_codes = err_codes;
export const UPDATE_STATUSES = update_statuses;
export const WEB_EVENTS = web_events;
export const WS_DATA_REQUEST = data_requests;
export const DEVICE_UPDATE_EVENT_MESSAGE_TYPES = device_update_event_message_types;
export const DEVICE_UPDATE_STATES = update_states;
export const ARTIFICIAL = 'artificial';
export const GROUP_ALL = 'all';

export const UPDATE_ERROR_CODES = {
  device_no_compatible_hardware: {
    code: 'device_no_compatible_hardware',
    msg: 'Device {deviceId} not affected for $mtuId, device does not have any compatible component',
    summary: `You tried to install an update to the {hardware_id} component, but the device {device_name} doesn't have a {hardware_id} component.`,
  },
  installed_target_is_update: {
    code: 'installed_target_is_update',
    msg: 'Device $deviceId/$ecuIdentifier not affected for $update, installed software package is already present',
    summary: `The device is already running the requested package version.`,
  },
  not_affected_by_mtu: {
    code: 'not_affected_by_mtu',
    msg: 'ecu $deviceId$ecuIdentifier not affected by $mtuId',
    summary: `The device is not affected by the update.`,
  },
  not_affected_running_assignment: {
    code: 'not_affected_running_assignment',
    msg: '${deviceId}/${ecuIdentifier} not affected because it has a running assignment',
    summary: `There's one or more pending update on the target device. You should cancel the pending update or wait until the device reports back whether that update failed or succeeded.`,
  },
  device_has_active_update: {
    code: 'device_has_active_update',
    msg: '${deviceId} not affected for ${ecuIdentifier}, there is an update scheduled for the device',
    summary: `There's one or more active update on the target device. You should cancel the active update or wait until the device reports back whether that update failed or succeeded.`,
  },
  update_already_scheduled_error: {
    code: 'update_already_scheduled_error',
    msg: '${deviceId}/${ecuIdentifier} not affected because it has another update scheduled',
    summary: `There's a scheduled update on the target device. You should cancel the scheduled update or wait until the device reports back whether that update failed or succeeded.`,
  },
  scheduled_update_exists: {
    code: 'scheduled_update_exists',
    msg: '${deviceId}/${ecuIdentifier} not affected because it has another update scheduled',
    summary: `There's a scheduled update on the target device. You should cancel the scheduled update or wait until the device reports back whether that update failed or succeeded.`,
  },
  ecu_assignment_exists: {
    code: 'ecu_assignment_exists',
    msg: '${deviceId}/${ecuIdentifier} not affected because it has a running assignment',
    summary: `There's one or more pending update on the target device. You should cancel the pending update or wait until the device reports back whether that update failed or succeeded.`,
  },
  other: {
    summary: `Unable to initiate update. Reported error: {backend_error_response}`,
  },
};

export const EMAIL_REGEX = new RegExp(
  /(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|"(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21\x23-\x5b\x5d-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])*")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\[(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21-\x5a\x53-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])+)\])/,
);

export const EMPTY_STRING_HASH = 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855'; // sha256 of empty string

export const FREE_TIER_DEVICE_LIMIT = 10;
