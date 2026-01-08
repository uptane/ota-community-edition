<template>
  <div id="login-form" class="login-form p-5 flex h-100 justify-center">
    <account-creation-header v-if="$q.screen.lt.lg"></account-creation-header>
    <div
      :class="{
        'm-auto': $q.screen.gt.md,
      }"
      style="max-width: 25rem"
    >
      <h1 class="text-center pt-0 mt-0">Sign In</h1>
      <div v-if="justConfirmedEmail" class="p-3 bg-green mb-3 text-white row items-center animated zoomIn">
        <div class="col-auto pr-1">
          <q-icon name="check_circle" size="4rem" class="animated zoomIn" />
        </div>
        <div class="col">Thanks for confirming your email address. Please sign in below to continue.</div>
      </div>
      <div v-if="error.message" class="error-message p-1 text-center">
        {{ error.message }}
        <div v-if="error.code === 'EMAIL_NOT_VERIFIED'">
          <q-btn @click="resendConfirmation" color="primary" flat no-caps>Resend email validation instruction</q-btn>
        </div>
      </div>
      <login-wizard ref="loginFormWizard" v-model="loginModel" :loading.sync="loading" :error.sync="error" @goto:register="gotoRegister" @goto:password-help="gotoPasswordHelp" @login="onSubmit"></login-wizard>
    </div>
  </div>
</template>

<script>
import { AuthService } from '../../services/auth.service';
import PasswordInput from './PasswordInput';
import AccountCreationHeader from './AccountCreationHeader';
import LoginWizard from './LoginWizard.vue';

export default {
  name: 'LoginFormComponent',
  components: {
    PasswordInput,
    AccountCreationHeader,
    LoginWizard,
  },
  data() {
    return {
      loginModel: {
        email: '',
        username: '',
        password: '',
        remember: false,
      },
      rawUsername: '',
      loading: false,
      error: {
        message: '',
        code: '',
      },
      focused: {},
    };
  },
  methods: {
    onReset() {},
    onSubmit(model) {
      this.loading = true;
      this.justConfirmedEmail = false;
      AuthService.signIn(model)
        .then((result) => {
          this.loading = false;
          // Take care of post login success actions like remember me, etc
          this.$refs.loginFormWizard.postLoginSuccess(result);
          this.$router.push({ name: 'dashboard' });
          this.$router.go();
        })
        .catch((err) => {
          this.loading = false;
          this.error = err;
          this.loginModel.totp = '';
          this.$refs.loginFormWizard.resetValidation();
          logError(err);
        });
    },
    resendConfirmation($e) {
      AuthService.resendConfirmation(this.loginModel.email)
        .then((result) => {
          this.$q.sessionStorage.set('email_verification_username', this.loginModel.email);
          this.error = '';
          this.$router.push({ name: 'confirmCode' });
        })
        .catch((err) => {
          this.error = err;
          logError(err);
          // to reset validations:
          this.$refs.loginFormWizard.resetValidation();
        });
    },
    gotoRegister() {
      this.$emit('goto:register', '');
    },
    gotoPasswordHelp() {
      this.$emit('goto:password-help', '');
    },
  },
  computed: {
    justConfirmedEmail: {
      get() {
        return this.$store.getters['ui/justConfirmedEmail'];
      },
      set(v) {
        return this.$store.commit('ui/setJustConfirmedEmail', v);
      },
    },
  },
  mounted() {
    setTimeout(() => {
      this.justConfirmedEmail = !!(this.$route.query || {}).jc;
    }, 2000);
  },
  watch: {
    rawUsername() {
      this.loginModel.username = this.rawUsername;
    },
  },
};
</script>
