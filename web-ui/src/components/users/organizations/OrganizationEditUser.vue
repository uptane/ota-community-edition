<template>
  <div class="">
    <q-form ref="orgDataForm" @submit="saveChanges">
      <q-card-section class="">
        <h4 class="text-center m-0">Modify User Access</h4>
        <div class="text-center mt-1 ellipsis"><span class="faded">User Email:</span> {{ user.email }}</div>
      </q-card-section>
      <q-card-section class="pl-3 pr-3 ">
        <div class="row">
          <div class="col-12">
            <q-select
              outlined
              v-model="modifiedUser.guest_profile"
              label="Guest Profile"
              placeholder="Select guest profile"
              :options="options"
              :option-label="'friendly_name'"
              :option-value="'role'"
              :display-value="`${selectedGuestProfile.friendly_name}`"
              emit-value
              :multiple="false"
              :clearable="false"
              class="p-0 m-0"
            >
              <template v-slot:option="scope">
                <q-item v-bind="scope.itemProps" v-on="scope.itemEvents">
                  <q-item-section>
                    <q-item-label v-html="scope.opt.friendly_name" />
                    <q-item-label caption>{{ scope.opt.description }}</q-item-label>
                  </q-item-section>
                </q-item>
              </template>
            </q-select>
          </div>
        </div>
      </q-card-section>
      <q-card-section>
        <div class="text-center">
          <q-btn flat :disable="loading" color="default" icon="close" label="Cancel" v-close-popup></q-btn>
          <q-btn :loading="loading" :disable="loading" flat color="primary" icon-right="check" label="Save Changes" type="submit">
            <template slot="loading">
              <q-spinner-gears class="mr-1"> </q-spinner-gears>Processing...
            </template>
          </q-btn>
        </div>
      </q-card-section>
    </q-form>
  </div>
</template>

<script>
import { mapGetters, mapActions } from 'vuex';
export default {
  name: 'OrganizationEditUser',
  props: {
    user: {
      type: Object,
      required: true,
    },
  },
  data() {
    return {
      modifiedUser: {},
      loading: false,
    };
  },
  computed: {
    ...mapGetters({
      hostRepository: 'organizations/hostRepository',
      guestProfiles: 'organizations/guestProfiles',
    }),
    options() {
      return _.values(this.guestProfiles);
    },
    repository() {
      return this.hostRepository || {};
    },
    selectedGuestProfile() {
      return this.guestProfiles[this.modifiedUser.guest_profile] || {};
    },
  },
  methods: {
    ...mapActions({
      updateUserInRepository: 'organizations/updateUserInRepository',
    }),
    saveChanges() {
      this.loading = true;
      this.updateUserInRepository({ user: this.modifiedUser, organizationId: this.repository.id })
        .then(() => {
          this.loading = false;
          this.$emit('successful');
          this.$q.notify({
            color: 'positive',
            message: 'User repository access updated successfully',
          });
        })
        .catch(() => {
          this.loading = false;
          this.$q.notify({
            color: 'negative',
            message: 'Error updating repository user access',
          });
        })
        .finally(() => {
          this.loading = false;
        });
    },
  },
  mounted() {
    this.modifiedUser = { ...this.user };
  },
};
</script>

<style></style>
