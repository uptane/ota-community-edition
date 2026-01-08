<template>
  <div class="fleet-menu-wrapper">
    <q-list link separator class="mt-0 pt-0" style="min-width: 10em; max-height: 35em;">
      <q-item class="menu-title text-black p-0 m-0">
        <q-item-section class="text-center opacity-60 pt-1 pb-1">{{ fleet.groupName }}</q-item-section>
      </q-item>
      <template v-for="(menu, index) in menuItems">
        <q-item v-if="!menu.hide" :class="menu.cssClass" class="menu-item items-center" :key="index" @click.native="menu.action">
          <q-item-section avatar>
            <q-icon :class="'' + menu.icon.cssClass" :name="menu.icon.name" :size="menu.icon.size"></q-icon>
          </q-item-section>
          <q-item-label>
            <div>{{ menu.text }}</div>
          </q-item-label>
        </q-item>
      </template>
    </q-list>
  </div>
</template>

<script>
import { mapActions } from 'vuex';
export default {
  components: {},
  props: {
    fleet: {
      type: Object,
      default: () => {
        return { devices: [{}] };
      },
    },
  },
  data() {
    return {
      menuItems: [
        {
          text: 'View devices',
          cssClass: 'text-black',
          icon: {
            name: 'info',
            cssClass: '',
            size: '2rem',
          },
          hide: !(this.fleet.devices || {}).total > 0,
          action: () => {
            this.showDevices();
          },
        },
        {
          text: 'Add or remove devices',
          cssClass: 'text-black',
          icon: {
            name: 'playlist_add',
            cssClass: '',
            size: '2rem',
          },
          // hide: !this.fleet.devices.total>0,
          action: () => {
            this.showFleetManager();
          },
        },
        {
          text: 'Create update',
          cssClass: 'text-black',
          icon: {
            name: 'fa fa-cloud-upload-alt',
            cssClass: '',
            size: '1.3rem',
          },
          hide: !(this.fleet.devices || {}).total > 0,
          action: () => {
            this.createUpdate();
          },
        },
        {
          text: 'Rename this fleet',
          cssClass: 'text-black',
          icon: {
            name: 'edit',
            cssClass: '',
            size: '2rem',
          },
          action: () => {
            this.showEditDialog();
          },
        },
        {
          text: 'Remove this fleet',
          cssClass: 'text-negative',
          icon: {
            name: 'delete',
            cssClass: 'text-negative',
            size: '2rem',
          },
          action: () => {
            this.promptForDelete();
          },
        },
      ],
    };
  },
  methods: {
    ...mapActions({
      fetchFleets: 'fleets/fetchFleets',
      deleteFleet: 'fleets/deleteFleet',
    }),
    showDevices() {
      this.$emit('show-devices', this.fleet);
    },
    showFleetManager() {
      this.$router.push({ name: 'fleet-manager', query: { fleetId: this.fleet.id } });
    },
    showEditDialog() {
      this.$events.$emit(`dialogs:create-fleet:open`, {
        show: true,
        fleet: this.fleet || {},
      });
    },
    createUpdate() {
      this.$events.$emit(`dialogs:create-device-update:open`, {
        show: true,
        isFleetUpdate: true,
        update: { fleet: this.fleet },
      });
    },
    promptForDelete() {
      this.$events.$emit(`dialogs:confirm:open`, {
        title: `Delete ${this.fleet.groupName}?`,
        message: `This can't be undone`,
        color: 'default',
        icon: 'delete',
        yesLabel: 'Yes, please!',
        yesColor: 'negative',
        noLabel: 'No',
        yesAction: () => {
          this.fleetDeleteInProgress = this.fleet;
          const name = this.fleet.groupName;
          this.deleteFleet(this.fleet.id, name)
            .then((deleted) => {
              this.$q.notify({
                color: 'positive',
                message: `${name} deleted!`,
              });
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
  },
  computed: {
    fleetDeleteInProgress: {
      get() {
        return this.$store.getters['ui/fleetDeleteInProgress'];
      },
      set(val) {
        this.$store.commit('ui/setFleetDeleteInProgress', val);
      },
    },
  },
};
</script>
