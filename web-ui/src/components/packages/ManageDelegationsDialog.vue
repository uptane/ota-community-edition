<template>
  <q-dialog v-model="show">
    <q-card class="p-1 w-80 mxw-70em">
      <q-card-section class="">
        <div class="text-h4 m-0 p-0 ml-2 mr-2 mb-1 text-center">Manage Package Sources</div>
        <div>
          <div v-if="reorderInProgress">
            <div v-if="savingChanges"><q-spinner-hourglass color="primary" class="ml-2" size="2em" /> Saving changes</div>
            <div v-else>
              <q-btn v-if="orderChanged" flat color="primary" icon="save" @click="finishSortable">&nbsp;Save current order</q-btn>
              <q-btn flat icon="restore" @click="cancelSortable">&nbsp;Cancel {{ orderChanged ? `and revert to previous order` : 're-order' }}</q-btn>
            </div>
          </div>
          <div v-else>
            <q-btn flat color="primary" icon="height" @click="setSortable">Re-order source priority</q-btn>
            <q-btn flat color="primary" icon="add" @click="importDelegation">Add new package source</q-btn>
          </div>
        </div>
      </q-card-section>
      <q-card-section class="p-1 h-90vh mxh-40em overflow-y-auto">
        <q-list bordered>
          <q-item class="h-divide-top opacity-40">
            <q-item-section v-if="reorderInProgress" avatar>
              <q-icon class="handle q-mr-sm faded cursor-drag" name="height"></q-icon>
            </q-item-section>

            <q-item-section>Source Name</q-item-section>
            <q-item-section>Source Paths</q-item-section>
            <q-item-section class="text-right">Actions</q-item-section>
          </q-item>
          <q-item class="h-divide-top">
            <q-item-section v-if="reorderInProgress" avatar>
              <q-icon class=" q-mr-sm cursor-not-allowed opacity-20" name="drag_handle"></q-icon>
            </q-item-section>

            <q-item-section>{{ customDelegation.label }}</q-item-section>
            <q-item-section>--</q-item-section>
            <q-item-section class="text-right">
              <span class="pr-3"> -- </span>
            </q-item-section>
          </q-item>
          <draggable :list="delegations" handle=".handle" class="" direction="vertical" :sort="true" ghost-class="opacity-30" chosen-class="text-secondary" drag-class="text-primary">
            <q-item v-for="(source, index) in delegations" :key="'delegation_item_' + index" class="h-divide-top">
              <q-item-section v-if="reorderInProgress" avatar>
                <q-icon class="handle q-mr-sm faded cursor-drag" name="drag_handle">
                  <tooltip>Drag each item by this handle to re-order them</tooltip>
                </q-icon>
              </q-item-section>

              <q-item-section>{{ source.label }}</q-item-section>
              <q-item-section>{{ (source.paths || ['--']).join(',') }}</q-item-section>
              <q-item-section class="text-right">
                <div class="">
                  <q-btn flat dense icon="edit" color="primary" @click="beginEdit(source)">
                    <tooltip>Modify package source</tooltip>
                  </q-btn>
                  <q-btn flat dense icon="delete" color="negative" @click="confirmDelete(source)">
                    <tooltip>Delete package source</tooltip>
                  </q-btn>
                </div>
              </q-item-section>
            </q-item>
          </draggable>
        </q-list>
      </q-card-section>

      <q-card-section>
        <div class="text-center">
          <q-btn v-close-popup icon="close"> Close</q-btn>
        </div>
      </q-card-section>
    </q-card>

    <create-delegation-dialog ref="createDelegationDialog" v-model="showCreateDelegationDialog" @created="prepareDelegations" @partially-created="prepareDelegations"></create-delegation-dialog>
  </q-dialog>
</template>

<script>
import { mapActions, mapGetters } from 'vuex';
import Tooltip from '../common/Tooltip.vue';
import CreateDelegationDialog from './CreateDelegationDialog.vue';
import draggable from 'vuedraggable';
import { QSpinnerHourglass } from 'quasar';
import { calculateKeyId } from '../../utils/Common';
import ImportDelegationPrompt from './ImportDelegationPrompt.vue';

export default {
  name: 'ManageDelegationDialog',
  components: { Tooltip, CreateDelegationDialog, draggable },
  props: {
    value: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      delegations: [],
      trustedKeys: [],
      selectedDelegation: null,
      customDelegation: {},
      reorderInProgress: false,
      savingChanges: false,
      initialPagination: {
        sortBy: 'desc',
        descending: false,
        page: 0,
        rowsPerPage: 10,
      },

      showCreateDelegationDialog: false,
      columns: [
        { name: 'label', align: 'left', label: 'Package Source Name', field: 'label', sortable: false },
        { name: 'paths', align: 'left', label: 'Paths', field: 'paths', sortable: false },
        { name: 'threshold', align: 'center', label: 'Signature Threshold', field: 'threshold', sortable: false },
        { name: 'actions', align: 'right', label: 'Actions', field: 'actions', sortable: false },
      ],
    };
  },
  computed: {
    ...mapGetters({
      packageSourceOptions: 'packages/packageSourceOptions',
      trustedDelegations: 'packages/trustedDelegations',
    }),
    show: {
      get() {
        return this.value;
      },
      set(val) {
        this.$emit('input', val);
      },
    },
    orderChanged() {
      return !_.isEqual(this.delegations, this.originalDelegations);
    },
  },
  methods: {
    ...mapActions({
      addDelegation: 'packages/addDelegation',
      deleteDelegation: 'packages/deleteDelegation',
      getTrustedDelegations: 'packages/getTrustedDelegations',
      getTrustedKeys: 'packages/getTrustedKeys',
      getDelegationMetadata: 'packages/getDelegationMetadata',
      saveDelegations: 'packages/saveDelegations',
    }),
    processMetadataFile(file) {
      this.delegationData.metadata = file;
    },
    addNewDelegation() {
      // this.$refs.delegationForm.validate(valid => {
      // if (valid) {
      // Add delegation
      this.delegationData.type = this.delegationSourceType.value;
      this.addDelegation(this.delegationData);
      // }
      // })
    },
    createDelegation() {
      this.$refs.createDelegationDialog.setDelegationData(null);
      this.showCreateDelegationDialog = true;
    },
    async beginEdit(delegation) {
      const loaderDlg = this.$q.dialog({
        title: 'Preparing source for editing',
        message: 'Please wait prepare this package source for editing.',
        progress: {
          spinner: QSpinnerHourglass,
          color: 'primary',
        },
        persistent: false,
        ok: false,
        cancel: false,
      });
      const trusted = { ...this.trustedDelegations.find((a) => a.name === delegation.name), ...delegation };
      trusted.friendlyName = delegation.friendlyName || delegation.label;
      const metadata = '';
      try {
        metadata = await this.getDelegationMetadata(delegation.name);
      } catch (e) {}
      if (delegation.remoteUri) {
        delegation.type = 'url';
      } else {
        delegation.type = 'paste';
        delegation.metadata = metadata;
      }
      trusted.keys = trusted.keyids.map((k) => {
        let key = this.trustedKeys.find((m) => m.keyid === k);
        return key || {};
      });
      loaderDlg.hide();
      this.$refs.createDelegationDialog.setDelegationData(trusted);
      this.showCreateDelegationDialog = true;
    },
    confirmDelete(source) {
      let loaderData = {
        title: 'Deleting ' + source.name + '...',
        message: 'Please wait while we complete this action.',
        progress: {
          spinner: QSpinnerHourglass,
          color: 'primary',
        },
        persistent: true,
        ok: false,
        cancel: false,
      };
      let deletedData = {
        title: source.name + ' deleted!',
        message: 'This process is now complete',
        progress: false,
        persistent: false,
        ok: {
          label: 'ok',
          color: 'primary',
          flat: true,
        },
        cancel: false,
      };
      let errorData = {
        title: 'Package Source Deletion Failed',
        message: 'Unable to complete the process. Please try again later.',
        progress: false,
        persistent: false,
        ok: {
          label: 'ok',
          color: 'primary',
          flat: true,
        },
        cancel: false,
      };
      let confirmData = {
        title: 'Delete ' + source.name + '?',
        message: 'This action cannot be undone',
        ok: {
          label: 'ok',
          color: 'primary',
          flat: true,
        },
        cancel: {
          label: 'Cancel',
          color: this.$q.dark.isActive ? 'light' : 'dark',
          flat: true,
        },
      };
      let dialog = this.$q
        .dialog(confirmData)
        .onOk(() => {
          dialog = this.$q.dialog(loaderData);
          this.deleteDelegation(source)
            .then(() => {
              dialog.update(deletedData);
            })
            .catch((err) => {
              if (err && err.response && err.response.data && err.response.data.code === 'role_key_not_found') {
                errorData.message = 'There are no signing keys available online to complete this operation. If you have taken your signing keys offline, this operation is no longer possible through the web UI. Please use offline signing tools (TorizonCore Builder and/or uptane-sign) instead.';
              }
              dialog.update(errorData);
            });
        })
        .onCancel(() => {})
        .onDismiss(() => {});
    },
    setSortable() {
      this.reorderInProgress = true;
    },
    finishSortable() {
      this.savingChanges = true;
      let delegationsToSave = this.delegations.map((a) => this.trustedDelegations.find((b) => b.name === a.name));
      this.saveDelegations(delegationsToSave)
        .then(() => {
          this.prepareDelegations();
          this.reorderInProgress = false;
        })
        .catch((err) => {})
        .finally(() => {
          this.savingChanges = false;
        });
    },
    cancelSortable() {
      this.prepareDelegations();
      this.reorderInProgress = false;
    },
    prepareDelegations() {
      this.delegations = this.packageSourceOptions.slice().filter((a) => a.name !== 'custom' && a.buildType !== 'custom');
      this.originalDelegations = this.delegations.slice();
      this.customDelegation = this.packageSourceOptions.find((d) => d.name === 'custom' && d.buildType === 'custom');
    },
    importDelegation() {
      this.$q
        .dialog({
          component: ImportDelegationPrompt,
          parent: this,
          title: 'Add new package source',
          color: 'primary',
          prompt: {
            model: '',
            type: 'url',
            isValid: (val) => /^https?:\/\/(?:www\.)?[-a-zA-Z0-9@:%._\+~#=]{1,256}\.[a-zA-Z0-9()]{1,6}\b(?:[-a-zA-Z0-9()@:%_\+.~#?&\/=]*)$/.test(val),
          },
          cancel: {
            color: this.$q.dark.isActive ? 'light' : 'dark',
            flat: true,
          },
          ok: {
            flat: true,
            label: 'Continue',
          },
          manualRequestLink: {
            action: () => {
              this.createDelegation();
            },
          },
          persistent: false,
        })
        .onOk((url) => {
          this.$axios
            .get(url, {
              bypassAuthIntercept: true,
            })
            .then((resp) => {
              resp.data.keys = resp.data.keys.map((key) => {
                return { ...key, keyid: calculateKeyId(key.keyval.public, key.keytype), keytype: key.keytype };
              });
              this.processImportedDelegation(resp.data);
            })
            .catch((err) => {
              let message = err.message;
              this.$q.dialog({
                title: 'Unable To Fetch Package Source Data',
                message: 'We are unable to fetch package source data from this URL: ' + (message === 'Network Error' ? 'Please make sure that CORS is enabled on the server and torizon.io is allowed.' : message),
                color: 'primary',
                cancel: {
                  flat: true,
                  label: 'close',
                  color: 'primary',
                },
                ok: false,
                persistent: false,
              });
            });
        })
        .onCancel(() => {})
        .onDismiss(() => {});
    },
    processImportedDelegation(delegation) {
      this.$refs.createDelegationDialog.setImportedDelegationData(delegation);
      let errors = [];
      if (delegation.delegationMetadata.name !== delegation.fetchUrl.delegationName) {
        errors.push({ message: 'Package source metadata name does not match what is defined in fetchUrl segement' });
      }
      if (delegation.delegationMetadata.paths.some((a) => !_.startsWith(a.name, 'extern-'))) {
        errors.push({ message: 'Package source paths must start with `extern-`' });
      }
      this.showCreateDelegationDialog = true;
    },
  },
  created() {},
  mounted() {
    this.getTrustedDelegations();
    this.getTrustedKeys().then((keys) => {
      this.trustedKeys = keys;
    });

    this.prepareDelegations();
  },
  watch: {
    packageSourceOptions() {
      this.prepareDelegations();
    },
  },
};
</script>

<style></style>
