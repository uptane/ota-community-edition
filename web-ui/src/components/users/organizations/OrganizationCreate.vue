<template>
  <div class="">
    <q-form ref="orgDataForm" @submit="saveChanges">
      <q-card-section class="">
        <h4 class="m-0 p-0 text-center">Set Repository Information</h4>
      </q-card-section>
      <q-card-section class="p-3">
        <div class="row">
          <div class="col-12">
            <q-input v-model="repoData.name" outlined label="Repository Name" type="company" placeholder="Enter repository name" lazy-rules :rules="[(val) => (val !== null && val !== '') || 'Please enter repository name']"> </q-input>
          </div>
        </div>
        <div class="row pt-2">
          <div class="col-12">
            <q-input v-model="repoData.description" outlined autogrow label="Repository Description" type="textarea" rows="3" placeholder="Describe your repository"></q-input>
          </div>
        </div>
      </q-card-section>
      <q-card-section>
        <div class="text-center">
          <q-btn flat :disable="loading" color="default" icon="close" label="Cancel" v-close-popup></q-btn>
          <q-btn :loading="loading" :disable="loading" flat color="primary" icon-right="check" label="Save Information" type="submit">
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
import { hostRepository } from '../../../store/organizations/getters';
export default {
  name: 'OrganizationCreate',
  props: {},
  data() {
    return {
      repoData: {},
      loading: false,
    };
  },
  computed: {
    ...mapGetters({
      hostRepository: 'organizations/hostRepository',
      defaultRepositoryData: 'organizations/defaultRepositoryData',
      user: 'ui/user',
    }),
    repository() {
      return this.hostRepository || {};
    },
    hostRepositoryIsDefined() {
      return this.hostRepository && this.hostRepository.id;
    },
  },
  methods: {
    ...mapActions({
      createRepository: 'organizations/createRepository',
      updateRepository: 'organizations/updateRepository',
    }),
    saveChanges() {
      this.loading = true;
      let promise;
      if (this.hostRepositoryIsDefined) {
        promise = this.updateRepository({ ...this.repoData });
      } else {
        promise = this.createRepository({ ...this.repoData });
      }
      promise
        .then(() => {
          this.loading = false;
          this.$emit('successful');
          this.$q.notify({
            color: 'positive',
            message: 'Repository information saved successfully',
          });
        })
        .catch(() => {
          this.loading = false;
          this.$q.notify({
            color: 'negative',
            message: 'Error creating repository',
          });
        })
        .finally(() => {
          this.loading = false;
        });
    },
  },
  mounted() {
    this.repoData = this.hostRepository && this.hostRepository.id ? { ...this.hostRepository } : this.defaultRepositoryData;
  },
};
</script>

<style></style>
