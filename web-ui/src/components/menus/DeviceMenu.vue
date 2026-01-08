<template>
  <div class="device-menu-wrapper">
    <q-list link separator class="p-0" style="min-width: 10em; max-height: 35em;">
      <q-item class="text-black text-center p-0 m-0">
        <q-item-section header class="menu-title bg-grey-1 pt-1 pb-1 opacity-60">{{ device.deviceName }}</q-item-section>
      </q-item>
      <template v-for="(menu, index) in menuItems">
        <feature-teaser :feature="menu.featureId" :key="index">
          <q-item v-if="!menu.hide" :class="'menu-item items-center ' + menu.cssClass" @click.native="menu.action">
            <q-item-section avatar>
              <q-icon v-if="menu.icon.type !== 'image'" :class="menu.icon.cssClass" :size="menu.icon.size" :name="menu.icon.name" />
              <img v-if="menu.icon.type === 'image'" :class="menu.icon.cssClass" :style="{ 'max-width': menu.icon.size }" :src="menu.icon.name" />
              <!-- <img style="width:2rem" src="statics/svg/icons/car-fleet-inverted.svg"> -->
            </q-item-section>
            <q-item-label>
              <div>{{ menu.text }}</div>
            </q-item-label>
          </q-item>
        </feature-teaser>
      </template>
    </q-list>
  </div>
</template>

<script>
import { mapActions } from 'vuex';
export default {
  components: {},
  props: {
    device: {
      type: Object,
      default: () => {
        return {};
      },
    },
  },
  data() {
    return {
      menuItems: [
        {
          text: 'Change device fleets',
          cssClass: 'text-black  add-device-to-fleet',
          featureId: 'manage-device-fleets',
          icon: {
            name: 'playlist_add',
            cssClass: 'opacity-100',
            size: '2rem',
            // type: "image"
          },
          hide: !this.device.activatedAt,
          action: () => {
            this.showFleetManager();
          },
        },
        {
          text: 'View info',
          cssClass: 'text-black view-device-info',
          featureId: 'view-device-detail',
          icon: {
            name: 'visibility',
            cssClass: '',
            size: '2rem',
          },
          action: () => {
            this.showDeviceDatail();
          },
        },
        {
          text: 'Create update',
          cssClass: 'text-black',
          featureId: 'create-device-update',
          icon: {
            name: 'fa fa-cloud-upload-alt',
            cssClass: '',
            size: '1.4rem',
          },

          action: () => {
            this.createUpdate();
          },
        },

        {
          text: 'Rename this device',
          cssClass: 'text-black rename-dvice',
          featureId: 'update-device-info',
          icon: {
            name: 'edit',
            cssClass: '',
            size: '2rem',
          },
          action: () => {
            this.showEditDialog();
          },
        },
      ],
    };
  },
  methods: {
    ...mapActions({
      deleteDevice: 'devices/deleteDevice',
    }),
    showDeviceDatail() {
      this.$router.push({
        name: 'device-detail',
        params: { deviceId: this.device.uuid },
      });
    },
    showEditDialog() {
      this.$events.$emit(`dialogs:create-device:open`, {
        show: true,
        device: this.device || {},
      });
    },
    showFleetManager() {
      this.$router.push({ name: 'fleet-manager', query: { deviceId: this.device.uuid } });
    },
    createUpdate() {
      const selectedDevice = this.device;
      this.$events.$emit(`dialogs:create-device-update:open`, {
        show: true,
        isFleetUpdate: false,
        selectedDevice,
        fromDeviceDetail: true,
        update: { devices: [selectedDevice] },
      });
    },
    showAddToFleetDialog() {
      this.$events.$emit(`dialogs:fleet-devices-manager:open`, {
        show: true,
        device: this.device || {},
      });
    },

    promptForDelete() {
      this.$events.$emit('dialogs:confirm:open', {
        title: `Delete ${this.device.deviceName}?`,
        message: `This can't be undone`,
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
              this.$q.notify({
                color: 'positive',
                message: `${name} deleted!`,
              });
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
      // .then(() => {
      //   )
      // .catch(() => {
      //   //   this.$q.notify({message: "Agreed!"});
      // });
    },
  },
  computed: {
    deviceDeleteInProgress: {
      get() {
        return this.$store.getters['ui/deviceDeleteInProgress'];
      },
      set(val) {
        this.$store.commit('ui/setDeviceDeleteInProgress', val);
      },
    },
  },
};
</script>
