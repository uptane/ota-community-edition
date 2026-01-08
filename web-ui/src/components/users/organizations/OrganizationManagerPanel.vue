<template>
  <div class="row p-1">
    <div class="col-12">
      <div class="p-2 q-card pb-3">
        <div class="row">
          <h4 class="m-0 col">My Repository Information</h4>
          <q-btn flat color="primary" @click="beginCreateOrEdit()"> <q-icon class="pr" name="fab fa-git-alt"></q-icon>&nbsp;{{ createOrgLabel }} </q-btn>
        </div>
        <div v-if="loading" class="p-2">
          Loading...
        </div>
        <div v-else>
          <div class="">
            <div class="q-pt-sm">
              <span class="faded"> Name:</span>
              <span class="q-ml-sm">{{ repository.name }}</span>
            </div>
            <div class="q-pt-sm">
              <span class="faded"> Description:</span>
              <span class="q-ml-sm">{{ repository.description }}</span>
            </div>
            <!-- <div class="q-pt-sm">
              <span class="faded"> Default:</span>
              <span class="q-ml-sm">
                <span
                  v-if="isDefaultRepository(repository)"
                  class=""
                >
                  <q-icon
                    name="task_alt"
                    class=""
                  /> Yes
                </span>
                <span v-else> No
                  <q-btn
                    flat
                    color="primary"
                    @click="setRepoAsDefault(repository)"
                  >
                    <q-icon
                      name="task_alt"
                      class="mr-1"
                    /> Set As Default
                  </q-btn>
                </span>
              </span>
            </div> -->
            <div class="q-pt-sm" v-if="hostRepositoryIsDefined">
              <span class="faded"> Active:</span>
              <span class="q-ml-sm" v-if="isActiveRepository(repository)"> <q-icon name="check_circle" color="positive" class="" /> Yes </span>
              <span class="q-ml-sm" v-else>No</span>
            </div>
          </div>
        </div>
      </div>
      <q-list bordered>
        <q-expansion-item v-if="isCommercialUser" group="repogroup" icon="explore" label="Repository Users" default-opened header-class="h-divide-bottom">
          <template v-slot:header>
            <q-item-section avatar>
              <q-avatar>
                <q-icon size="1.5em" name="people"></q-icon>
              </q-avatar>
            </q-item-section>

            <q-item-section>
              <h5 class="m-0">Repository Users</h5>
            </q-item-section>
          </template>
          <div class="q-card p-2 row">
            <div class="col-4 mr-5">
              <filter-input v-model="usersFilter" placeholder="Filter users"></filter-input>
            </div>
            <div class="col"></div>
            <q-btn flat color="primary" @click="beginInvitation()"> <q-icon class="pr" name="person_add"></q-icon>&nbsp;Invite Users </q-btn>
          </div>
          <div v-if="loadingRepoUsers" class="q-card p-2 row items-center justify-center">
            <empty title="Loading users" message="Loading repository users" icon="sync" no-action></empty>
          </div>
          <div v-else class="q-card p-2 row">
            <div v-if="repositoryUsers && repositoryUsers.length > 0" class="row  w-100">
              <div :class="{}" class=" col-12">
                <q-table
                  :data="repositoryUsers"
                  :columns="repositoryUsersColumns"
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
                  <template v-slot:body-cell-email="props">
                    <q-td :props="props" :class="{ selected: selectedOrg && selectedOrg.org_id === props.row.org_id }">
                      <div class="">
                        <span>{{ props.row.email || '--' }}</span>
                        <div class=" mxw-20em ellipsis-2-lines">
                          <small class="opacity-60"> UUID: {{ props.row.namespace || '--' }} </small>
                        </div>
                      </div>
                    </q-td>
                  </template>
                  <template v-slot:body-cell-guest_profile="props">
                    <q-td :props="props" :class="{ selected: selectedOrg && selectedOrg.org_id === props.row.org_id }">
                      <div class="">
                        <span>{{ (guestProfiles[props.row.guest_profile] || {}).friendly_name }}</span>
                        <div class=" mxw-20em ellipsis-2-lines">
                          <small class="opacity-60" v-if="(guestProfiles[props.row.guest_profile] || {}).description">
                            {{ (guestProfiles[props.row.guest_profile] || {}).description }}
                          </small>
                        </div>
                      </div>
                    </q-td>
                  </template>
                  <template v-slot:body-cell-actions="props">
                    <q-td :props="props" auto-width>
                      <div class="">
                        <q-btn flat dense @click.stop="beginEditUser(props.row)" color="primary">
                          <q-icon name="edit" class="mr-1" />
                          <tooltip> Modify Access</tooltip>
                        </q-btn>
                        <q-btn flat dense @click="revokeUserAccess(props.row, $event)" color="negative">
                          <q-icon name="block" class="mr-1" />
                          <tooltip> Revoke access</tooltip>
                        </q-btn>
                      </div>
                    </q-td>
                  </template>
                </q-table>
              </div>
            </div>
            <div v-else class="row flex-center p-3 w-100">
              <empty title="" no-icon message="You have not invited any user to this repository" @on-action="beginInvitation()" action-text="Invite Users" action-icon="person_add"></empty>
            </div>
          </div>
        </q-expansion-item>
        <q-expansion-item group="repogroup" icon="far git-alt" header-class="h-divide-top" :default-opened="true">
          <template v-slot:header>
            <q-item-section avatar>
              <q-avatar>
                <q-icon size="1.5em" name="share"></q-icon>
              </q-avatar>
            </q-item-section>

            <q-item-section>
              <h5 class="m-0">Repositories Shared With You</h5>
            </q-item-section>
          </template>
          <div class="q-card p-2 row">
            <!-- <h4 class="m-0 col">Repository Users</h4> -->
            <div class="col-4 mr-5">
              <filter-input v-model="reposFilter" placeholder="Filter shared repositiries"></filter-input>
            </div>
            <div class="col"></div>
          </div>
          <div v-if="loadingGuestRepos" class="q-card p-2 row items-center justify-center">
            <empty title="Loading respositories" message="Loading repositories shared with you" icon="sync" no-action></empty>
          </div>
          <div v-else class="p-2 row">
            <div v-if="myGuestRepositories && myGuestRepositories.length > 0" class="row w-100 ">
              <div :class="{}" class="col-12">
                <q-table :data="myGuestRepositories" :columns="guestReposColumns" row-key="email" selection="single" :pagination.sync="pagination">
                  <template v-slot:body-selection> </template>
                  <template v-slot:body-cell-name="props">
                    <q-td :props="props" :class="{ selected: selectedOrg && selectedOrg.org_id === props.row.org_id }">
                      {{ props.row.name || '--' }}
                      <div class=" mxw-20em ellipsis-2-lines">
                        <small class="opacity-60" v-if="props.row.description">
                          {{ props.row.description }}
                        </small>
                      </div>
                    </q-td>
                  </template>
                  <template v-slot:body-cell-guest_profile="props">
                    <q-td :props="props" :class="{ selected: selectedOrg && selectedOrg.org_id === props.row.org_id }">
                      <div class="">
                        <span>{{ guestProfiles[props.row.guest_profile].friendly_name }}</span>
                        <div class=" mxw-20em ellipsis-2-lines">
                          <small class="opacity-60" v-if="guestProfiles[props.row.guest_profile].description">
                            {{ guestProfiles[props.row.guest_profile].description }}
                          </small>
                        </div>
                      </div>
                    </q-td>
                  </template>

                  <template v-slot:body-cell-status="props">
                    <q-td :props="props" auto-width class="w-15em">
                      <div class="">
                        <div>
                          <!-- <div
                            class="faded"
                            v-if="isDefaultRepository(props.row)"
                          >
                            <q-icon
                              name="task_alt"
                              class=" mr-1"
                            /> Default
                          </div> -->

                          <div class="" v-if="isActiveRepository(props.row)"><q-icon name="check_circle" color="positive" class="mr-1" /> Active</div>
                          <!-- <q-btn
                            v-if="!hostRepositoryIsDefined"
                            flat
                            dense
                            @click.stop="clearDefaultRepo(props.row)"
                            color="negative"
                            size="sm"
                          >
                            <q-icon
                              name="clear"
                              class="mr-1"
                            />Clear default
                          </q-btn> -->
                        </div>
                      </div>
                    </q-td>
                  </template>
                  <template v-slot:body-cell-actions="props">
                    <q-td :props="props" auto-width class="w-15em">
                      <div class="">
                        <q-btn v-if="!isDefaultRepository(props.row)" flat dense @click.stop="setRepoAsActiveAndDefault(props.row)" color="primary"> <q-icon name="task_alt" class="mr-1" />Set as active </q-btn>
                      </div>
                    </q-td>
                  </template>
                </q-table>
              </div>
            </div>
            <div v-else class="row w-100 flex-center p-3">
              <empty title="" no-icon message="There are no repositories shared with you yet." no-action></empty>
            </div>
          </div>
        </q-expansion-item>
      </q-list>
    </div>
    <q-dialog v-model="revokingUser">
      <q-card class="mnw-30em">
        <q-card-section>
          <div class="text-h6 text-center"><q-spinner-hourglass color="secondary" /> Revoking user access</div>
        </q-card-section>
      </q-card>
    </q-dialog>
    <q-dialog v-model="showInviteDialog">
      <q-card class="mxw-80em w-80 mnw-30em">
        <organization-users-invitation-wizard></organization-users-invitation-wizard>
      </q-card>
    </q-dialog>
    <q-dialog v-model="showCreateDialog">
      <q-card class="mxw-40em w-40 mnw-30em">
        <organization-create @successful="showCreateDialog = false"></organization-create>
      </q-card>
    </q-dialog>
    <q-dialog v-model="showUserEditDialog">
      <q-card class="mxw-40em w-40 mnw-30em">
        <organization-edit-user v-if="showUserEditDialog" :user="selectedUser" @successful="showUserEditDialog = false"></organization-edit-user>
      </q-card>
    </q-dialog>
  </div>
</template>

<script>
import { mapActions, mapGetters } from 'vuex';
import FormattedDate from 'src/components/common/FormattedDate.vue';
import MaskedText from 'src/components/common/masked-text.vue';
import TextCopy from 'src/components/common/TextCopy.vue';
import Tooltip from 'src/components/common/Tooltip.vue';
import { copyToClipboard } from 'quasar';
import OrganizationUsersInvitationWizard from './OrganizationUsersInvitationWizard.vue';
import OrganizationCreate from './OrganizationCreate.vue';
import FilterInput from '../../common/FilterInput.vue';
import OrganizationEditUser from './OrganizationEditUser.vue';
import Empty from '../../common/Empty.vue';
import { OptionsService } from 'src/services/options.service';

export default {
  components: { FormattedDate, Tooltip, MaskedText, TextCopy, OrganizationUsersInvitationWizard, OrganizationCreate, FilterInput, OrganizationEditUser, Empty },
  name: 'OrganizationManagerPanel',
  data() {
    return {
      pagination: {
        sortBy: 'name',
        descending: false,
        page: 1,
        rowsPerPage: 20,
      },
      guestReposColumns: [
        {
          name: 'name',
          required: true,
          label: 'Repository Information',
          align: 'left',
          field: (row) => row.name,
          format: (val) => `${val}`,
          sortable: true,
        },
        { name: 'guest_profile', label: 'Guest Profile', field: 'guest_profile', align: 'left' },
        { name: 'status', label: 'Status', field: 'status', align: 'left' },
        { name: 'actions', label: 'Actions', field: 'actions', align: 'left' },
      ],
      repositoryUsersColumns: [
        {
          name: 'email',
          required: true,
          label: 'User Email',
          align: 'left',
          field: (row) => row.email,
          format: (val) => `${val}`,
          sortable: true,
        },
        { name: 'guest_profile', label: 'Guest Profile', field: 'guest_profile', align: 'left' },
        { name: 'actions', label: 'Actions', field: 'actions', align: 'left' },
      ],
      showCreateDialog: false,
      showUserEditDialog: false,
      showInviteDialog: false,
      revokingUser: false,
      creatingClient: false,
      selectedOrg: null,
      selectedUser: null,
      loading: false,
      loadingRepoUsers: true,
      loadingGuestRepos: false,
      usersFilter: '',
      reposFilter: '',
      selectedArray: [],
      created: false,
      defaultRepoId: null,
    };
  },
  methods: {
    ...mapActions({
      getHostRepository: 'organizations/getHostRepository',
      getHostRepositoryUsers: 'organizations/getHostRepositoryUsers',
      getGuestProfiles: 'organizations/getGuestProfiles',
      removeUserFromRepository: 'organizations/removeUserFromRepository',
      saveMetadata: 'users/saveMetadata',
    }),
    beginEditUser(user) {
      this.selectedUser = user;
      this.showUserEditDialog = true;
    },
    beginInvitation() {
      this.showInviteDialog = true;
    },
    beginCreateOrEdit() {
      this.showCreateDialog = true;
    },
    revokeUserAccess(userData, event) {
      event.stopPropagation();
      this.$q
        .dialog({
          title: 'Revoke User Access',
          message: 'Are you sure you want to revoke user ' + userData.email + "'s access to this repository?",
          cancel: {
            label: 'No',
            color: 'default',
            flat: true,
          },
          ok: {
            label: 'Yes',
            color: 'negative',
            flat: true,
          },
        })
        .onOk(() => {
          this.revokingUser = true;
          this.removeUserFromRepository({ user: userData, organizationId: this.repository.id })
            .then((result) => {
              this.$q.dialog({
                title: 'User Access Revoked',
                message: 'User access was successfully revoked.',
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
                message: 'User access was not revoked, please try again.',
                ok: {
                  label: 'OK',
                  color: 'primary',
                  flat: true,
                },
              });
            })
            .finally(() => {
              this.revokingUser = false;
            });
        });
    },
    setRepoAsActiveAndDefault(repo) {
      this.$events.$emit('dialogs:organizations:set-repo-as-active:confirm', repo);
    },
    setRepoAsDefault(repo) {
      this.$q
        .dialog({
          title: 'Set As Default Repository',
          message: `Setting \`${repo.name}\` as default will make it the selected repository when you login to your account.
        Are you sure you want continue?`,
          cancel: {
            label: 'No',
            color: 'default',
            flat: true,
          },
          ok: {
            label: 'Yes',
            color: 'primary',
            flat: true,
          },
        })
        .onOk(() => {
          OptionsService.saveOption('default_org_repo_id', repo.id);
          this.defaultRepoId = repo.id;
          this.$q.dialog({
            title: 'Repository Set As Default',
            message: 'Repository was successfully set as default.',
            ok: {
              label: 'OK',
              color: 'primary',
              flat: true,
            },
          });
        });
    },
    clearDefaultRepo(repo) {
      this.$q
        .dialog({
          title: 'Clear Default Repository',
          message: 'Are you sure you want to change `' + repo.name + '` from being your default repository?',
          cancel: {
            label: 'No',
            color: 'default',
            flat: true,
          },
          ok: {
            label: 'Yes',
            color: 'primary',
            flat: true,
          },
        })
        .onOk(() => {
          OptionsService.saveOption('default_org_repo_id', null);
          this.defaultRepoId = null;
          this.$q.dialog({
            title: 'Repository Is No Longer Default',
            message: 'Repository was successfully unset as default.',
            ok: {
              label: 'OK',
              color: 'primary',
              flat: true,
            },
          });
        });
    },

    rowClicked(row) {
      // this.beginEditUser(row);
    },
    fetchRepositoryData() {
      this.loading = true;
      this.getHostRepository()
        .then((result) => {})
        .catch((err) => {})
        .finally(() => {
          this.loading = false;
          this.fetchHostRepositoryUsers();
        });
    },
    fetchHostRepositoryUsers() {
      this.loadingRepoUsers = true;
      this.getHostRepositoryUsers({ organizationId: this.repository.id })
        .then((result) => {})
        .catch((err) => {})
        .finally(() => {
          this.loadingRepoUsers = false;
        });
    },
    isDefaultRepository(repo) {
      return this.defaultRepoId && repo && this.defaultRepoId === repo.id;
    },
    isActiveRepository(repo) {
      return this.activeRepository && repo && repo.id === this.activeRepository.id;
    },
  },
  computed: {
    ...mapGetters({
      guestRepositories: 'organizations/guestRepositories',
      hostRepository: 'organizations/hostRepository',
      hostRepositoryIsDefined: 'organizations/hostRepositoryIsDefined',
      activeRepository: 'organizations/activeRepository',
      hostRepositoryUsers: 'organizations/hostRepositoryUsers',
      guestProfiles: 'organizations/guestProfiles',
      defaultRepositoryData: 'organizations/defaultRepositoryData',
      userSettings: 'ui/userSettings',
      user: 'ui/user',
      isCommercialUser: 'users/isCommercialUser',
    }),
    user_settings() {
      return this.userSettings || {};
    },
    myGuestRepositories() {
      return (this.guestRepositories || []).filter((f) => {
        return (f.name + ' ' + f.description).match(new RegExp(this.reposFilter, 'i'));
      });
    },

    repository() {
      const repo = this.hostRepository && this.hostRepository.id ? this.hostRepository : this.defaultRepositoryData;
      return repo;
    },
    repositoryUsers() {
      return (this.hostRepositoryUsers || []).filter((f) => {
        return (f.email + ' ' + f.guest_profile).match(new RegExp(this.usersFilter, 'i'));
      });
    },
    createOrgLabel() {
      return 'Edit Repository Information';
    },
  },
  watch: {},
  mounted() {
    this.fetchRepositoryData();
    this.defaultRepoId = OptionsService.getSavedOptionOrDefault('default_org_repo_id', null);
  },
};
</script>

<style></style>
