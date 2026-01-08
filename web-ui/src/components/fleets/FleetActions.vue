<template>
  <div class="">
    <h6 v-if="!horizontal" class="m-0">Actions</h6>
    <div>
      <div class="row mb-4 h-divide-top-dotted" v-if="horizontal">
        <template v-for="(key, index) in actions">
          <feature-teaser
            v-if="!actionMap[key].hide || !actionMap[key].hide()"
            :feature="(actionMap[key] || {}).featureId"
            :key="key + '_action_item_ft'"
            :class="{
              'v-divide-left-dotted': index > 0,
            }"
            class="col"
          >
            <q-btn
              flat
              @click="(actionMap[key] || {}).action()"
              :key="key + '_action_item'"
              class="full-width pt-1 pb-1"
              :class="{
                'v-divide-left-dotted': index > 0,
              }"
            >
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
                  <div class="col-auto ellipsis q-pr-sm">{{ (actionMap[key] || {}).label }}</div>
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
import { QSpinnerGears } from 'quasar';
import BetaBadge from 'src/components/common/BetaBadge.vue';
import Tooltip from '../common/Tooltip.vue';
export default {
  name: 'FleetActions',
  components: {
    BetaBadge,
    Tooltip,
  },
  props: {
    fleet: {
      type: Object,
      default: () => {
        return {};
      },
    },
    actions: {
      type: Array,
      default: () => {
        return ['update', 'devices', 'rename', 'view', 'hibernate', 'delete'];
      },
    },
    horizontal: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      actionMap: {
        update: {
          color: 'secondary',
          label: 'Initiate Update',
          icon: 'publish',
          summary: 'Initiate update for this device',
          featureId: 'manage-fleet-update',
          action: () => {
            this.createUpdate();
          },
        },
        devices: {
          color: 'secondary',
          label: 'Manage Devices',
          icon: 'fas fa-layer-group',
          summary: 'Show fleet management dialog',
          featureId: 'manage-fleet',
          action: () => {
            this.showFleetManager();
          },
        },
        rename: {
          color: 'secondary',
          label: 'Rename Fleet',
          icon: 'edit',
          summary: 'Rename this fleet',
          featureId: 'manage-fleet',
          action: () => {
            this.showEditDialog();
          },
        },
        view: {
          color: 'secondary',
          label: 'View Detail',
          icon: 'wysiwyg',
          summary: 'View additional information about this fleet',
          featureId: 'view-fleet',
          action: () => {
            this.goToDetail();
          },
        },
        hibernate: {
          color: 'secondary',
          label: 'Hibernate Devices',
          icon: 'mdi-sleep',
          summary: 'Hibernate all devices in this fleet',
          featureId: 'hibernation',
          isBeta: true,
          hide: () => {
            return this.hideHibernateAction;
          },
          action: () => {
            this.toggleHibernation(true);
          },
        },
        wakeup: {
          color: 'secondary',
          label: 'Wake Up Devices',
          icon: 'mdi-sleep-off',
          summary: 'Wake up all devices in this fleet',
          featureId: 'hibernation',
          isBeta: true,
          hide: () => {
            return this.hideHibernateAction;
          },
          action: () => {
            this.toggleHibernation(false);
          },
        },
        delete: {
          color: 'negative',
          label: 'Delete',
          icon: 'delete',
          summary: 'Delete this fleet',
          featureId: 'delete-fleet',
          action: () => {
            this.promptForDelete();
          },
        },
      },
    };
  },
  computed: {
    ...mapGetters({
      isSuperUser: 'users/hasSuperUserAccess',
      isInternalUser: 'users/hasInternalUserAccess',
      betaFeaturesEnabled: 'users/betaFeaturesEnabled',
    }),
    hideHibernateAction() {
      return !this.betaFeaturesEnabled;
    },
  },
  methods: {
    ...mapActions({
      deleteFleet: 'fleets/deleteFleet',
    }),
    goToDetail() {
      this.$router.push({
        name: 'fleet-detail',
        params: { fleetId: this.fleet.id },
      });
    },
    showEditDialog() {
      const fleet = this.fleet || {};
      this.$events.$emit(`dialogs:create-fleet:open`, {
        show: true,
        fleet: fleet || {},
      });
    },
    promptForDelete() {
      const fleet = this.fleet || {};
      this.$events.$emit(`dialogs:confirm:open`, {
        title: `Delete ${fleet.groupName}?`,
        message: `This can't be undone.`,
        color: 'default',
        icon: 'delete',
        yesLabel: 'Yes, please!',
        yesColor: 'negative',
        noLabel: 'No',
        yesAction: () => {
          this.fleetDeleteInProgress = fleet;
          const name = fleet.groupName;
          this.deleteFleet(fleet.id, name)
            .then((deleted) => {
              this.$q.notify({
                color: 'positive',
                message: `${name} deleted!`,
              });
              this.$router.push({ name: 'fleets' });
              this.$emit('deleted', deleted);
            })
            .catch((err) => {
              this.fleetDeleteInProgress = null;
              this.$q.notify({
                message: `Unable to delete ${name}!`,
                color: 'negative',
              });
            });
        },
      });
    },
    showFleetManager() {
      this.$events.$emit('dialogs:fleet-devices-manager:open', { mode: 'fleet', fleet: this.fleet });
    },
    createUpdate() {
      const fleet = this.fleet || {};
      this.$events.$emit(`dialogs:create-device-update:open`, {
        show: true,
        fromFleet: true,
        isFleetUpdate: true,
        update: { fleet },
      });
    },
    hideDetail() {
      this.$emit('hide', {});
    },
    toggleHibernation(hibernated = true) {
      const proceed = () => {
        // Show progress dialog
        let progressDialog = this.$q.dialog({
          title: `${hibernated ? 'Going into hibernation' : 'Waking up'}`,
          message: `The devices in this fleet are ${hibernated ? 'going into hibernation' : 'waking up'}`,
          progress: {
            spinner: QSpinnerGears,
            color: 'primary',
          },
          persistent: true,
          ok: false,
          cancel: false,
        });
        this.$store
          .dispatch('fleets/setDevicesHibernationState', { uuid: this.fleet.id, state: hibernated })
          .then((data) => {
            this.$emit('view-updated', this.fleet);
            this.$q.dialog({
              title: `Success!`,
              message: `Devices in ${this.fleet.groupName} ${!hibernated ? 'are now awake' : 'are now hibernating'}`,
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
      };

      // Show confirmation dialog
      this.$q
        .dialog({
          title: `${hibernated ? 'Hibernate' : 'Wake Up'} Devices`,
          message: `Are you sure you want to ${hibernated ? 'hibernate' : 'wake up'} all devices in ${this.fleet.groupName}?`,
          cancel: true,
          persistent: true,
          ok: {
            color: 'primary',
            label: 'Yes',
          },
          cancel: {
            color: 'primary',
            label: 'No',
            flat: true,
          },
        })
        .onOk(() => {
          proceed();
        });
    },
  },
};
</script>
