<template>
  <div class="row p-1">
    <div class="col-12">
      <div class="q-card p-2 row">
        <h4 class="m-0 col">API Clients</h4>
        <q-btn flat color="primary" @click="beginCreateOrEdit()"> <q-icon class="pr" name="add"></q-icon>&nbsp;Create API Client </q-btn>
      </div>
      <div class="row q-card ">
        <div
          :class="{
            'col-12': !selectedClient || loadingClients,
            'col-6': !!selectedClient && !loadingClients,
          }"
          class="v-divide-right"
        >
          <q-table
            :data="apiClients"
            :columns="columns"
            row-key="name"
            selection="single"
            :pagination.sync="pagination"
            @row-click="
              (evt, row, index) => {
                rowClicked(row);
              }
            "
          >
            <template v-slot:body-selection> </template>
            <template v-slot:body-cell-name="props">
              <q-td :props="props" :class="{ selected: selectedClient && selectedClient.client_id === props.row.client_id }">
                {{ props.row.name || '--' }}
                <div class=" mxw-20em ellipsis-2-lines">
                  <small class="opacity-60" v-if="props.row.description">
                    {{ props.row.description }}
                  </small>
                </div>
              </q-td>
            </template>
            <template v-slot:body-cell-client_id="props">
              <q-td :props="props" :class="{ selected: selectedClient && selectedClient.client_id === props.row.client_id }">
                <div class="">
                  <span>{{ props.row.client_id }}</span>
                </div>
              </q-td>
            </template>
            <template v-slot:body-cell-type="props">
              <q-td :props="props" :class="{ selected: selectedClient && selectedClient.client_id === props.row.client_id }">
                <div class="">
                  <span>{{ props.row.type }}</span>
                </div>
              </q-td>
            </template>
            <template v-slot:body-cell-start="props">
              <q-td :props="props" :class="{ selected: selectedClient && selectedClient.client_id === props.row.client_id }">
                <div class="">
                  <formatted-date :date="props.row.start"></formatted-date>
                </div>
              </q-td>
            </template>
            <template v-slot:body-cell-access_token_lifetime_seconds="props">
              <q-td :props="props" :class="{ selected: selectedClient && selectedClient.client_id === props.row.client_id }">
                <div class="">
                  {{ props.row.access_token_lifetime_seconds }}
                </div>
              </q-td>
            </template>
            <template v-slot:body-cell-last_access="props">
              <q-td :props="props" :class="{ selected: selectedClient && selectedClient.client_id === props.row.client_id }">
                <div class="">
                  <formatted-date :date="props.row.last_access"></formatted-date>
                </div>
              </q-td>
            </template>
            <template v-slot:body-cell-actions="props">
              <q-td :props="props">
                <div class="">
                  <q-btn flat dense @click.stop="beginCreateOrEdit(props.row)" color="primary"> <q-icon name="edit" class="mr-1" />Edit </q-btn>
                  <q-btn flat dense @click="deleteClient(props.row, $event)" color="negative"> <q-icon name="delete" class="mr-1" />Delete </q-btn>
                </div>
              </q-td>
            </template>
          </q-table>
        </div>
        <transition appear enter-active-class="animated slideInRight" leave-active-class=" " class="mnh-100vh">
          <div
            class="col-6 pt-0 mb-1"
            v-if="!loadingClients && !!showClientDetails"
            :class="{
              'col-12': $q.screen.lt.md,
            }"
          >
            <div
              class="h-100  pl-1
          animated"
              style="overflow-y: auto;"
              :class="{
                pulse: giveAttensionToDetailView,
              }"
            >
              <div>
                <div class="p-1 row">
                  <div class="col">
                    {{ selectedClient.name }}
                  </div>
                  <div class="col-auto">
                    <q-btn flat dense icon="close" @click="selectedClient = null"></q-btn>
                  </div>
                </div>
                <div class="q-item">
                  <div class="label ">Client ID</div>
                  <div class="sublabel opacity-50">{{ selectedClient.client_id }}</div>
                </div>
                <div class="q-item" v-if="false">
                  <div class="label ">Client Secret</div>
                  <div class="sublabel opacity-50">
                    <masked-text v-if="selectedClient.secret" mask="*" :text="selectedClient.secret"></masked-text>
                    <span v-else>--</span>
                  </div>
                </div>
                <div class="q-item">
                  <div class="label ">Description</div>
                  <div class="sublabel opacity-50 ">{{ selectedClient.description || 'None' }}</div>
                </div>
                <div class="q-item">
                  <div class="label ">Client Type</div>
                  <div class="sublabel opacity-50 ">{{ selectedClient.type }}</div>
                </div>
                <div class="q-item" v-if="selectedClient.type === 'provision'">
                  <div class="label ">Default Provisioning Client</div>
                  <div class="" v-if="selectedClient.default"><q-icon color="positive" name="check_circle"></q-icon> <span class="sublabel opacity-50 ">&nbsp;Yes</span></div>
                  <div class="" v-else>
                    <div class="sublabel opacity-50 ">No</div>
                  </div>
                </div>
                <div class="h-divide-top mt-1 pt-1 row w-100">
                  <div class="col-auto">
                    <q-btn v-if="selectedClient.type === 'provision' && !selectedClient.default" flat dense size="md" class="mr-1" color="secondary" icon="check_circle_outline" :loading="settingClientAsDefault" @click="setDefaultProvisioningClient(selectedClient)">
                      <template v-slot:loading>
                        <div class="mnw-10em"><q-spinner-hourglass class="on-left" /> Processing...</div>
                      </template>
                      &nbsp;Set as default
                    </q-btn>
                  </div>
                  <q-btn flat dense class="col-auto" @click="copyClient(selectedClient)" :color="copiedToClipboard ? 'positive' : 'secondary'" icon="content_copy" :loading="copiedToClipboard">
                    <template v-slot:loading>
                      <div class="mnw-10em"><q-icon name="check_circle" class="on-left" /> Copied!</div>
                    </template>
                    &nbsp;Copy to clipboard
                    <tooltip v-if="!copiedToClipboard">
                      Click here to copy this client credentials to clipboard in base64 encoded format
                    </tooltip>
                  </q-btn>
                  <q-btn flat dense class="col-auto" @click="beginCreateOrEdit(selectedClient)" color="primary"> <q-icon name="edit" class="mr-0" />&nbsp;Edit </q-btn>

                  <q-btn flat dense class="col-auto" @click="deleteClient(selectedClient, $event)" color="negative"> <q-icon name="delete" class="mr-0" />&nbsp;Delete </q-btn>
                </div>
              </div>
            </div>
          </div>
        </transition>
      </div>
    </div>
    <q-dialog v-model="deletingClient">
      <q-card class="mnw-30em">
        <q-card-section>
          <div class="text-h6 text-center"><q-spinner-hourglass color="secondary" /> Deleting client</div>
        </q-card-section>
      </q-card>
    </q-dialog>
    <q-dialog v-model="showCreateDialog">
      <q-card class="mnw-30em" v-if="created">
        <q-card-section>
          <div class="text-h5">API Client {{ isNewClient ? 'Created' : 'Saved' }} Successfully</div>
        </q-card-section>
        <q-separator></q-separator>
        <q-card-section v-if="isNewClient">
          <div class="prompt-for-secret-copy only-show-once faded mb-2 text-center">
            Please copy the client secret and store it in a safe place. It will not be shown again after this dialog is closed.
          </div>
          <div>
            <div class="text-1">Client ID</div>
            <div class="text-1 opacity-50">{{ created.client_id }}</div>
            <div class="text-1">Client Secret</div>
            <div class="pb-1">
              <text-copy :prompt-position="'bottom'" :content="created.secret"></text-copy>
            </div>
          </div>
        </q-card-section>
        <q-card-section>
          <div class="text-center">
            <q-btn flat v-close-popup color="primary">
              Close
            </q-btn>
          </div>
        </q-card-section>
      </q-card>
      <q-card class="mnw-30em" v-else>
        <q-form @submit="isNewClient ? createClient() : updateClient()">
          <q-card-section>
            <div class="text-h5">{{ isNewClient ? 'Create' : 'Edit' }} API Client</div>
          </q-card-section>
          <q-separator></q-separator>
          <q-card-section>
            <div class="row p-1">
              <p class="col-6 text-1 pr-1">
                <span>Client Name:</span>
                <q-input outlined v-model="newClient.name" dense placeholder="Enter client name" class="" :rules="[(val) => !!val || 'Client name is required']"></q-input>
              </p>
              <p class="col-6 text-1 ">
                <span
                  >Client Type:
                  <q-icon name="help">
                    <tooltip>
                      This will be used to determine the access level for this client.
                      <ul class="m-0 p-0 ml-1">
                        <li :key="clientType.value" v-for="clientType in clientTypes">
                          <strong>{{ clientType.label }}: </strong>{{ clientType.hint }}
                        </li>
                      </ul>
                    </tooltip>
                  </q-icon></span
                >
                <q-select outlined dense :options="clientTypes" emit-value map-options :disable="!isNewClient" v-model="newClient.type" placeholder="Select client type" class="pb-1"></q-select>
              </p>
              <p class="col-6 text-1 pr-1">
                <span>Description (Optional):</span>
                <q-input outlined dense v-model="newClient.description" placeholder="Description" class="pb-1"></q-input>
              </p>
              <div class="col-6 text-1">
                <span>Token Lifetime: </span>
                <div class="row">
                  <div class="col">
                    <q-input outlined type="number" dense v-model="tokenLifeTime" placeholder="This is optional" class="pb-1" :max="Math.round(31536000 / timeUnit.value)" :min="0">
                      <!-- <template v-slot:append>
                    
                  </template> -->
                    </q-input>
                  </div>
                  <div class="col-auto">
                    <q-select dense outlined :options="timeUnits" v-model="timeUnit" placeholder="Select time unit" class="pl-0"></q-select>
                  </div>
                  <small class=" col-12 q-field__messages text-caption opacity-50" style="margin-top:-0.5rem"> Maximum lifetime is {{ Math.round(31536000 / timeUnit.value) }} {{ timeUnit.label }} </small>
                </div>
              </div>
            </div>
          </q-card-section>
          <q-card-section>
            <div class="text-center">
              <q-btn flat v-close-popup color="default">
                Cancel
              </q-btn>
              <q-btn color="primary" type="submit"> {{ isNewClient ? 'Create' : 'Save' }} Client </q-btn>
            </div>
          </q-card-section>
        </q-form>
      </q-card>
    </q-dialog>
  </div>
</template>

<script>
import { mapActions, mapGetters } from 'vuex';
import FormattedDate from '../common/FormattedDate.vue';
import MaskedText from '../common/masked-text.vue';
import TextCopy from '../common/TextCopy.vue';
import Tooltip from '../common/Tooltip.vue';
import { copyToClipboard } from 'quasar';

export default {
  components: { FormattedDate, Tooltip, MaskedText, TextCopy },
  name: 'ApiClientManagerPanel',
  data() {
    return {
      copiedToClipboard: false,
      pagination: {
        sortBy: 'name',
        descending: false,
        page: 1,
        rowsPerPage: 20,
        // rowsNumber: xx if getting data from a server
      },
      columns: [
        {
          name: 'name',
          required: true,
          label: 'Name',
          align: 'left',
          field: (row) => row.name,
          format: (val) => `${val}`,
          sortable: true,
        },
        { name: 'client_id', label: 'Client ID', field: 'client_id', align: 'left' },
        { name: 'type', label: 'Access Type', field: 'type', align: 'left' },
        // { name: 'start', label: 'Date Created', field: 'start' },
        // { name: 'expiration-date', label: 'Expiration Date', field: 'expiry', sortable: true, sort: (a, b) => parseInt(a, 10) - parseInt(b, 10) },
        // { name: 'last_access', label: 'Last Used', field: 'last_access', sortable: true, sort: (a, b) => parseInt(a, 10) - parseInt(b, 10) },
        { name: 'actions', label: 'Actions', field: 'actions', align: 'left' },
      ],
      showCreateDialog: false,
      newClient: {
        type: 'api-minimal',
      },
      clientTypes: [
        { value: 'provision', label: 'Provision', hint: 'Generates token that can only be used to provision devices. The default version is downloaded in credentials.zip' },
        { value: 'api-minimal', label: 'API Minimal', hint: 'Generates token that supports the core features of OTA, modifying devices, creating fleets, pushing updates' },
        { value: 'api-v2', label: 'API V2 (beta)', hint: 'Generates a token that allows access to the new (beta) Rest API. Available under api/v2beta/* uris' },
      ],
      deletingClient: false,
      creatingClient: false,
      selectedClient: null,
      loadingClients: false,
      settingClientAsDefault: false,
      giveAttensionToDetailView: false,
      selectedArray: [],
      masked: true,
      created: false,
      timeUnit: { label: 'weeks', value: 604800 },
      timeUnits: [{ label: 'seconds', value: 1 }, { label: 'minutes', value: 60 }, { label: 'hours', value: 3600 }, { label: 'days', value: 86400 }, { label: 'weeks', value: 604800 }, { label: 'months', value: 2592000 }, { label: 'years', value: 31536000 }],
    };
  },
  methods: {
    ...mapActions({
      createApiClient: 'users/createApiClient',
      revokeApiClient: 'users/revokeApiClient',
      deleteApiClient: 'users/deleteApiClient',
      getApiClients: 'users/getApiClients',
      setDefaultApiClient: 'users/setDefaultApiClient',
      updateApiClient: 'users/updateApiClient',
    }),
    beginCreateOrEdit(client) {
      this.showCreateDialog = true;
      this.created = null;
      this.deletingClient = false;
      if (client) {
        this.newClient = { ...client };
      } else {
        this.newClient = { type: 'api-minimal' };
        this.created = null;
        this.deletingClient = false;
      }
    },
    deleteClient(clientData, event) {
      event.stopPropagation();
      this.$q
        .dialog({
          title: 'Delete Client',
          message: 'Are you sure you want to delete this API client?',
          cancel: {
            label: 'No',
            color: 'default',
            flat: true,
          },
          ok: {
            label: 'Yes',
            color: 'primary',
            flat: false,
          },
        })
        .onOk(() => {
          this.deletingClient = true;
          this.deleteApiClient(clientData)
            .then((result) => {
              this.$q.dialog({
                title: 'Client Deleted',
                message: 'Client was successfully deleted.',
                ok: {
                  label: 'OK',
                  color: 'primary',
                  flat: true,
                },
              });
            })
            .catch((err) => {
              this.$q.dialog({
                title: 'Error',
                message: 'Client was not deleted, please try again.',
                ok: {
                  label: 'OK',
                  color: 'primary',
                  flat: true,
                },
              });
            })
            .finally(() => {
              this.deletingClient = false;
            });
        });
    },

    createClient() {
      this.creatingClient = true;
      this.createApiClient(this.newClient)
        .then((result) => {
          this.created = result;
        })
        .catch((err) => {
          console.log(err);
          this.$q.dialog({
            title: 'Error',
            message: 'Client was not created, please try again.',
            ok: {
              label: 'OK',
              color: 'primary',
              flat: false,
            },
          });
        })
        .finally(() => {
          this.creatingClient = false;
        });
    },

    rowClicked(row) {
      if (this.selectedClient && this.selectedClient.client_id === row.client_id) {
        this.selectedClient = null;
      } else {
        this.selectedClient = { ...row };
      }
    },
    copyClient(client) {
      this.copiedToClipboard = true;
      copyToClipboard(btoa(JSON.stringify(client)));
      setTimeout(() => {
        this.copiedToClipboard = false;
      }, 3000);
    },
    updateClient() {
      this.creatingClient = true;
      this.updateApiClient(this.newClient)
        .then((result) => {
          this.$q.dialog({
            title: 'Client Changes Saved',
            message: `Changes to ${this.newClient.name} was successfully saved.`,
            ok: {
              label: 'OK',
              color: 'primary',
              flat: true,
            },
          });
          this.showCreateDialog = false;
          this.selectedClient = null;
          this.fetchClients();
        })
        .catch((err) => {
          this.$q.dialog({
            title: 'Error',
            message: 'Client was saved, please try again.',
            ok: {
              label: 'OK',
              color: 'primary',
              flat: true,
            },
          });
        })
        .finally(() => {
          this.creatingClient = false;
        });
    },
    setDefaultProvisioningClient(client) {
      this.settingClientAsDefault = true;
      this.setDefaultApiClient(client)
        .then((result) => {
          this.$q.dialog({
            title: 'Default Client Set',
            message: `${client.name} was successfully set as default.`,
            ok: {
              label: 'OK',
              color: 'primary',
              flat: true,
            },
          });
          client.default = true;
          this.fetchClients();
        })
        .catch((err) => {
          this.$q.dialog({
            title: 'Error',
            message: 'Default client was not set, please try again.',
            ok: {
              label: 'OK',
              color: 'primary',
              flat: true,
            },
          });
        })
        .finally(() => {
          this.settingClientAsDefault = false;
        });
    },
    fetchClients() {
      this.loadingClients = true;
      this.getApiClients()
        .then((result) => {
          this.loadingClients = false;
        })
        .catch((err) => {
          this.loadingClients = false;
        })
        .finally(() => {
          this.loadingClients = false;
        });
    },
    mask(text) {
      return text.replace(/./g, '#');
    },
  },
  computed: {
    ...mapGetters({
      apiClients: 'users/apiClients',
    }),
    showClientDetails() {
      return this.selectedClient && this.selectedClient.client_id;
    },
    isNewClient() {
      return !this.newClient.id;
    },
    tokenLifeTime: {
      get() {
        return this.newClient.access_token_lifetime_seconds / this.timeUnit.value;
      },
      set(v) {
        this.newClient.access_token_lifetime_seconds = v * this.timeUnit.value;
      },
    },
  },
  watch: {
    selectedClient(n) {
      if (!n) {
        this.masked = true;
      }
    },
    // lifetimeInSeconds (n) {
    //   this.newClient.access_token_lifetime_seconds = this.lifetimeInSeconds;
    // }
  },
  mounted() {
    this.fetchClients();
  },
};
</script>

<style></style>
