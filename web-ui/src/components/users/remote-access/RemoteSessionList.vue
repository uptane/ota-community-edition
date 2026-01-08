<template>
  <div class="">
    <div class="col-12">
      <h4 class="m-0 col mb-2">Active Remote Sessions</h4>
    </div>
    <table-skeleton v-if="loading" :rows="5" />
    <div v-else class="col-12 ">
      <p>This is a list of SSH keys associated with your account. Remove any keys that you do not recognize.</p>
      <q-table :data="tableData" :columns="columns" row-key="name" class="shadow-0">
        <template v-slot:body-cell-name="props">
          <q-td :props="props">
            <div class="text-1 text-bold">{{ props.row.name }}</div>
            <small>SHA256: {{ props.row.sha256 }}</small>
          </q-td>
        </template>
        <template v-slot:body-cell-actions="props">
          <q-td :props="props">
            <q-btn color="primary" label="delete" outline @click="deleteKey(props.row)" />
          </q-td>
        </template>
      </q-table>
    </div>
  </div>
</template>

<script>
import { mapActions, mapGetters } from 'vuex';
import TableSkeleton from '../../common/skeletons/TableSkeleton.vue';
function hash(string) {
  return new Promise((resolve, reject) => {
    const utf8 = new TextEncoder().encode(string);
    return crypto.subtle.digest('SHA-256', utf8).then((hashBuffer) => {
      const hashArray = Array.from(new Uint8Array(hashBuffer));
      const hashHex = hashArray.map((bytes) => bytes.toString(16).padStart(2, '0')).join('');
      return resolve(hashHex);
    });
  });
}
export default {
  components: { TableSkeleton },
  name: 'SshKeyList',
  props: {},
  data() {
    return {
      loading: false,
      percent: 0,
      columns: [
        {
          name: 'name',
          label: 'Device name',
          field: 'name',
          align: 'left',
          sortable: true,
        },
        // {
        //   name: 'pubkey',
        //   label: 'Public Key',
        //   field: 'pubkey',
        //   align: 'left',
        //   sortable: true
        // },
        {
          name: 'expiresAt',
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
      loading: false,
    };
  },
  computed: {
    ...mapGetters({
      publicKeys: 'remoteAccess/publicKeys',
    }),
  },
  methods: {
    ...mapActions({
      fetchPublicKeys: 'remoteAccess/fetchPublicKeys',
      deletePublicKey: 'remoteAccess/deletePublicKey',
    }),
    addKey() {
      this.loading = true;
      this.percent = 0;
      this.$emit('addKey');
    },
    getPublicKeys() {
      this.loading = true;
      this.fetchPublicKeys()
        .then(() => {
          this.loading = false;
        })
        .catch((err) => {
          this.loading = false;
          this.$q.dialog({
            title: 'Unable to fetch keys',
            message: err,
            ok: {
              label: 'Close',
              color: 'primary',
              flat: true,
            },
          });
        });
    },
    deleteKey(key) {
      // Confirm delete
      this.$q
        .dialog({
          title: 'Confirm delete',
          message: 'Are you sure you want to delete this key?',
          ok: {
            label: 'Cancel',
            color: 'primary',
          },
          cancel: {
            label: 'Delete',
            color: 'primary',
            outline: true,
          },
        })
        .onCancel(() => {
          this.deletePublicKey({ keyId: key.keyId })
            .then(() => {
              this.$q.notify({
                message: 'Key deleted',
                color: 'positive',
                icon: 'check',
              });
            })
            .catch((err) => {
              this.$q.dialog({
                title: 'Unable to delete key',
                message: err,
                ok: {
                  label: 'Close',
                  color: 'primary',
                  flat: true,
                },
              });
            });
        });
    },
    parseTableData() {
      const parsed = [];
      _.each((this.publicKeys || {}).keys, (pub, key) => {
        hash(pub.pubkey).then((hashedKey) => {
          pub.sha256 = hashedKey;

          parsed.push({
            name: pub.meta.name,
            pubkey: pub.pubkey,
            sha256: hashedKey,
            keyId: key,
            //   startedAt: key.startedAt,
          });
        });
      });
      this.tableData = parsed;
    },
  },
  mounted() {
    this.getPublicKeys();
  },
  watch: {
    publicKeys() {
      this.parseTableData();
    },
  },
};
</script>

<style></style>
