<template>
  <q-card
    class=" device-item-wrapper hoverable clickable overflow-hidden animated fadeIn"
    :class="{
      'for-layout-cards': layoutType === 'cards',
      'for-layout-list': layoutType !== 'cards',
      'for-view-type-thick': viewType === 'thick',
      selected: selected,
      'for-view-type-thin no-border-radius shadow-1': viewType === 'thin',
      [(device.badgeData || {}).summary || 'not-seen']: true,
    }"
    :style="{
      width: $q.screen.lt.md || layoutType !== 'cards' || isThinOrThickItem ? (isThinOrThickItem ? '100%' : 'auto') : viewSizeRem,
    }"
    @click.native="showDeviceDatail()"
  >
    <!-- 'bg-grey-2':device.hibernated, -->
    <tooltip v-if="viewType === 'thin'" content-class="bg-black" transition-hide="" transition-show="">
      <div class="text-bold">{{ device.deviceName }}</div>
      <div class="opacity-50">ID: {{ device.deviceId }}</div>
      <div class="opacity-50">UUID: {{ device.uuid }}</div>
    </tooltip>
    <q-item :style="{}" :class="{ 'shadow-0': viewType === 'thin' }">
      <!-- v-if="viewType!='card'" -->
      <q-item-section avatar top class="device-img" v-if="!hideIcon" :size="viewType === 'thin' ? 'xs' : 'md'">
        <q-img
          class=" w-100"
          :class="{
            'opacity-20': showOnlineBadge && viewType === 'thin',
          }"
          src="statics/svg/icons/som.svg"
        ></q-img>
      </q-item-section>

      <q-item-section :class="{ relaxed: viewType === 'relaxed' }">
        <q-item-label class="pr-1">
          <div class="q-item-tile label device-name ellipsis mxw-80">{{ device.deviceName }}</div>
          <div
            class="q-item-tile sublabel device-id ellipsis mxw-90"
            v-if="!minimal"
            :class="{
              ' w-80': isListLayout || isThinItem,
            }"
          >
            ID: {{ device.deviceId }}
          </div>
          <div v-if="!isThinItem && !isListLayout && device.hardwareType" class="q-item-tile sublabel  device-id ellipsis">Hardware: {{ device.hardwareType }}</div>
          <div v-if="!isThinItem || isListLayout">
            <div v-if="device.deviceStatus !== 'NotSeen'" class="row q-item-tile sublabel last-seen text-ellipsis w-90">
              <div class="col-auto">Last seen: &nbsp;</div>
              <timeago class="col w-70 ellipsis" :datetime="device.lastSeen" :auto-update="10"></timeago>
            </div>
            <div v-if="device.deviceStatus === 'NotSeen'" class="q-item-tile sublabel last-seen">Last seen: Never</div>
            <span class="text-negative p-1" v-if="deviceDeleteInProgress && deviceDeleteInProgress.uuid === device.uuid">(Deleting)</span>
          </div>

          <div v-if="!isThinItem && !isListLayout && viewType !== 'thick'" class="q-item-tile sublabel device-id row">
            <div class="col-auto">Activated at: &nbsp;</div>
            <span v-if="device.deviceStatus === 'NotSeen'"></span>
            <formatted-date class="col w-70 ellipsis" v-else :date="device.activatedAt"></formatted-date>
          </div>
          <div v-if="!isThinItem && !isListLayout && viewType !== 'thick'" class="q-item-tile sublabel device-id ellipsis">UUID: {{ device.uuid }}</div>

          <div class="q-item-tile device-id row" v-if="!isThinItem && !minimal">
            <div class="col-auto sublabel" v-if="!hideIcon && device.deviceStatus !== 'Outdated' && device.deviceStatus !== 'UpdatePending'">Status:&nbsp;</div>
            <div class="col" :class="{}">
              <update-status-indicator :device="device" size="0.8rem" minimized> </update-status-indicator>
            </div>
          </div>
        </q-item-label>
      </q-item-section>
      <q-item-section class="option-section absolute-top-right" avatar>
        <!-- <q-menu auto-close transition-show="jump-down" transition-hide="jump-up">
            <device-menu :device="device"></device-menu>
          </q-menu> -->
        <feature-teaser class="inline-block" feature="create-device-update">
          <q-btn :id="device.deviceId + '-menu'" v-if="!deviceDeleteInProgress || deviceDeleteInProgress.uuid !== device.uuid" icon="publish" flat @click.stop="createUpdate()" color="secondary" style="padding: .25rem;">
            <tooltip>Initiate update for this device.</tooltip>
          </q-btn>
        </feature-teaser>
        <loader v-if="deviceDeleteInProgress && deviceDeleteInProgress.uuid === device.uuid"></loader>
      </q-item-section>
      <!--  -->
    </q-item>
    <device-online-badge :device="device"></device-online-badge>
  </q-card>
</template>

<script>
import Loader from '../loaders/Loader';
import DeviceStatus from './DeviceStatus';
import DeviceMenu from '../menus/DeviceMenu';
import DeviceOnlineBadge from './DeviceOnlineBadge';
import FormattedDate from '../common/FormattedDate.vue';
import UpdateStatusIndicator from '../updates/UpdateStatusIndicator.vue';
import Tooltip from '../common/Tooltip.vue';

export default {
  name: 'DeviceItem',
  components: {
    Loader,
    DeviceMenu,
    DeviceOnlineBadge,
    DeviceStatus,
    FormattedDate,
    UpdateStatusIndicator,
    Tooltip,
  },
  props: {
    device: {
      type: Object,
      default: () => {
        return {};
      },
    },
    layoutType: {
      type: String,
      default: 'list',
    },
    viewType: {
      type: String,
      default: 'thick',
    },
    viewSize: {
      type: Number,
      default: 20,
    },
    selected: {
      type: Boolean,
      default: false,
    },
    hideIcon: {
      type: Boolean,
      default: false,
    },
    minimal: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {};
  },
  created() {},
  mounted() {},
  beforeDestroy() {},
  computed: {
    deviceDeleteInProgress() {},
    showOnlineBadge() {
      return ((this.device || {}).badgeData || {}).show;
      // (this.device || {}).deviceStatus &&
      //     (this.device || {}).deviceStatus !== "NotSeen"
    },
    isThinItem() {
      return this.viewType === 'thin';
    },
    isThickItem() {
      return this.viewType === 'thick';
    },
    isThinOrThickItem() {
      return this.isThinItem || this.isThickItem;
    },
    isListLayout() {
      return this.layoutType === 'list';
    },
    deviceUpdating() {
      return this.$store.getters['ui/devicesWithUpdateInProgress'][(this.device || {}).uuid];
    },
    viewSizeRem() {
      return this.viewType === 'dense' ? '22rem' : this.viewSize + 'rem';
    },
  },
  methods: {
    showDeviceDatail() {
      this.$emit('item-click', this.device);
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
  },
};
</script>
