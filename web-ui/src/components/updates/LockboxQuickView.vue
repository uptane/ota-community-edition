<template>
  <div class="device-quick-view  ml-1">
    <q-card-section class="pt-2 pb-2 p-0">
      <div class="p-0 row pt-0 mt-0">
        <h5 class="m-0 pl-1 pr-2 col-12 pb-1">
          <div class="row">
            <div class="gt-xs col">
              <div class="row w-00">
                <div class="col-auto ellipsis">
                  {{ item.name }}
                </div>
                <div class="col-auto">
                  <q-btn flat @click="createUpdate" color="secondary" icon="edit">
                    <tooltip>Modify</tooltip>
                  </q-btn>
                </div>
              </div>
            </div>
            <div class="col-auto pl-2">
              <q-btn class="absolute-top-right mt-1 mr-1" flat @click="hideDetail" icon="close">
                <tooltip>Hide detail</tooltip>
              </q-btn>
            </div>
            <div class="lt-sm col-12 text-center pt-2">{{ item.name }}</div>
          </div>
        </h5>
      </div>
      <div
        class="row pl-1 pr-0 mnh-80vh"
        style="overflow-y:auto; "
        :style="{
          'max-height': parentHeight,
        }"
      >
        <div class="col-12 col-xl-8 h-auto">
          <div class="row q-item-tile hash sublabel pt-1 pr-1 text-1">
            <span class="pr-1 opacity-60">Status:</span>
            <span class=" ellipsis col">
              <span v-if="item.revoked" class="text-negative">Revoked</span>
              <span v-else-if="item.expired" class="text-warning">Expired</span>
              <span v-else>Active</span>
            </span>
          </div>
          <div v-if="!item.revoked && !item.expired && item.expires" class="row q-item-tile hash sublabel pt-1 pr-1 text-1">
            <span class="pr-1 opacity-60">Expires:</span>
            <span class=" ellipsis col">
              <formatted-date :date="item.expires"></formatted-date>
            </span>
          </div>
          <div class="mt-3 text-h6">Packages:</div>
          <div class="mxh-50vh overflow-x-auto">
            <div :key="'target_' + pkgName" class="mt-1 pt-1 pb-1 h-divide-top" v-for="(pkg, pkgName) in item.packages">
              <package-info :pkg="pkg" max-width="95%" show-hash show-labels></package-info>
            </div>
          </div>
        </div>

        <div class="col-12 col-xl-4">
          <div class="row">
            <div class="col-12 col-auto">
              <q-list separator>
                <q-item clickable v-ripple @click="viewSnippet">
                  <q-item-section avatar>
                    <q-icon color="secondary" name="usb" />
                  </q-item-section>
                  <q-item-section>Create Lockbox USB Image</q-item-section>
                </q-item>
                <q-item clickable v-ripple @click="createUpdate">
                  <q-item-section avatar>
                    <q-icon color="secondary" name="edit" />
                  </q-item-section>
                  <q-item-section>Modify</q-item-section>
                </q-item>
                <q-item v-if="!item.revoked" clickable v-ripple @click="revokeUpdate">
                  <q-item-section avatar>
                    <q-icon color="negative" name="not_interested" />
                  </q-item-section>
                  <q-item-section>Revoke</q-item-section>
                </q-item>
              </q-list>
            </div>
          </div>
        </div>
      </div>
    </q-card-section>
    <q-dialog ref="dialogRef" v-model="showSnippet">
      <q-card class="q-dialog-plugin p-1 mnw-80">
        <q-card-section>
          <div class="row">
            <div class="col-12 relative-position pl-1 text-center text-h6">
              <p>Run the following command to create Lockbox USB image:</p>
            </div>
            <div class="mb-1">
              <h6 class="m-0 p-0">Options</h6>
              <q-checkbox v-model="useEnvironmentVariableForCredentials">Use environment variable for <strong>credentials.zip</strong> path </q-checkbox>
              <q-input v-if="!useEnvironmentVariableForCredentials" filled dense class="ml-3" v-model="credentialsPath" label="Credentials.zip path"></q-input>
            </div>
            <text-copy class="w-100" :content="download_command_parsed"></text-copy>
          </div>
        </q-card-section>
        <q-card-actions align="right">
          <q-btn color="primary" label="Hide" flat v-close-popup />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </div>
</template>

<script>
import { mapActions, mapGetters } from 'vuex';
import { format } from 'quasar';
const { humanStorageSize } = format;
import Loader from '../loaders/Loader';
import Tooltip from '../common/Tooltip.vue';
import FormattedDate from '../common/FormattedDate.vue';
import PackageIcon from '../packages/PackageIcon.vue';
import TextCopy from '../common/TextCopy.vue';
import PackageInfo from '../packages/PackageInfo.vue';

export default {
  name: 'LockboxQuickView',
  components: {
    Loader,
    Tooltip,
    FormattedDate,
    PackageIcon,
    TextCopy,
    PackageInfo,
  },
  props: {
    parentHeight: {
      type: String,
      default: '',
    },
    update: {
      type: Object,
      default: () => ({}),
    },
    updateSize: {
      type: Number,
      default: 0,
    },
  },
  data() {
    return {
      showSnippet: false,
      loadingUpdatedData: true,
      loadingPackageData: false,
      currentUpdateName: '',
      download_command: 'torizoncore-builder platform lockbox --credentials $CREDENTIALS $LOCKBOX_NAME',
      useEnvironmentVariableForCredentials: true,
      credentialsPath: '~/credentials.zip',
    };
  },
  methods: {
    ...mapActions({
      fetchUpdate: 'updates/fetchUpdate',
      fetchUpdateDetail: 'updates/fetchUpdateDetail',
    }),

    loadData() {
      this.loadingUpdatedData = false;
      this.fetchUpdates({ updateName: this.updateName })
        .then((data) => {
          this.loadingUpdatedData = false;
        })
        .catch((err) => {
          logError('Lockbox fetch err:', err);
          this.loadingUpdatedData = false;
        });
    },
    setup() {
      this.$events.$on('updates:refresh', () => {
        this.loadData();
      });
    },
    showEditDialog() {
      this.$events.$emit(`dialogs:rename-device:open`, {
        show: true,
        device: this.device || {},
      });
    },
    viewSnippet() {
      this.showSnippet = true;
    },
    revokeUpdate() {
      this.$emit('on-revoke', { item: this.item });
    },
    createUpdate() {
      this.$emit('on-modify', { item: this.item });
    },
    hideDetail() {
      this.$emit('hide', {});
    },
    humanStorageSize,
  },
  mounted() {
    this.setup();
  },
  computed: {
    ...mapGetters({
      // devices: 'devices/devices',
      packages: 'packages/packages',
      packagesByHash: 'packages/packagesByHash',
    }),
    item() {
      return this.update;
    },
    updateName() {
      return this.update.name;
    },

    activeUuid() {
      return this.currentDeviceUuid || this.deviceUuid;
    },
    deviceDeleteInProgress: {
      get() {
        return this.$store.getters['ui/deviceDeleteInProgress'];
      },
      set(val) {
        this.$store.commit('ui/setDeviceDeleteInProgress', val);
      },
    },
    download_command_parsed() {
      const credentialsPath = this.useEnvironmentVariableForCredentials ? '"$CREDENTIALS"' : this.credentialsPath;
      return this.download_command.replace(/\$CREDENTIALS/g, credentialsPath).replace(/\$LOCKBOX_NAME/g, this.updateName);
    },
  },
  watch: {
    activeUuid(n) {
      this.$emit('device-uuid-change', { uuid: this.activeUuid });
    },
    updateName(n) {
      if (n) {
        this.currentUpdateName = this.updateName;
        this.setup();
      }
    },
  },
};
</script>
