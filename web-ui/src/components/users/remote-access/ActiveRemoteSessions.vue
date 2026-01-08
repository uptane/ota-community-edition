<template>
  <div class="">
    <div class="col-12">
      <h4 class="m-0 col mb-2">Active Remote Sessions</h4>
    </div>
    <div class="col-12 ">
      <p class="mxw-30em"><filter-input :placeholder="'Filter active devices'" /></p>
      <q-table :data="tableData" :columns="columns" row-key="name" class="shadow-0">
        <template v-slot:body-cell-host_name="props">
          <q-td :props="props" class=" mxw-20em">
            <div class="row">
              <div class="col-auto text-bold">{{ props.row.host_name }}</div>
              <div v-if="props.row.ssh_command" class="col-12">
                <span class="mr-1">{{ props.row.ssh_command }}</span>
                <copy-to-clipboard class="text-primary" :text="props.row.ssh_command" />
              </div>
            </div>
          </q-td>
        </template>
        <template v-slot:body-cell-remote_ip="props">
          <q-td :props="props">
            <div>{{ props.row.remote_ip }}</div>
          </q-td>
        </template>
        <template v-slot:body-cell-port="props">
          <q-td :props="props">
            <div>{{ props.row.port }}</div>
          </q-td>
        </template>
        <template v-slot:body-cell-expires_at="props">
          <q-td :props="props">
            <div v-if="props.row.session.ssh.expires_at">
              <div class=" text-bold">{{ $date.formatDate(props.row.session.ssh.expires_at, 'YYYY-MM-DD HH:mm') }}</div>
              (<timeago :datetime="props.row.session.ssh.expires_at"></timeago>)
            </div>
            <div v-else>
              <div class="">Never</div>
            </div>
          </q-td>
        </template>
        <template v-slot:body-cell-actions="props">
          <q-td :props="props">
            <q-btn icon="close" flat @click="killSession(props.row)">
              <q-tooltip>Terminate session</q-tooltip>
            </q-btn>
          </q-td>
        </template>
      </q-table>
    </div>
  </div>
</template>

<script>
import { mapActions, mapGetters } from 'vuex';
import { ensureCondition } from '../../../utils/Common';
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
  props: {
    sshKeys: {
      type: Array,
      default: () => [],
    },
  },
  data() {
    return {
      loading: false,
      columns: [
        {
          name: 'deviceName',
          label: 'Device name',
          field: 'deviceName',
          align: 'left',
          sortable: true,
        },
        {
          name: 'host_name',
          label: 'Host name',
          field: 'host_name',
          align: 'left',
          sortable: true,
        },
        {
          name: 'expires_at',
          label: 'Expires at',
          field: 'expires_at',
          align: 'left',
          sortable: true,
        },
        {
          name: 'actions',
          label: 'Actions',
          field: 'actions',
          align: 'center',
          sortable: false,
        },
      ],
      tableData: [],
    };
  },
  computed: {
    ...mapGetters({
      devicesByUuid: 'devices/devicesByUuid',
    }),
  },
  methods: {
    ...mapActions({
      fetchAllSessions: 'remoteAccess/fetchAllSessions',
    }),
    fetchSessions() {
      this.loading = true;
      ensureCondition({
        condition: () => {
          return this.devicesByUuid && Object.keys(this.devicesByUuid).length > 0;
        },
        interval: 500,
      })
        .then(() => {
          this.fetchAllSessions()
            .then((sessions) => {
              this.tableData = sessions
                .filter((f) => f.session)
                .map(({ deviceUuid, session, sessionInfo }) => {
                  let device = this.devicesByUuid[deviceUuid] || {};
                  return {
                    deviceName: device.deviceName || deviceUuid,
                    deviceUuid,
                    ...session,
                    // ...sessionInfo,
                    // ...sessionInfo.last_user_connect,
                    actions: 'actions',
                  };
                });
              this.loading = false;
            })
            .catch((err) => {
              this.loading = false;
              console.log(err);
            });
        })
        .catch((err) => {
          this.loading = false;
        });
    },
    killSession(session) {
      this.$q
        .dialog({
          title: 'Confirm session termination',
          message: `Are you sure you want to tarminate this session?`,
          cancel: true,
          persistent: true,
          ok: {
            label: 'Cancel',
            color: 'primary',
          },
          cancel: {
            label: 'Confirm',
            color: 'primary',
            outline: true,
          },
        })
        .onCancel(() => {
          this.$store
            .dispatch('remoteAccess/killSession', { deviceUuid: session.deviceUuid })
            .then(() => {
              this.$q.notify({
                message: 'Session terminated',
                color: 'positive',
                icon: 'check',
              });
              this.fetchSessions();
            })
            .catch((err) => {
              this.$q.dialog({
                title: 'Session termination failed',
                message: err || 'An error occurred while terminating the session',
                cancel: true,
                persistent: true,
                ok: {
                  label: 'Close',
                  color: 'primary',
                  flat: true,
                },
                cancel: false,
              });
              console.log(err);
            });
        });
    },
  },
  mounted() {
    this.fetchSessions();
  },
};
</script>

<style></style>
