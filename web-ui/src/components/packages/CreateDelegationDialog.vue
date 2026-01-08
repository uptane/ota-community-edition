<template>
  <q-dialog v-model="show" @hide="onHidden">
    <q-card class="p-1 w-80 mxw-40em">
      <q-card-section class="text-center">
        <h4 v-if="editMode" class="m-0 p-0 ml-2 mr-2">Modify {{ delegationData.name }}</h4>
        <h4 v-else-if="importMode" class="m-0 p-0 ml-2 mr-2">Imported Package Source Data</h4>
        <h4 v-else class="m-0 p-0 ml-2 mr-2">Add New Package Source</h4>
      </q-card-section>
      <q-card-section class="p-3 h-90vh mxh-40em overflow-y-auto">
        <q-form ref="delegationForm">
          <p class="m-0 p-0 mb-1">
            <q-input
              v-if="!editMode"
              v-model="delegationData.name"
              label="Source name"
              type="text"
              outlined
              placeholder="Enter package source name"
              :rules="[(val) => !!val || 'Delegation name is required', (val) => val.match(/^[a-zA-Z0-9-_]+$/) || 'Name can only be alphanumeric with hyphen (-) and underscore (_)', (val) => val.length < 255 || 'Name cannot be longer than 254 characters']"
            />
          </p>
          <p class="m-0 p-0 mb-1">
            <q-input v-model="delegationData.friendlyName" label="Friendly name (To display in the UI)" type="text" outlined placeholder="Enter package source friendly name" :rules="[(val) => (val || '').length < 80 || 'Friendly name cannot be longer than 80 characters']" />
          </p>
          <div class="m-0 p-0 mb-2">
            <p class="p-1">Signing keys</p>
            <div v-for="(key, ind) in delegationData.keys" :key="'delegation_key_' + ind" class="row h-divide-top-dotted q-pt-sm">
              <div class="col">
                <p class="mxw-90 ellipsis"><span class="faded">Key ID:&nbsp;</span> {{ key.keyid }}</p>
              </div>
              <div class="col-auto">
                <q-btn flat dense size="sm" color="primary" icon="edit" @click="editSigningKey(ind)"></q-btn>
                <q-btn flat dense size="sm" color="negative" icon="delete" @click="removeSigningKey(ind)"></q-btn>
              </div>
            </div>
            <div class="text-center h-divide-top-dotted">
              <q-btn flat color="primary" icon="add" @click="addSigningKey">&nbsp;Add signing key</q-btn>
            </div>
          </div>
          <p class="m-0 p-0 mb-1">
            <q-input v-model="delegationPath" label="Delegation path" type="text" outlined placeholder="Enter delegation path" :rules="[(val) => !!val || 'Source path is required']" />
          </p>
          <p class="m-0 p-0 mb-1">
            <q-input v-model="delegationData.threshold" label="Signature threshold" type="number" outlined placeholder="Enter signature threshold" :rules="[(val) => val > 0 || 'Signature threshold must be at least 1']" />
          </p>
          <div class="m-0 p-0 mb-1">
            <div class="q-gutter-sm">
              <q-radio v-for="(type, index) in delegationTypes" :key="'delegationType_' + index" v-model="delegationSourceType" :val="type" :label="type.label" @input="metadataFile = null" />
              <div class="mt-1 faded">{{ delegationSourceType.desc }}</div>
              <div>
                <q-file label="Metadata file" v-if="delegationSourceType.value == 'file' && !metadataFile" :rules="[(val) => (delegationSourceType.value == 'file' && !!val) || 'Metadata file is required']" @input="processMetadataFile" outlined v-model="metadataFile"></q-file>
                <q-input v-else-if="delegationSourceType.value == 'url'" v-model="delegationData.remoteUri" label="Metadata URL" type="url" outlined placeholder="Enter metadata URL" :rules="[(val) => (delegationSourceType.value == 'url' && !!val) || 'Metadata URL is required']" />
                <div v-else-if="delegationSourceType.value == 'paste' || metadataFile">
                  <q-input v-model="delegationData.metadata" label="Metadata content" type="textarea" outlined placeholder="Metadata content" :rules="[(val) => ((delegationSourceType.value == 'paste' || delegationSourceType.value == 'file') && !!val) || 'Metadata is required']" lazy-rules />
                  <q-btn flat dense color="primary" @click="metadataFile = null">Clear metadata content</q-btn>
                </div>
              </div>
            </div>
          </div>
        </q-form>
      </q-card-section>
      <q-card-section class="text-center">
        <div class="mt-0">
          <q-btn v-if="!editMode" @click="saveDelegation" color="primary" label="Add package source" />
          <q-btn v-else @click="saveDelegation" color="primary" label="Update package source" />
          <q-btn flat class=" ml-1" label="Cancel" v-close-popup />
        </div>
      </q-card-section>
    </q-card>
  </q-dialog>
</template>

<script>
import { QSpinnerHourglass } from 'quasar';
import { mapActions } from 'vuex';
import { calculateKeyId } from '../../utils/Common';
import AddDelegationKeyDialog from './AddDelegationKeyDialog.vue';
export default {
  name: 'CreateDelegationDialog',
  components: {},
  props: {
    value: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      editMode: false,
      importMode: false,
      delegationData: {
        name: '',
        friendlyName: '',
        keys: [],
        paths: [''],
        threshold: 1,
        metadata: '',
        type: 'file',
      },
      metadataFile: null,
      delegationSourceType: {},
      delegationTypes: [
        {
          value: 'file',
          label: 'Metadata file',
          desc: 'Select source metadata file',
        },
        {
          value: 'paste',
          label: 'Paste from clipboard',
          desc: 'Paste source metadata from clipboard',
        },
        {
          value: 'url',
          label: 'Metadata URL',
          desc: 'Enter source metadata URL',
        },
      ],
    };
  },
  computed: {
    show: {
      get() {
        return this.value;
      },
      set(val) {
        this.$emit('input', val);
      },
    },
    delegationPath: {
      get() {
        return (this.delegationData.paths || []).join(',');
      },
      set(val) {
        this.delegationData.paths = val.split(',').map((a) => a.trim());
      },
    },
  },
  methods: {
    ...mapActions({
      addDelegation: 'packages/addDelegation',
      updateDelegation: 'packages/updateDelegation',
    }),
    addSigningKey() {
      this.$q
        .dialog({
          component: AddDelegationKeyDialog,

          parent: this,
          persistent: false,
          keyContent: '',
          keyType: 'rsa',
          persistent: false,
          title: 'Add Signing Key',
        })
        .onOk(({ keyContent, keyType }) => {
          let keyid = '';
          let key = keyContent.replace(/\\n/g, '');
          try {
            keyid = calculateKeyId(key, keyType);
          } catch (error) {
            return this.$q.dialog({
              title: 'Unable To Parse Signing Key',
              message: 'The signing key you entered could not be parsed. Please check that the key you pasted is in X.509 SubjectPublicKeyInfo (SPKI) format.',
              progress: false,
              persistent: false,
              color: 'primary',
              cancel: false,
              ok: {
                flat: true,
                label: 'ok',
              },
            });
          }
          const found = this.delegationData.keys.find((a) => {
            return a.keyid === keyid;
          });
          if (!!found) {
            this.$q.dialog({
              title: 'Signing Key Was Already Included',
              message: 'The signing key you entered was already included in your entry. It will be processed with the rest of the delegation data when you submit the form.',
              progress: false,
              persistent: false,
              color: 'primary',
              cancel: false,
              ok: {
                flat: true,
                label: 'ok',
              },
            });
          } else {
            this.delegationData.keys.push({
              keyid,
              keytype: keyType,
              keyval: {
                public: key,
              },
            });
            this.$q.dialog({
              title: 'Signing Key Parsed Successfully',
              message: 'The signing key you entered was successfully parsed and included in your entry. It will be processed with the rest of the delegation data when you submit the form.',
              progress: false,
              persistent: false,
              color: 'primary',
              cancel: false,
              ok: {
                flat: true,
                label: 'ok',
              },
            });
          }
        });
    },
    removeSigningKey(index) {
      this.delegationData.keys.splice(index, 1);
    },
    editSigningKey(index) {
      const existingKey = this.delegationData.keys[index];
      this.$q
        .dialog({
          component: AddDelegationKeyDialog,

          parent: this,
          persistent: false,
          keyContent: (existingKey || { keyval: { public: '' }, keytype: 'rsa' }).keyval.public,
          keyType: (existingKey || { keyval: { public: '' }, keytype: 'rsa' }).keytype,
          title: 'Edit Signing Key',
        })
        .onOk(({ keyContent, keyType }) => {
          const keyid = calculateKeyId(keyContent, keyType);
          this.delegationData.keys.splice(index, 1, {
            keyid,
            keytype: keyType,
            keyval: {
              public: keyContent,
            },
          });
          this.$q.dialog({
            title: 'Signing Key Parsed',
            message: 'The changes to the signing key was successfully parsed and included in your entry. It will be processed with the rest of the delegation data when you submit the form. ',
            progress: false,
            persistent: false,
            color: 'primary',
            cancel: false,
            ok: {
              flat: false,
              label: 'ok',
            },
          });
        });
    },
    processMetadataFile(file) {
      let reader = new FileReader();

      // Closure to capture the file information.
      reader.onload = (e) => {
        this.$set(this.delegationData, 'metadata', e.target.result);
      };

      // Read in the image file as a text.
      reader.readAsText(file);
    },
    saveDelegation() {
      this.$refs.delegationForm
        .validate()
        .then((valid) => {
          if (valid) {
            // Add delegation
            if (!this.editMode && this.delegationData.keys.length < 1) {
              return this.$q.dialog({
                title: 'Invalid Entry',
                message: 'You must add at least one sigining key.',
                progress: false,
                persistent: false,
                ok: {
                  label: 'ok',
                  color: 'primary',
                  flat: true,
                },
                cancel: false,
              });
            }
            this.delegationData.type = this.delegationSourceType.value;
            let loaderData = {
              title: 'Adding ' + this.delegationData.name + '...',
              message: 'Please wait while we complete this action.',
              progress: {
                spinner: QSpinnerHourglass,
                color: 'primary',
              },
              persistent: true,
              ok: false,
              cancel: false,
            };
            let doneData = {
              title: this.delegationData.name + (this.editMode ? ' updated!' : ' added!'),
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
            let dialog = this.$q.dialog(loaderData);
            let promise;
            if (this.editMode) {
              promise = this.updateDelegation(this.delegationData);
            } else {
              promise = this.addDelegation(this.delegationData);
            }

            promise
              .then(() => {
                dialog.update(doneData);
                dialog.onDismiss(() => {
                  this.show = false;
                  this.$emit('created', this.delegationData);
                });
              })
              .catch((err) => {
                let data = {
                  title: 'Package Source ' + (this.editMode ? 'Update ' : 'Creation ') + ' Failed',
                  message: err.message || 'Unable to add delegation, please try again.',
                  progress: false,
                  persistent: false,
                  ok: {
                    label: 'ok',
                    color: 'primary',
                    flat: true,
                  },
                  cancel: false,
                };
                if (err && err.response && err.response.data && err.response.data.code === 'role_key_not_found') {
                  data.message = 'There are no signing keys available online to complete this operation. If you have taken your signing keys offline, this operation is no longer possible through the web UI. Please use offline signing tools (TorizonCore Builder and/or uptane-sign) instead.';
                }

                dialog.update(data);
                if (err.partiallyCreated) {
                  this.$emit('patially-created', this.delegationData);
                  dialog.onDismiss(() => {
                    this.show = false;
                  });
                }
              });
            // console.log('Cancel')
          }
        })
        .catch((e) => {
          this.$q.dialog({
            title: 'Invalid Entry',
            message: e.message,
            progress: false,
            persistent: false,
            ok: {
              label: 'ok',
              color: 'primary',
              flat: true,
            },
            cancel: false,
          });
        });
    },
    setDelegationData(delegation) {
      if (delegation) {
        this.editMode = true;
        this.delegationData = { ...delegation, keys: delegation.keys || [] };
        this.delegationSourceType = this.delegationTypes.find((a) => a.value === (delegation.type || 'url'));
      } else {
        this.editMode = false;
        this.delegationData = { threshold: 1, keys: [], paths: [] };
      }
    },
    setImportedDelegationData(delegation) {
      this.importMode = true;
      this.editMode = false;
      this.delegationData = {
        ...delegation.delegationMetadata,
        keys: delegation.keys || [],
        remoteUri: delegation.fetchUrl.uri,
      };
      this.delegationSourceType = this.delegationTypes.find((a) => a.value === 'url');
    },
    onHidden() {
      this.delegationData = {
        name: '',
        friendlyName: '',
        keys: [],
        paths: [''],
        threshold: 1,
        metadata: '',
        type: 'file',
      };
      this.delegationSourceType = this.delegationTypes.find((a) => a.value === 'file');
      this.importMode = false;
      (this.$refs.delegationForm || { resetValidation: () => {} }).resetValidation();
    },
  },
  created() {},
  mounted() {
    this.delegationSourceType = this.delegationTypes[0];
  },
};
</script>

<style></style>
