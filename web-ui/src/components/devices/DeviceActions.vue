<template>
  <div class="">
    <h6 v-if="!horizontal" class="m-0">Actions</h6>
    <div>
      <div class="row mb-4 h-divide-top-dotted" v-if="horizontal">
        <template v-for="(key, index) in actions">
          <feature-teaser
            v-if="!actionMap[key].hide || !actionMap[key].hide()"
            :feature="(actionMap[key] || {}).featureId"
            :key="index"
            :class="{
              'v-divide-left-dotted': index > 0,
            }"
            class="col"
          >
            <q-btn flat @click="(actionMap[key] || {}).action()" :key="key + '_action_item'" class="full-width pt-1 pb-1">
              <div class="row">
                <div class="col ellipsis q-pr-sm">
                  <q-icon :color="(actionMap[key] || {}).color" :name="(actionMap[key] || {}).icon" class="q-mr-sm" />
                  {{ (actionMap[key] || {}).label }}
                </div>
                <div class="col-auto">
                  <beta-badge v-if="(actionMap[key] || {}).isBeta" size="sm" class="" key="beta-badge" />
                </div>
              </div>
              <tooltip>{{ (actionMap[key] || {}).label }}</tooltip>
            </q-btn>
          </feature-teaser>
        </template>
      </div>
      <q-list v-else>
        <template v-for="(key, index) in actions">
          <feature-teaser v-if="!actionMap[key].hide || !actionMap[key].hide()" :feature="(actionMap[key] || {}).featureId" :key="index">
            <q-item
              @click="(actionMap[key] || {}).action()"
              clickable
              v-ripple
              :key="key + '_action_item'"
              :class="{
                'h-divide-top-dotted': index > 0,
              }"
            >
              <q-item-section avatar>
                <q-icon :color="(actionMap[key] || {}).color" :name="(actionMap[key] || {}).icon" />
              </q-item-section>

              <q-item-section>
                <div class="row">
                  <div class="col-auto ellipsis q-pr-sm">
                    {{ (actionMap[key] || {}).label }}
                  </div>
                  <div class="col-auto">
                    <beta-badge v-if="(actionMap[key] || {}).isBeta" size="sm" class="" key="beta-badge" />
                  </div>
                </div>
              </q-item-section>
              <q-item-section avatar class="ml-2">
                <q-icon name="keyboard_arrow_right" />
              </q-item-section>
            </q-item>
          </feature-teaser>
        </template>
      </q-list>
    </div>
  </div>
</template>

<script>
import { mapActions, mapGetters } from 'vuex';
import RemoteShellSessionDialog from 'src/components/users/remote-access/RemoteShellSessionDialog.vue';
import { QSpinnerGears } from 'quasar';
import BetaBadge from 'src/components/common/BetaBadge.vue';
import Tooltip from '../common/Tooltip.vue';
import CreateRemoteAccessDialog from '../users/remote-access/CreateRemoteAccessDialog.vue';
export default {
  name: 'DeviceActions',
  components: {
    BetaBadge,
    Tooltip,
  },
  props: {
    device: {
      type: Object,
      default: () => {
        return {};
      },
    },
    actions: {
      type: Array,
      default: () => {
        return ['update', 'fleet', 'rename', 'view', 'remoteAccess', 'hibernate', 'delete'];
      },
    },
    horizontal: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      loadingDialog: null,
    };
  },
  computed: {
    ...mapGetters({
      betaFeaturesEnabled: 'users/betaFeaturesEnabled',
      isSuperUser: 'users/hasSuperUserAccess',
      isInternalUser: 'users/hasInternalUserAccess',
    }),
    actionMap() {
      return {
        update: {
          color: 'secondary',
          label: 'Initiate Update',
          icon: 'publish',
          summary: 'Initiate update for this device',
          featureId: 'create-device-update',
          action: () => {
            this.createUpdate();
          },
        },
        fleet: {
          color: 'secondary',
          label: 'Manage Fleets',
          icon: 'fas fa-layer-group',
          summary: 'Show fleet management dialog',
          featureId: 'manage-device-fleets',
          action: () => {
            this.showFleetManager();
          },
        },
        rename: {
          color: 'secondary',
          label: 'Rename Device',
          icon: 'edit',
          summary: 'Rename this device',
          featureId: 'update-device-info',
          action: () => {
            this.showEditDialog();
          },
        },
        view: {
          color: 'secondary',
          label: 'View Detail',
          icon: 'wysiwyg',
          summary: 'View additional information about this device',
          featureId: 'view-device-detail',
          action: () => {
            this.showDeviceDatail();
          },
        },
        remoteAccess: {
          color: 'secondary',
          label: 'Remote Shell',
          icon: 'wifi',
          summary: 'Enable remote access for this device',
          featureId: 'use-remote-access',
          isBeta: false,
          hide: () => {
            return this.hideRemoteShellAction;
          },
          action: () => {
            this.createRemoteAccessSession();
          },
        },
        hibernate: {
          color: 'secondary',
          label: this.device.hibernated ? 'Wake Up' : 'Hibernate',
          icon: this.device.hibernated ? 'mdi-sleep-off' : 'mdi-sleep',
          summary: this.device.hibernated ? 'Wake up this device' : 'Hibernate this device',
          featureId: 'hibernation',
          isBeta: true,
          hide: () => {
            return this.hideHibernateAction;
          },
          action: () => {
            this.toggleHibernation();
          },
        },
        delete: {
          color: 'negative',
          label: 'Delete',
          icon: 'delete',
          summary: 'Delete this device',
          featureId: 'delete-device',
          action: () => {
            this.promptForDelete();
          },
        },
      };
    },
    hideRemoteShellAction() {
      return false;
    },
    hideHibernateAction() {
      return !this.betaFeaturesEnabled;
    },
  },
  methods: {
    ...mapActions({
      deleteDevice: 'devices/deleteDevice',
      createSshSession: 'remoteAccess/createSession',
      fetchSession: 'remoteAccess/fetchSession',
    }),
    showDeviceDatail() {
      this.$router.push({
        name: 'device-detail',
        params: { deviceId: this.device.uuid },
      });
    },
    showEditDialog() {
      this.$events.$emit(`dialogs:rename-device:open`, {
        show: true,
        device: this.device || {},
      });
    },
    showFleetManager() {
      this.$events.$emit('dialogs:fleet-devices-manager:open', { device: this.device });
    },
    createUpdate() {
      const selectedDevice = this.device;
      this.$events.$emit(`dialogs:create-device-update:open`, {
        isFleetUpdate: false,
        selectedDevice,
        fromDeviceDetail: true,
        update: { devices: [selectedDevice] },
      });
    },
    hideDetail() {
      this.$emit('hide', {});
    },
    showLoadingDialog() {
      this.hideLoadingDialog();
      this.loadingDialog = this.$q.dialog({
        message: 'Please wait ...',
        progress: {
          color: 'primary',
        },
        persistent: true,
        ok: false,
        cancel: false,
      });
    },
    hideLoadingDialog() {
      this.loadingDialog && this.loadingDialog.hide();
      this.loadingDialog = null;
    },
    createRemoteAccessSession() {
      if (!this.device.sessionInfo) {
        this.$q.dialog({
          title: 'Remote access client is not installed',
          html: true,
          message: 'Please install the remote access client on the device to enable remote shell access. \n' + "You can follow the instructions <a href='https://developer.toradex.com/torizon/torizon-platform/remote-access/#1-enable-the-remote-access-on-the-target-device' target='_blank'>here</a>.",
          cancel: false,
          ok: {
            outline: true,
            label: 'Close',
            color: 'primary',
          },
        });
      } else {
        this.showLoadingDialog();
        // Function to start session creation
        const beginSessionCreation = () => {
          this.$q
            .dialog({
              component: CreateRemoteAccessDialog,
              title: 'Create Remote Access Session',
            })
            .onOk(({ duration }) => {
              this.showLoadingDialog();
              this.createSshSession({ uuid: this.device.uuid, duration: `${duration}s` })
                .then((data) => {
                  if (data && data.ssh_command) {
                    this.$events.$emit('device-ssh-session:updated', { device: this.device, session: data });
                    this.$q
                      .dialog({
                        sshCommand: data.ssh_command,
                        component: RemoteShellSessionDialog,
                        cancel: {
                          outline: true,
                          label: 'Close',
                          color: 'primary',
                        },
                        ok: {
                          label: 'Manage Sessions',
                          color: 'primary',
                        },
                      })
                      .onOk(() => {
                        this.$router.push({
                          name: 'remote-access',
                        });
                      });
                  } else {
                    this.$q.dialog({
                      title: 'Failed to start remote shell session',
                      html: true,
                      message: 'Please try again later.',
                      cancel: false,
                      ok: {
                        outline: true,
                        label: 'Close',
                        color: 'primary',
                      },
                    });
                  }
                })
                .catch((err) => {
                  this.$q.dialog({
                    title: 'Failed to start remote shell session',
                    html: true,
                    message: err || 'Please try again later.',
                    cancel: false,
                    ok: {
                      outline: true,
                      label: 'Close',
                      color: 'primary',
                    },
                  });
                })
                .finally(() => {
                  this.hideLoadingDialog();
                });
            });
        };
        // Fetch existing session
        this.fetchSession({ uuid: this.device.uuid })
          .then((sessionData) => {
            let sessionWarning = `There is already an active remote session for this device that will expire at ${this.$date.formatDate(sessionData.ssh.expires_at, 'YYYY-MM-DD HH:mm:ss')}. Do you want to terminate the existing session and start a new one?`;
            if (new Date(sessionData.ssh.expires_at).getTime() < Date.now()) {
              sessionWarning = `A remote session exists for this device but it has expired. The session will now be terminated and a new one will be created.`;
            }
            this.$q
              .dialog({
                title: 'Remote Session Already Exists',
                message: `${sessionWarning}`,
                progress: false,
                ok: {
                  label: 'Terminate Session',
                  color: 'primary',
                  flat: false,
                },
                cancel: {
                  label: 'Cancel',
                  color: 'primary',
                  flat: true,
                },
              })
              .onOk(() => {
                this.$store.dispatch('remoteAccess/killSession', { deviceUuid: this.device.uuid }).finally(() => {
                  beginSessionCreation();
                });
              });
          })
          .catch(() => {
            beginSessionCreation();
          })
          .finally(() => {
            this.hideLoadingDialog();
          });
      }
    },
    promptForDelete() {
      this.$events.$emit('dialogs:confirm:open', {
        title: `Delete ${this.device.deviceName}?`,
        message: `This can't be undone.`,
        color: 'default',
        icon: 'delete',
        yesFlat: true,
        yesClass: 'delete',
        yesLabel: 'Yes, please!',
        yesColor: 'negative',
        yesAction: () => {
          this.deviceDeleteInProgress = this.device;
          const name = this.device.deviceName;
          this.deleteDevice(this.device.uuid)
            .then((deleted) => {
              this.show = false;
              this.$q.notify({
                color: 'positive',
                message: `${name} deleted!`,
              });
              this.$emit('deleted', {});
            })
            .catch((err) => {
              this.deviceDeleteInProgress = null;
              this.$q.notify({
                message: `Unable to delete ${name}!`,
                color: 'negative',
              });
            });
        },
        noFlat: true,
        noLabel: 'No',
        noAction: () => {},
      });
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
          this.device.hibernated = hibernated;
          this.$emit('update:device', this.device);
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
  },
};
</script>
