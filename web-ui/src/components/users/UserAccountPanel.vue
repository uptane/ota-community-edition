<template>
  <div class="row p-1">
    <div class="col-12">
      <div class="q-card p-2 row">
        <h4 class="m-0 col-12">Personal Information</h4>
        <div class="col-auto pr-2">
          <q-item>
            <q-item-label>
              <div v-if="!updatingAvatar" class="row q-item-tile label pt-1">
                <div class="col-auto pr-1">
                  <user-avatar @click="updatingAvatar = !updatingAvatar"></user-avatar>
                </div>
                <q-btn flat dense small @click="updatingAvatar = !updatingAvatar">Set avatar</q-btn>
              </div>
              <avatar-upload-dialog v-if="updatingAvatar" @on-ok="imageUploadDone" @on-cancel="updatingAvatar = !updatingAvatar"></avatar-upload-dialog>
            </q-item-label>
          </q-item>
        </div>
        <div class="col-12 pr-2">
          <q-btn flat color="secondary" @click="signOut" :loading="signingOut">Sign out</q-btn>
        </div>
        <div class="col-12 pr-2">
          <editable-account-info-input field="given_name" edit-field="first_name" label="Name:" edit-label="Name:" :allow-edit="false"></editable-account-info-input>
          <editable-account-info-input field="company_name" label="Company Name:" edit-label="Your Company:" :allow-edit="false"></editable-account-info-input>
          <editable-account-info-input field="country" label="Country:" type="list" :options="countries" edit-label="Your Country:" :allow-edit="false"></editable-account-info-input>
          <div class="row q-item-tile  sublabel pt-1">
            <div class="col-3  mxw-10em pr-1">Email:</div>
            <div class="col-9">{{ user.email }}</div>
          </div>
          <div v-if="false">
            <div class="row q-item-tile  sublabel pt-1">
              <div class="col-3  mxw-10em pr-1">Email:</div>
              <div class="col-9">
                <q-input v-model="newEmail" />
                <q-btn @click="changeEmail" label="Save" />
              </div>
            </div>
            <div class="row q-item-tile  sublabel pt-1">
              <div class="col-9">Email not verified</div>
            </div>
            <div class="row q-item-tile  sublabel pt-1">
              <div class="col-3  mxw-10em pr-1">Verification code:</div>
              <div class="col-9">
                <q-input v-model="verificationCode" />
                <q-btn @click="resendEmailCode" label="Resend Code" />
                <q-btn @click="verifyEmail" label="Save" />
              </div>
            </div>
          </div>
          <div class="row q-item-tile  sublabel pt-1">
            <div class="col-3  mxw-10em pr-1">User ID:</div>
            <div class="col-9">{{ user.sub }}</div>
          </div>
          <div class="row q-item-tile  sublabel pt-1">
            <div class="col-3  mxw-10em pr-1">Repository UUID:</div>
            <div class="col-9">{{ user.namespace }}</div>
          </div>
          <div class="row q-item-tile  sublabel pt-1 items-center">
            <div class="col-3  mxw-10em pr-1">Account Tier:</div>
            <div class="col-9">
              <div v-if="isCommercialUser">
                <span class="">Commercial</span>
                <q-icon size="1.8rem" name="img:/statics/svg/premium-badge.svg"></q-icon>
              </div>
              <div class="row items-center" v-else>
                <div class="col-auto">Standard</div>
                <div class="col ">
                  <request-premium-btn flat size="md"></request-premium-btn>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    <div class="col-12 pt-1 overflow-hidden">
      <div class="q-card p-2 row">
        <h4 class="m-0 col-12">Account Management</h4>
        <div class="col-auto pr-2">
          <p>
            To modify your account security and profile information, click on the link below. You will be redirected to the account management page.
          </p>
          <p>
            <q-btn color="primary" type="a" target="_blank" :href="profileUrl">Manage Account &nbsp;<q-icon name="launch"></q-icon></q-btn>
          </p>
        </div>
      </div>
    </div>
    <feature-teaser class="col-12 pt-1" feature="provision-device">
      <div class="q-card p-2 row">
        <div class=" col-12">
          <h4 class="m-0">Device Provisioning</h4>
          <device-provision-limit-info></device-provision-limit-info>

          <p class="mt-1 ">To provision a device, click the button below to generate a command snippet with provision token.</p>
        </div>
        <div class="col-12">
          <q-btn color="primary" type="a" target="_blank" @click="provisionNewDevice" :loading="loadingProv" :percentage="provPercent">
            Provision New Device
            <template v-slot:loading>
              <q-spinner-gears class="on-left" />Precessing...
            </template>
          </q-btn>
        </div>
        <div class="col-12">
          <device-provision
            show-x
            @state-change="
              loadingProv = $event.loading;
              provPercent = $event.percent;
            "
            ref="prov"
          ></device-provision>
        </div>
      </div>
    </feature-teaser>
    <feature-teaser class="col-12 pt-1" feature="manage-credentials">
      <div class="q-card p-1 row">
        <div class="col-12 pr-2">
          <div class="row q-item-tile label p-1">
            <div class="col-auto">
              <manage-credentials></manage-credentials>
            </div>
          </div>
        </div>
      </div>
    </feature-teaser>
    <div class="col-12 pt-1" feature="manage-credentials">
      <div class="q-card p-1 row">
        <div class="col-12 pr-2">
          <div class="row q-item-tile label p-1">
            <div class="col-auto">
              <onboarding-guide></onboarding-guide>
            </div>
          </div>
        </div>
      </div>
    </div>
    <div v-if="isCommercialUser" class="col-12 pt-1">
      <div class="q-card p-1 row">
        <div class="col-12 pr-2">
          <div class="row q-item-tile label p-1">
            <div class="col-auto">
              <early-access></early-access>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { mapGetters } from 'vuex';
import ChangePasswordMinForm from 'src/components/users/ChangePasswordMinForm';
import Loader from 'src/components/loaders/Loader';
import UserAvatar from 'src/components/users/UserAvatar';
import AvatarUploadDialog from 'src/components/users/AvatarUploadDialod';
import EditableAccountInfoInput from 'src/components/users/EditableAccountInfoInput';
import DeviceProvision from 'src/components/devices/DeviceProvision';
import { AuthService } from 'src/services/auth.service';
import { countries } from 'countries-list';
import DeviceProvisionLimitInfo from 'src/components/devices/DeviceProvisionLimitInfo';
import ManageCredentials from 'src/components/users/ManageCredentials';
import RequestPremiumBtn from 'src/components/common/RequestPremiumBtn';
import gtm from 'src/services/gtm.service';
import Tooltip from '../common/Tooltip.vue';
import OnboardingGuide from './OnboardingGuide.vue';
import EarlyAccess from './EarlyAccess.vue';

export default {
  name: 'UserAccountPanel',
  components: {
    Loader,
    UserAvatar,
    AvatarUploadDialog,
    EditableAccountInfoInput,
    ChangePasswordMinForm,
    DeviceProvision,
    DeviceProvisionLimitInfo,
    ManageCredentials,
    RequestPremiumBtn,
    Tooltip,
    OnboardingGuide,
    EarlyAccess,
  },
  data() {
    return {
      twoFA: false,
      updatingAvatar: false,
      loading: true,
      loadingCred: false,
      deleteTotpDialog: false,
      totpDeviceToDelete: null,
      totpCodeBusy: false,
      totpPassword: '',
      totpCode: '',
      signingOut: false,
      passwordResetLinkBusy: false,
      credPercent: 0,
      loadingProv: false,
      newEmail: '',
      verificationCode: '',
      showChangePassword: false,
      changedPassword: false,
      updating: {},
      nameUpdate: '',
      provPercent: 0,
      twoFAStatus: {},
      totpDevices: [],
      deletingTotpDevice: {},
      countries: Object.values(countries).map((c) => {
        return {
          value: c.name,
          label: c.emoji + ' ' + c.name,
        };
      }),
    };
  },
  created() {},
  mounted() {
    this.pageTitle = 'Account Information';
  },
  computed: {
    ...mapGetters({
      isCommercialUser: 'users/isCommercialUser',
    }),
    profileUrl() {
      return AuthService.profileUrl;
    },
    pageTitle: {
      get() {
        return this.$store.getters['ui/currentPageTitle'];
      },
      set(val) {
        return this.$store.commit('ui/setCurrentPageTitle', val);
      },
    },
    initial() {
      return ((this.user.name || this.user.email || '')[0] || '').toUpperCase();
    },
    user() {
      return this.$store.getters['ui/user'] || {};
    },
    totpConfigured() {
      return this.twoFAStatus.enabled && this.twoFAStatus.configured;
    },
    totpEnabled() {
      return this.twoFAStatus.enabled && !this.twoFAStatus.configured;
    },
    darkTheme: {
      get() {
        const val = this.$store.getters['ui/isDarkTheme'];
        return val;
      },
      set(val) {
        return this.$store.commit('ui/setIsDarkTheme', val);
      },
    },
  },
  methods: {
    provisionNewDevice() {
      this.$refs.prov.getProvisioningToken();
      gtm.logEvent('Account', 'click', 'Create Device', null);
    },
    saveField(name, value, defaultValue) {
      AuthService.updateUserData({ [name]: value || defaultValue })
        .then((saved) => {
          this.updating[name] = false;
          // this.nameUpdate = this.user.name;
        })
        .catch((e) => {
          this.updating[name] = false;
        });
    },
    changeEmail() {
      this.saveField('email', this.newEmail);
    },
    verifyEmail() {
      AuthService.verifyAttribute({ attributeName: 'email', code: this.verificationCode })
        .then((saved) => {
          // this.updating[name] = false;
          // this.nameUpdate = this.user.name;
        })
        .catch((e) => {
          // this.updating[name] = false;
        });
    },
    resendEmailCode() {
      AuthService.resendAttributeVerificationCode({ attributeName: 'email' })
        .then((saved) => {
          // this.updating[name] = false;
          // this.nameUpdate = this.user.name;
        })
        .catch((e) => {
          // this.updating[name] = false;
        });
    },
    sendPasswordResetLink() {
      this.passwordResetLinkBusy = true;
      AuthService.forgotPassword({ username: this.user.email })
        .then((done) => {
          this.$q.dialog({
            title: 'Password Reset Instructions Sent',
            message: 'Please check your registered email inbox for next step on changing your password.',
            color: 'primary',
          });
        })
        .catch((err) => {
          this.$q.dialog({
            title: 'Error Occured',
            message: 'We are currently unable to process your request. Please try again in a moment.',
            color: 'primary',
          });
        })
        .finally(() => {
          this.passwordResetLinkBusy = false;
          gtm.logEvent('Account', 'click', 'Change Password', null);
        });
    },
    finishChangePasswordProcess() {
      this.showChangePassword = false;
      this.changedPassword = true;
      setTimeout(() => {
        this.changedPassword = false;
      }, 5000);
    },
    startChangePasswordProcess() {
      this.showChangePassword = true;
      this.changedPassword = false;
    },
    cancelChangePasswordProcess() {
      this.showChangePassword = false;
      this.changedPassword = false;
    },

    imageUploadDone(data) {
      this.updatingAvatar = !this.updatingAvatar;
    },
    signOut() {
      gtm.logEvent('User Info', 'click', 'Signout', null);
      this.signingOut = true;
      AuthService.signOut()
        .then((result) => {
          this.$router.push({ name: 'login' });
          this.$router.go();
        })
        .catch((err) => {
          logError(err);
        })
        .finally(() => {
          this.signingOut = false;
        });
    },
  },
};
</script>

<style></style>
