<template>
  <div class="">
    <q-card-section class="">
      <h4 class="m-0 p-0 text-center">Invite Users</h4>
      <div class="text-center" v-if="resultsAvailable">The list below shows the result of adding the user(s) to the repository.</div>
      <div class="text-center" v-else>Invite users to this repository to give them access</div>
    </q-card-section>
    <q-card-section v-if="resultsAvailable">
      <div v-for="result in results" class="row justify-center ml-2 mr-2 q-pa-sm h-divide-top-dotted" :key="result.email">
        <div class="col-auto">
          <q-icon :name="result.error ? 'error' : 'check_circle'" :color="result.error ? 'negative' : 'positive'" size="1.3rem" class="q-mr-sm"></q-icon>
        </div>
        <div class="col">
          <div>{{ result.email }}</div>
          <div
            :class="{
              'text-negative': result.error,
              'text-positive': !result.error,
            }"
          >
            {{ result.message }}
          </div>
        </div>
      </div>
    </q-card-section>
    <q-card-section v-else>
      <q-form ref="inviteForm" @submit="addNewInvitationRow" class="p-2">
        <div class="mxh-35em h-60vh overflow-y-auto ">
          <div v-if="invitations && invitations.length">
            <div class="row h-divide-top-dotted items-center">
              <div class="col  pt-0 mt-0 pb-0 mb-0 q-pr-sm">
                <div class="q-pt-sm q-pb-sm faded">
                  <strong>User's Registered Email</strong>
                </div>
              </div>
              <div class="col-4 v-divide-right  v-divide-left q-pa-sm">
                <strong class="q-pa-sm faded">Access Level</strong>
              </div>
              <div class="col-2 pl-1 text-center">
                <strong class=" faded">Actions</strong>
              </div>
            </div>

            <div v-for="(invitation, index) in invitations" class="row h-divide-top-dotted items-center" :key="'invitation_' + index">
              <div class="col  pt-0 mt-0 pb-0 mb-0 q-pr-sm">
                <q-input
                  v-if="invitation.editing"
                  v-model="invitation.email"
                  :loading="lockedAndLoading"
                  :disable="lockedAndLoading"
                  outlined
                  @focus="inputFocused('email', invitation)"
                  @blur="
                    inputBlurred('email', invitation);
                    invitation.email = (invitation.email || '').replaceAll(' ', '').toLowerCase();
                  "
                  @input="inputChanged('email')"
                  label="Email"
                  type="email"
                  placeholder="Enter user's email"
                  class="m-0 p-0"
                  lazy-rules
                  :rules="[(val) => (val !== null && val !== '') || 'Enter the user\'s registered email', (val) => val.match(emailRegex) || 'This is not a valid email', (val) => !emailAlreadyAdded(invitation.email) || 'You have already added this email to the list']"
                >
                </q-input>
                <span v-else class="q-pt-md q-pb-md">{{ invitation.email }}</span>
              </div>
              <div class="col-4 v-divide-right v-divide-left q-pr-sm q-pl-sm">
                <q-select
                  v-if="invitation.editing"
                  :loading="lockedAndLoading"
                  :disable="lockedAndLoading"
                  outlined
                  v-model="invitation.role"
                  label="Guest Profile"
                  placeholder="Select guest profile"
                  :options="guestProfileOptions"
                  :option-label="'friendly_name'"
                  :option-value="'role'"
                  :display-value="`${invitation.role.friendly_name}`"
                  :multiple="false"
                  :clearable="false"
                  class="p-0 m-0"
                ></q-select>
                <div v-else class="q-pa-sm">{{ invitation.role.friendly_name }}</div>
              </div>
              <div class="col-2 text-center">
                <q-btn v-if="!invitation.editing" :loading="lockedAndLoading" :disable="lockedAndLoading" @click="startEditingRow(invitation)" flat color="primary" icon="edit" key="toggle_start_editing">
                  <tooltip>Modify</tooltip>
                </q-btn>
                <q-btn v-if="invitation.editing" :loading="lockedAndLoading" :disable="lockedAndLoading" @click.stop="doneEditingRow(invitation)" flat color="primary" icon="check" type="submit" key="toggle_done_editing">
                  <tooltip>Exit edit mode</tooltip>
                </q-btn>
                <q-btn @click="removeInvitationRow(index)" :loading="lockedAndLoading" :disable="lockedAndLoading" flat color="default" icon="close">
                  <tooltip>Remove</tooltip>
                </q-btn>
              </div>
            </div>
            <div class="row h-divide-top-dotted items-center cursor-pointer">
              <div class="col p-2 text-center">
                <q-btn :loading="lockedAndLoading" :disable="lockedAndLoading" :no-caps="lockedAndLoading" flat :color="lockedAndLoading ? 'default' : 'primary'" label="Click here to add users" icon="add" @click="addNewInvitationRow">
                  <template slot="loading">
                    <q-spinner-gears color="primary" size="1.3rem" class="q-mr-sm"> </q-spinner-gears>
                    Inviting users, please wait...
                  </template>
                </q-btn>
              </div>
            </div>
          </div>
          <div v-else class="row w-100 h-100 justify-center items-center">
            <empty title="Empty list" :oneLine="true" message="You have not added any user to invite yet" @on-action="addNewInvitationRow" :icon="'info'" :actionText="'Click here to add users'" actionColor="primary" :actionIcon="'add'"></empty>
          </div>
        </div>
      </q-form>
    </q-card-section>

    <q-card-section class=" h-divide-top-dotted">
      <div v-if="resultsAvailable" class="text-center">
        <q-btn :disable="loading" flat color="default" icon="close" label="Close" v-close-popup></q-btn>
        <q-btn v-if="errorOccured" :loading="lockedAndLoading" :disable="lockedAndLoading" flat color="primary" icon-right="refresh" label="Retry" @click="results = null">
          <template slot="loading">
            <q-spinner-gears class="mr-1"> </q-spinner-gears>Processing...
          </template>
        </q-btn>
      </div>
      <div v-else class="text-center">
        <q-btn :disable="loading" flat color="default" icon="close" label="Cancel" v-close-popup></q-btn>
        <q-btn v-if="invitationsAvailable" :loading="lockedAndLoading" :disable="lockedAndLoading" flat color="primary" icon-right="check" label="Invite Users" @click="ensureRepoIsCreatedAndAddUsers">
          <template slot="loading">
            <q-spinner-gears class="mr-1"> </q-spinner-gears>Processing...
          </template>
        </q-btn>
      </div>
    </q-card-section>
  </div>
</template>

<script>
import { mapActions, mapGetters } from 'vuex';
import { EMAIL_REGEX } from 'src/constants';
import Empty from '../../common/Empty.vue';
import Tooltip from '../../common/Tooltip.vue';
export default {
  components: { Empty, Tooltip },
  name: 'OrganizationUsersInvitationWizard',
  data() {
    return {
      splitterModel: 40,
      loading: false,
      emailRegex: EMAIL_REGEX,
      focused: {},
      repository: {},
      invitations: [],
      results: [],
    };
  },
  computed: {
    ...mapGetters({
      savedRepository: 'organizations/hostRepository',
      guestProfiles: 'organizations/guestProfiles',
      hostRepository: 'organizations/hostRepository',
      defaultRepositoryData: 'organizations/defaultRepositoryData',
      user: 'ui/user',
    }),
    hostRepositoryIsDefined() {
      return this.hostRepository && this.hostRepository.id;
    },
    guestProfileOptions() {
      return _.values(this.guestProfiles);
    },
    invitationsToSend() {
      return this.invitations.filter((invitation) => invitation.email && invitation.role);
    },
    modifiedRepository() {
      const orgData = { organizationId: this.repository.id, users: [] };
      return {
        ...orgData,
        users: this.invitations.map((invitation) => ({
          email: invitation.email,
          guest_profile: invitation.role.role,
        })),
      };
    },
    resultsAvailable() {
      return this.results && this.results.length > 0;
    },
    errorOccured() {
      return this.resultsAvailable && this.results.some((result) => result.error);
    },
    invitationsAvailable() {
      return this.invitationsToSend.length > 0;
    },
    allEmails() {
      return this.invitationsToSend.map((a) => a.email);
    },
    lockedAndLoading() {
      return this.loading;
    },
  },
  mounted() {
    this.repository = { ...this.savedRepository };
  },
  methods: {
    ...mapActions({
      saveRepository: 'organizations/saveRepository',
      createRepository: 'organizations/createRepository',
      addUsersToRepository: 'organizations/addUsersToRepository',
    }),
    emailAlreadyAdded(email) {
      return this.invitations.filter((invitation) => invitation.email === email).length > 1;
    },
    saveAllEntry() {
      this.$refs.inviteForm.validate().then((success) => {
        if (success !== false) {
          this.clearAllEditFlags();
          this.$refs.inviteForm.resetValidation();
        }
      });
    },
    addNewInvitationRow() {
      this.invitations = this.invitations.filter((invitation) => invitation.email && invitation.role);
      this.$refs.inviteForm.validate().then((success) => {
        if (success !== false) {
          this.clearAllEditFlags();
          this.invitations.push({
            email: '',
            role: { friendly_name: 'Read Access', role: 'guest-read-access' },
            editing: true,
          });
          this.$refs.inviteForm.resetValidation();
        }
      });
    },
    doneEditingRow(invitation) {
      this.$refs.inviteForm
        .validate()
        .then((success) => {
          if (success !== false) {
            invitation.editing = false;
            this.$refs.inviteForm.resetValidation();
          }
        })
        .catch((err) => {
          console.log(err);
        });
    },
    startEditingRow(invitation) {
      this.$refs.inviteForm.validate().then((success) => {
        if (success !== false) {
          this.clearAllEditFlags();
          invitation.editing = true;
          this.$refs.inviteForm.resetValidation();
        }
      });
    },
    removeInvitationRow(index) {
      this.invitations.splice(index, 1);
    },
    clearAllEditFlags() {
      this.invitations.forEach((invitation) => {
        invitation.editing = false;
      });
    },
    inputChanged(field) {
      this.$refs.inviteForm.resetValidation();
    },
    inputFocused(field, invitation) {
      this.focused[field] = true;
    },
    inputBlurred(field, invitation) {
      this.focused[field] = false;
    },
    ensureRepoIsCreatedAndAddUsers() {
      this.$refs.inviteForm.validate().then((success) => {
        if (success !== false) {
          this.loading = true;
          if (!this.hostRepositoryIsDefined) {
            const repoData = this.defaultRepositoryData;
            this.createRepository(repoData)
              .then(() => {
                this.inviteUsers();
              })
              .catch(() => {
                this.loading = false;
                this.results = this.allEmails.map((email) => ({
                  email,
                  message: 'There was an error inviting this user',
                  error: true,
                }));
              });
          } else {
            this.inviteUsers();
          }
        }
      });
    },
    inviteUsers() {
      this.loading = true;
      this.addUsersToRepository(this.modifiedRepository)
        .then((data) => {
          this.results = _.map(this.allEmails, (email) => ({
            email,
            message: 'User invited successfully',
            error: false,
          }));
        })
        .catch((err) => {
          let results = [],
            errorResults = {};
          errorResults = (((err.response || {}).data || {}).message || {}).invalid_user_emails || {};
          if (_.keys(errorResults).length < 1) {
            results = this.allEmails.map((email) => ({
              email,
              message: 'There was an error inviting this user',
              error: true,
            }));
          } else {
            const successResults = _.difference(this.allEmails, _.keys(errorResults));
            results = _.map(errorResults, (r, email) => ({
              email,
              message: 'There was an error inviting this user', // errorResults[email],
              error: true,
            })).concat(
              _.map(successResults, (email) => ({
                email,
                message: 'User added successfully',
                error: false,
              })),
            );
          }
          this.results = results;
        })
        .finally(() => {
          this.loading = false;
          if (this.$refs.inviteForm) {
            this.$refs.inviteForm.resetValidation();
          }
        });
    },
  },
};
</script>

<style></style>
