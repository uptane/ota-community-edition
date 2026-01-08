<template>
  <div class="">
    <div class="col-12">
      <h4 class="m-0 col mb-2">Remote Access Management</h4>
    </div>
    <div class="col-12 mxw-50em">
      <q-form @submit="addKey">
        <p>
          Add an SSH key for secure remote to Torizon Platform:
          <q-input
            v-model="sshKey"
            outlined
            dense
            placeholder="Paste your SSH key here"
            type="textarea"
            @input="sshKeyChanged"
            hint="Begins with 'ssh-rsa', 'ecdsa-sha2-nistp256', 'ecdsa-sha2-nistp384', 'ecdsa-sha2-nistp521', 'ssh-ed25519', 'sk-ecdsa-sha2-nistp256@openssh.com' or 'sk-ssh-ed25519@openssh.com'"
            :rules="[(val) => !!val || 'Public key is required']"
          />
        </p>
        <div class="q-pt-sm mxw-30em">
          <div class="text-bold">
            Friendly name:
          </div>
          <q-input v-model="sshKeyName" outlined dense placeholder="Example: My Laptop" :rules="[(val) => !!val || 'Friendly name is required']" />
        </div>
        <div class="mt-2 mb-2 mxw-30em">
          <q-btn
            :loading="loading"
            :percentage="percent"
            color="primary"
            :class="{
              'w-10em': loading,
            }"
            type="submit"
          >
            Add Key
            <template v-slot:loading>
              <q-spinner-gears class="on-left" />
              Processing...
            </template>
          </q-btn>
        </div>
      </q-form>
    </div>
  </div>
</template>

<script>
import { mapActions } from 'vuex';
export default {
  name: 'SshKeyEntry',
  data() {
    return {
      loading: false,
      percent: 0,
      sshKey: '',
      sshKeyName: '',
    };
  },
  methods: {
    ...mapActions({
      addPublicKey: 'remoteAccess/addPublicKey',
    }),
    sshKeyChanged(value) {
      this.$emit('sshKeyChanged', value);
    },
    sshKeyNameChanged(value) {
      this.$emit('sshKeyNameChanged', value);
    },
    addKey() {
      this.loading = true;
      this.percent = 0;
      this.addPublicKey({
        publicKey: this.sshKey,
        name: this.sshKeyName,
      })
        .then(() => {
          this.$emit('keyAdded');
          this.sshKey = '';
          this.sshKeyName = '';
          this.$q.notify({
            message: 'Key added successfully',
            color: 'positive',
            icon: 'check_circle',
          });
        })
        .catch((err) => {
          this.$emit('keyAdded');
          this.$q.dialog({
            title: 'Unable to add key',
            message: err,
            ok: {
              label: 'Close',
              color: 'primary',
              flat: true,
            },
          });
        })
        .finally(() => {
          this.loading = false;
        });
    },
  },
};
</script>

<style></style>
