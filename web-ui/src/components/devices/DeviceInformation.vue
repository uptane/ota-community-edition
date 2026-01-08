<template>
  <div class="row">
    <div class="col-auto m-0 p-0 pr-3 mb-2">
      <div class="pl-0 ml-0">
        <q-item-label>
          <div class="row q-item-tile label pt-1">
            <div class="col  text-bold">Hardware</div>
          </div>
          <div class="row q-item-tile sublabel pt-1">
            <div class="col-auto pr-1">Device ID:</div>
            <div class="col">{{ device.deviceId }}</div>
          </div>
          <div class="row q-item-tile sublabel pt-1">
            <div class="col-auto pr-1">Hardware ID:</div>
            <div class="col">
              <span dense>{{ ((device.directorAttributes || {}).primary || {}).hardwareId }}</span>
            </div>
          </div>
          <div class="row q-item-tile sublabel pt-1">
            <div class="col-auto pr-1">Last Seen:</div>
            <div class="col">
              <div v-if="device.deviceStatus === 'NotSeen'" class="col">Never</div>
              <div v-if="device.deviceStatus !== 'NotSeen'" class="col">
                <formatted-date :date="device.lastSeen"></formatted-date>
              </div>
            </div>
          </div>

          <div v-if="showAll" class="row q-item-tile sublabel pt-1">
            <div class="col-auto pr-1">Provisioned At:</div>
            <div class="col">
              <formatted-date :date="device.createdAt"></formatted-date>
            </div>
          </div>
          <div v-if="showAll" class="row q-item-tile sublabel pt-1">
            <div class="col-auto pr-1">Activated At:</div>
            <div class="col">
              <div v-if="device.deviceStatus === 'NotSeen'" class="col">Never</div>
              <div v-if="device.deviceStatus !== 'NotSeen'" class="col">
                <formatted-date :date="device.activatedAt"></formatted-date>
              </div>
            </div>
          </div>
          <div v-if="showAll" class="row q-item-tile sublabel pt-1">
            <div class="col-auto pr-1">UUID:</div>
            <div class="col">{{ device.uuid }}</div>
          </div>
        </q-item-label>
      </div>
    </div>
    <div class="col-auto col-xs-12 col-md-auto pr-3  mb-2">
      <div class="pt-1" v-if="!device.networkInfo">
        <div class="row q-item-tile label pb-1">Network</div>
        <span class="opacity-30">Not available</span>
      </div>
      <div v-if="device.networkInfo" class="pl-0">
        <q-item-label>
          <div class="row q-item-tile label pt-1 text-bold">Network</div>
          <div class="row q-item-tile sublabel pt-1">
            <div class="col-auto pr-1">Hostname:</div>
            <div class="col">{{ device.networkInfo.hostname }}</div>
          </div>
          <div class="row q-item-tile sublabel pt-1">
            <div class="col-auto pr-1">Local IPv4:</div>
            <div class="col">{{ device.networkInfo.local_ipv4 }}</div>
          </div>
          <div class="row q-item-tile sublabel pt-1">
            <div class="col-auto pr-1">Mac:</div>
            <div class="col">{{ device.networkInfo.mac }}</div>
          </div>
        </q-item-label>
      </div>
    </div>
    <div class="col-auto  col-xs-12  col-md-auto pr-3  mb-2">
      <div class="pl-0">
        <q-item-label>
          <div class="row q-item-tile label pt-1">
            <div class="col-auto pr-1 text-bold">Fleets</div>
          </div>
          <div class="row q-item-tile sublabel pt-1 opacity-30" v-if="!fleets || fleets.length < 1">
            <div class="col">Not in any fleet</div>
          </div>
          <template v-if="fleets && fleets.length > 0">
            <div v-for="(fleet, index) in fleets" :key="index" class="row q-item-tile sublabel pt-1">
              <div class="col-auto pr-1">{{ index + 1 }}.</div>
              <div class="col ellipsis">{{ fleet.groupName }}</div>
            </div>
          </template>
          <div class="mt-1">
            <feature-teaser feature="manage-device-fleets">
              <q-btn @click="showFleetManager" color="secondary" icon="fas fa-layer-group" flat dense size="sm"
                >&nbsp; Manage fleet
                <tooltip>Change fleets that this device belongs to</tooltip>
              </q-btn>
            </feature-teaser>
          </div>
        </q-item-label>
      </div>
    </div>
    <div v-if="betaFeaturesEnabled" class="col-auto  col-xs-12  col-md-auto pr-3  mb-2">
      <div class="pl-0">
        <q-item-label>
          <div class="row q-item-tile label pt-1">
            <div class="col-auto pr-1 text-bold">Hibernation Status <beta-badge dense size="md" /></div>
          </div>
          <div v-if="device.hibernated" class="row q-item-tile sublabel pt-1">
            <div class="col"><q-icon name="mdi-sleep"></q-icon> In hibernation</div>
          </div>
          <div v-else class="row q-item-tile sublabel pt-1">
            <div class="col  opacity-30">Not in hibernation</div>
          </div>
          <div class="mt-1">
            <q-btn v-if="!device.hibernated" @click="toggleHibernation" color="secondary" flat dense size="sm"
              ><q-icon name="mdi-sleep" size="2em" />&nbsp;Hibernate
              <tooltip>
                Put this device into hibernation mode. It will not be able to connect to the server until it is woken up.
              </tooltip>
            </q-btn>
            <q-btn v-if="device.hibernated" @click="toggleHibernation" color="secondary" flat dense size="sm"
              ><q-icon name="mdi-sleep-off" size="2em" />&nbsp;Wake from hibernation
              <tooltip>
                Wake this device up from hibernation mode. It will be able to connect to the server again.
              </tooltip>
            </q-btn>
          </div>
        </q-item-label>
      </div>
    </div>
    <div v-if="showRemoteShellInfo" class="col-auto  col-xs-12  col-md-auto pr-3  mb-2">
      <div class="pl-0">
        <q-item-label>
          <div class="row q-item-tile label pt-1">
            <div class="col-auto pr-1  text-bold">Remote Shell Session</div>
          </div>
          <template v-if="device.sessionInfo">
            <template v-if="device.sessionInfo.last_ssh_session_connect">
              <template v-if="noActiveSession">
                <div class="col-auto pr-1 pt-1 faded">There is no active remote session</div>
              </template>
              <div v-else-if="device.sessionInfo.last_ssh_session_connect" class="row q-item-tile sublabel pt-1">
                <div class="col-auto pr-1">Last SSH Session Connect:</div>
                <div class="col-auto">
                  <timeago :datetime="device.sessionInfo.last_ssh_session_connect.connected_at" :auto-update="10" />
                  <tooltip>
                    <formatted-date :date="device.sessionInfo.last_ssh_session_connect.connected_at" />
                  </tooltip>
                </div>
              </div>
              <template v-if="device.sessionInfo.last_user_connect">
                <div v-if="device.sessionInfo.last_user_connect.remote_ip" class="row q-item-tile sublabel pt-1">
                  <div class="col-auto pr-1">Last Connected User IP:</div>
                  <div class="col">
                    {{ device.sessionInfo.last_user_connect.remote_ip }}
                  </div>
                </div>
                <div v-if="device.sessionInfo.last_user_connect.connect_at" class="row q-item-tile sublabel pt-1">
                  <div class="col-auto pr-1">Last User Connected At:</div>
                  <div class="col-auto">
                    <timeago :datetime="device.sessionInfo.last_user_connect.connect_at" :auto-update="10" />
                    <tooltip>
                      <formatted-date :date="device.sessionInfo.last_user_connect.connect_at" />
                    </tooltip>
                  </div>
                </div>
              </template>
              <div v-if="sshCommand && !sessionExpired" class="row q-item-tile sublabel pt-1">
                <div class="col-auto pr-1">SSH Command:</div>
                <div class="col-auto">
                  <span class="q-mr-sm faded">{{ sshCommand }}</span>
                  <copy-to-clipboard :text="sshCommand" />
                </div>
              </div>

              <template v-if="expiresAt">
                <div
                  class="row q-item-tile sublabel pt-1"
                  :class="{
                    'text-negative': sessionExpired,
                  }"
                >
                  <div class="col-auto pr-1">{{ expirLabel }}:</div>
                  <div class="col-auto">
                    <timeago :datetime="expiresAt" :auto-update="10"> </timeago>
                    <tooltip>
                      <formatted-date :date="expiresAt" />
                    </tooltip>
                  </div>
                </div>
              </template>
            </template>
            <template v-else>
              <div class="col-auto pr-1">Session requested, waiting for device</div>
            </template>

            <div class="mt-1">
              <feature-teaser feature="view-remote-access-manager">
                <div>
                  <q-btn @click="gotoRemoteAccessManager" color="secondary" icon="fas fa-layer-group" flat dense size="sm"
                    >&nbsp; Manage Remote Sessions
                    <tooltip>Manage remote ssh sessions</tooltip>
                  </q-btn>
                </div>
                <div v-if="sshSession">
                  <q-btn @click="confirmTerminateSession" color="secondary" icon="cancel" flat dense size="sm"
                    >&nbsp; Terminate current session
                    <tooltip>Terminate current session on this device </tooltip>
                  </q-btn>
                </div>
              </feature-teaser>
            </div>
          </template>
          <div v-else>
            <div class="faded row q-item-tile sublabel pt-1">No remote shell session info available</div>
          </div>
        </q-item-label>
      </div>
    </div>
    <div class="pl-0 ml-0 col-12">
      <device-comment :device="device" editable color="secondary"></device-comment>
    </div>
    <div class="pl-0 ml-0 col-12" v-if="device.lastSeen && device.lastSeen !== 'Never'">
      <q-separator class="mt-1 mb-1"></q-separator>

      <q-item-label>
        <div class="row q-item-tile label pt-1">
          <div class="col">Update Status</div>
        </div>
        <div class="row q-item-tile pt-1">
          <div class="col">
            <update-status-indicator size="1.5rem" :device="device" auto-update-scheduled-status> </update-status-indicator>
          </div>
        </div>
      </q-item-label>
    </div>
    <div class="col-12">
      <device-package-information :device="device"></device-package-information>
    </div>
  </div>
</template>

<script>
import { mapGetters, mapActions } from 'vuex';
import { QSpinnerGears } from 'quasar';
import FormattedDate from '../common/FormattedDate.vue';
import Tooltip from '../common/Tooltip.vue';
import UpdateStatusIndicator from '../updates/UpdateStatusIndicator.vue';
import DeviceComment from './DeviceComment.vue';
import DevicePackageInformation from './DevicePackageInformation.vue';
import DeviceStatus from './DeviceStatus.vue';
import BetaBadge from '../common/BetaBadge.vue';
import CopyToClipboard from '../common/CopyToClipboard.vue';
export default {
  components: { Tooltip, DevicePackageInformation, DeviceStatus, FormattedDate, UpdateStatusIndicator, DeviceComment, BetaBadge, CopyToClipboard },
  name: 'DeviceInformation',
  props: {
    device: {
      type: Object,
      default: () => {
        return {};
      },
    },
    showAll: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      showAsTimeAgo: {
        ssh_expires_at: false,
      },
      sshSession: null,
      pollingInterval: null,
    };
  },
  computed: {
    ...mapGetters({
      betaFeaturesEnabled: 'users/betaFeaturesEnabled',
      isSuperUser: 'users/hasSuperUserAccess',
      isInternalUser: 'users/hasInternalUserAccess',
    }),
    expirLabel() {
      if (this.sshSession && this.sshSession.ssh && this.sshSession.ssh.expires_at && new Date(this.sshSession.ssh.expires_at).getTime() >= Date.now()) {
        return 'Session Expires';
      }
      return 'Session Expired';
    },
    fleets() {
      return this.device.fleets || [];
    },
    isUpdating() {
      return this.device.deviceStatus && (this.device.deviceStatus === 'Outdated' || this.device.deviceStatus === 'Pending') && Date.now() - new Date(this.device.lastSeen).getTime() < 30 * 60 * 1000;
    },
    showRemoteShellInfo() {
      return true;
    },
    expiresAt() {
      return (this.device.sessionInfo || {}).expires_at || ((this.sshSession || {}).ssh || {}).expires_at;
    },
    sessionExpired() {
      if (!this.expiresAt) {
        return true;
      }
      return new Date(this.expiresAt).getTime() < Date.now();
    },
    noActiveSession() {
      return !this.expiresAt;
    },
    sshCommand() {
      return (this.sshSession || {}).ssh_command;
    },
  },
  mounted() {
    this.fetchFleets();
    if (!_.isEmpty(this.device)) {
      this.fetchDeviceInfo(this.device);
    }
    this.$events.$on('device-ssh-session:updated', ({ device, session }) => {
      if (device.uuid === this.device.uuid) {
        this.sshSession = session;
        this.$emit('update:device', { ...this.device, ssh_session: session });
      }
    });
    this.pollSessionInfo();
  },
  beforeDestroy() {
    // Let's make sure we clear the interval when the component is destroyed
    clearInterval(this.pollingInterval);
  },
  methods: {
    ...mapActions({
      fetchFleets: 'fleets/fetchFleets',
      fetchDevice: 'devices/fetchDevice',
      fetchSession: 'remoteAccess/fetchSession',
    }),
    pollSessionInfo() {
      // Just for due diligence, let's clear the interval if it's already set
      clearInterval(this.pollingInterval);

      // Poll for session info every 10 seconds
      this.pollingInterval = setInterval(() => {
        // There are some conditions where we should stop polling
        // 1. If the session has expired ** not sure about this one yet **
        // 2. If the device is not seen
        // 3. If the device is in hibernation
        if (/* this.sessionExpired || */ this.device.deviceStatus === 'NotSeen' || this.device.hibernated) {
          return clearInterval(this.pollingInterval);
        }
        // We only poll if there's a valid device (uuid present) and the remote shell info is shown
        if (this.device.uuid && this.showRemoteShellInfo) {
          this.fetchSessionInfo();
        }
      }, 30000);
    },
    fetchDeviceInfo(device) {
      this.fetchDevice(device);
      this.fetchSessionInfo();
    },
    fetchSessionInfo() {
      const device = this.device;
      this.fetchSession(this.device)
        .then((data) => {
          this.sshSession = data;
          this.$emit('update:device', { ...this.device, ssh_session: data });
        })
        .catch((err) => {
          this.$emit('update:device', { ...this.device, ssh_session: null });
        });
    },
    showFleetManager() {
      this.$events.$emit('dialogs:fleet-devices-manager:open', { mode: 'device', device: this.device });
    },
    toggleHibernation() {
      // Show progress dialog
      let progressDialog = this.$q.dialog({
        title: `This device is ${!this.device.hibernated ? 'going into hibernation' : 'waking up'}`,
        message: `${this.device.deviceName} ${!this.device.hibernated ? 'is going into hibernation' : 'is waking up'}`,
        progress: {
          spinner: QSpinnerGears,
          color: 'primary',
        },
        persistent: true,
        ok: false,
        cancel: false,
      });
      let hibernated = !this.device.hibernated;
      this.$store
        .dispatch('devices/setDeviceHibernationState', { uuid: this.device.uuid, state: hibernated })
        .then((data) => {
          this.$emit('update:device', { ...this.device, hibernated });
          this.$q.dialog({
            title: `Success!`,
            message: `Device ${this.device.deviceName} ${!hibernated ? 'is now awake' : 'is now hibernating'}`,
            ok: {
              color: 'primary',
              label: 'ok',
            },
          });
        })
        .catch((error) => {
          this.$q.dialog({
            title: `Error`,
            message: `Error while updating device hibernation state: ${error}`,
            ok: {
              color: 'primary',
              label: 'ok',
            },
          });
        })
        .finally(() => {
          progressDialog.hide();
        });
    },
    gotoRemoteAccessManager() {
      this.$router.push({ name: 'remote-access' });
    },
    confirmTerminateSession() {
      this.$q
        .dialog({
          title: 'Terminate current session',
          message: 'Are you sure you want to terminate the current shell session on this device?',
          ok: {
            color: 'primary',
            label: 'cancel',
          },
          cancel: {
            color: 'primary',
            label: 'terminate',
            outline: true,
          },
        })
        .onCancel(() => {
          this.terminateSession();
        });
    },
    terminateSession() {
      let loader = this.$q.dialog({
        title: 'Terminating session',
        message: 'Please wait while the session is terminated',
        persistent: true,
        ok: false,
        cancel: false,
        progress: {
          spinner: QSpinnerGears,
          color: 'primary',
        },
      });
      this.$store
        .dispatch('remoteAccess/killSession', { deviceUuid: this.device.uuid })
        .then(() => {
          this.$q.notify({
            message: 'Session terminated',
            color: 'positive',
            icon: 'check',
          });
          this.sshSession = null;
          this.$emit('update:device', { ...this.device, ssh_session: null });
        })
        .catch((err) => {
          this.$q.dialog({
            title: 'Session termination failed',
            message: err || 'An error occurred while terminating the session',
            cancel: true,
            persistent: true,
            ok: {
              label: 'Close',
              color: 'primary',
              flat: true,
            },
            cancel: false,
          });
          console.log(err);
        })
        .finally(() => {
          loader.hide();
          this.pollSessionInfo();
        });
    },
  },

  watch: {
    device: {
      handler(newValue, oldValue) {
        if ((!oldValue && newValue) || (oldValue && newValue && oldValue.uuid !== newValue.uuid)) {
          this.fetchSessionInfo();
          // We need to poll for session info if the device has changed
          this.pollSessionInfo();
        }
      },
      deep: true,
    },
  },
};
</script>
