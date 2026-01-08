<template>
  <q-dialog v-model="showRepoSwitchDialog">
    <q-card v-if="loading" class="mxw-40em w-40 mnw-30em p-1">
      <q-card-section>
        <h1 class="m-0 p-0 text-center">Switching Repository...</h1>
        <empty no-action no-fade one-line noIcon message="Please wait while we switch repositories. The page will reload when done."></empty>
      </q-card-section>
    </q-card>
    <q-card v-else class="mxw-40em w-40 mnw-30em p-1">
      <q-card-section>
        <h1 class="m-0 p-0 text-center">Switch Repository?</h1>

        <div>By switching to this repository, you will be accessing resources (devices, packages, fleets, etc.) in the selected repository's namespace. Switching will also make this the default repository for next time you log in to your account. Do you want to continue?</div>
      </q-card-section>
      <q-card-section>
        <div class="row items-center justify-center">
          <!-- <q-btn
              flat
              color="primary"
              @click="onSwitchRepo"
              class="col-auto"
            >
              Switch
            </q-btn> -->
          <q-btn flat color="primary" @click="onSwitchRepoWithDefault" class="col-auto">
            Yes, Switch and set as default
          </q-btn>
          <q-btn flat color="default" v-close-popup class="col-auto">
            No, Cancel
          </q-btn>
        </div>
      </q-card-section>
    </q-card>
  </q-dialog>
</template>

<script>
import { AuthService } from 'src/services/auth.service';
import { OptionsService } from 'src/services/options.service';
import Empty from '../../common/Empty.vue';
export default {
  components: { Empty },
  name: 'OrganizationRepoSwitchDialog',
  props: {
    // value: {
    //   type: Boolean,
    //   default: false
    // }
  },
  data() {
    return {
      showRepoSwitchDialog: false,
      selectedRepo: null,
      loading: false,
      checkColor: 'primary',
    };
  },
  methods: {
    onSwitchRepo: function() {
      this.switchRepository(this.selectedRepo, false);
      //   this.showRepoSwitchDialog = false;
      this.$emit('input', false);
    },
    onSwitchRepoWithDefault: function() {
      this.switchRepository(this.selectedRepo, true);
      //   this.showRepoSwitchDialog = false;
      this.$emit('input', false);
    },
    onClose: function() {
      this.showRepoSwitchDialog = false;
      this.$emit('input', false);
    },
    switchRepository(repository, setDefault) {
      this.loading = true;
      this.$events.$emit(`dialogs:organizations:set-repo-as-active:loading`, true);
      AuthService.switchAccessType(repository)
        .then(() => {
          this.$events.$emit(`dialogs:organizations:set-repo-as-active:set`, repository);
          if (setDefault) {
            // Let's make sure to set current page device pagination to the 1st page before switching to the new repo
            const pagination = OptionsService.getSavedOptionOrDefault('deviceTablePagination', {
              page: 1,
              rowsPerPage: 10,
              sortBy: 'name',
              descending: false,
            });
            pagination.page = 1;
            // Save the new pagination to the options store queue
            OptionsService.saveOption('deviceTablePagination', pagination);
            // Save the new repo as the default org repo and make the data is saved immediately. This will also ensure that deviceTablePagination is saved along with it.
            OptionsService.saveOptionImmediately('default_org_repo_id', repository.id)
              .catch(() => {
                console.log('Failed to save default_org_repo_id');
              })
              .finally(() => {
                this.$events.$emit(`dialogs:organizations:set-repo-as-active:loading`, false);
                window.location.reload();
              });
          } else {
            this.$events.$emit(`dialogs:organizations:set-repo-as-active:loading`, false);
            window.location.reload();
          }
        })
        .finally(() => {
          setTimeout(() => {
            this.loading = false;
          }, 2000);
        });
    },
  },
  mounted() {
    this.$events.$on(`dialogs:organizations:set-repo-as-active:confirm`, (data) => {
      this.showRepoSwitchDialog = true;
      this.$emit('input', true);
      this.selectedRepo = data;
    });
  },
};
</script>

<style></style>
