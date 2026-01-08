<template>
  <div class="">
    <div class="col-12">
      <div class="row">
        <div class="col">
          <h4 class="m-0 col mb-2">IP Addresses (Accept List)</h4>
          <p>You can add up to 5 IP addresses to the accept list.</p>
        </div>
        <!-- Add IP button -->
        <div v-if="tableData.length < 5" class="col-auto">
          <q-btn color="primary" label="Add IP" @click="showAddIpDialog = true" class="q-mb-md q-mr-md" />
        </div>
      </div>
    </div>
    <div class="col-12 ">
      <!-- <p class="mxw-30em"><filter-input :placeholder="'Filter active devices'" /></p> -->
      <q-table :data="tableData" :columns="columns" row-key="name" class="shadow-0" :loading="loading">
        <template v-slot:body-cell-host_name="props">
          <q-td :props="props">
            <div class="col text-bold">{{ props.row.ip_address }}</div>
          </q-td>
        </template>
        <template v-slot:body-cell-actions="props">
          <q-td :props="props">
            <q-btn label="Delete" outline color="primary" @click="removeIp(props.row)">
              <q-tooltip>Remove IP address</q-tooltip>
            </q-btn>
          </q-td>
        </template>
      </q-table>
    </div>
    <q-dialog v-model="showAddIpDialog" persistent @hide="newIp = ''">
      <q-card class="mnw-25em q-pa-md">
        <q-form @submit="submit">
          <q-card-section>
            <h5 class="m-0">Add Accept-Listed IP Address</h5>
            <q-input v-model="newIp" label="IP Address" autofocus :rules="ipValidationRules" lazy-rules />
          </q-card-section>
          <q-card-actions align="right">
            <q-btn label="Cancel" color="primary" outline @click="showAddIpDialog = false" />
            <q-btn :disable="isIpValid" label="Add" color="primary" type="submit" />
          </q-card-actions>
        </q-form>
      </q-card>
    </q-dialog>
  </div>
</template>

<script>
import { mapActions, mapGetters } from 'vuex';
import CopyToClipboard from '../../common/CopyToClipboard.vue';
import FilterInput from '../../common/FilterInput.vue';
import FormattedDate from '../../common/FormattedDate.vue';
export default {
  name: 'ActiveRemoteSessions',
  components: {
    FilterInput,
    CopyToClipboard,
    FormattedDate,
  },
  props: {},
  data() {
    return {
      loading: false,
      columns: [
        {
          name: 'ip_address',
          label: 'IP Address',
          field: 'ip_address',
          align: 'left',
          sortable: true,
        },
        {
          name: 'actions',
          label: 'Actions',
          field: 'actions',
          align: 'right',
          sortable: false,
        },
      ],
      tableData: [],
      showAddIpDialog: false,
      newIp: '',
      ipV4Regex: /^(\d{1,3}\.){3}\d{1,3}$/,
      ipValidationRules: [(v) => !!v || 'IP Address is required', (v) => this.ipV4Regex.test(v) || 'Invalid IP Address'],
    };
  },
  computed: {
    ...mapGetters({}),
    isIpValid() {
      return !this.ipValidationRules.every((rule) => rule(this.newIp) === true);
    },
  },
  methods: {
    ...mapActions({
      fetchIpAcceptList: 'remoteAccess/fetchIpAcceptList',
      addIpAcceptList: 'remoteAccess/addIpAcceptList',
      deleteIpAcceptListItem: 'remoteAccess/deleteIpAcceptListItem',
    }),
    fetchList() {
      this.loading = true;
      this.fetchIpAcceptList()
        .then((data) => {
          this.tableData = data.values.map((ip) => ({ ip_address: ip }));
        })
        .catch((err) => {
          console.log(err);
        })
        .finally((err) => {
          this.loading = false;
        });
    },
    addIp() {
      this.addIpAcceptList({ ips: [this.newIp] }).then(() => {
        this.showAddIpDialog = false;
        this.fetchList();
      });
    },
    removeIp(row) {
      this.$q
        .dialog({
          title: 'Remove IP Address',
          message: `Are you sure you want to remove ${row.ip_address} from accept list?`,
          ok: { label: 'Yes', color: 'primary' },
          cancel: { lebel: 'No', color: 'primary', outline: true },
        })
        .onOk(() => {
          this.deleteIpAcceptListItem({ ip: row.ip_address }).then(() => {
            this.fetchList();
          });
        });
    },
    submit() {
      if (!this.isIpValid) {
        this.addIp();
      }
    },
  },
  mounted() {
    this.fetchList();
  },
};
</script>

<style></style>
