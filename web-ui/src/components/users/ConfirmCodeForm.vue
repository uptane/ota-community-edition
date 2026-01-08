<template>
  <div id="code-form" class="login-form p-5 flex h-100 justify-center">
    <account-creation-header v-if="$q.screen.lt.lg"></account-creation-header>
    <div
      :class="{
        'm-auto': $q.screen.gt.md,
        'mt-0': $q.screen.lt.lg,
      }"
      style="max-width: 25rem"
    >
      <h4 class="text-center pt-0 mt-0">Verify Your Email</h4>
      <q-form ref="loginForm" @submit="onSubmit" @reset="onReset" class="q-gutter-md">
        <div v-if="error.message" class="error-message p-1 text-center">
          {{ error.message }}
        </div>
      </q-form>
      <div class="text-center mt-2">
        Please check your email for further instructions
      </div>
      <div class="text-center mt-2">
        <q-btn :loading="loading" :disable="loading" to="/login" flat no-caps color="primary">Sign In</q-btn>
      </div>
    </div>
  </div>
</template>

<script>
import { AuthService } from '../../services/auth.service';
import AccountCreationHeader from './AccountCreationHeader';

export default {
  name: 'ConfirmCodeFormComponent',
  components: {
    AccountCreationHeader,
  },
  data() {
    return {
      loading: false,
      codeModel: {
        code: '',
        username: '',
      },
      error: {
        message: '',
        code: '',
      },
      focused: {},
      hasUsername: false,
      existingUsermame: null,
      emailRegex: new RegExp(
        /(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|"(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21\x23-\x5b\x5d-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])*")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\[(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21-\x5a\x53-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])+)\])/,
      ),
    };
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
  methods: {
    onReset() {},
    onSubmit($e) {
      this.loading = true;
      // Remove leading and trailing whitespace
      const sanitizedCode = (this.codeModel.code || '').trim();
      AuthService.confirmRegistration({ code: sanitizedCode, username: this.hasUsername ? this.codeModel.username : this.$q.cookies.get('code_username') })
        .then((result) => {
          this.loading = false;
          this.justConfirmedEmail = true;
          this.$router.push({ name: 'login' });
        })
        .catch((err) => {
          this.loading = false;
          this.error = {
            message: 'We are unable to verify the code you entered. Please check the code and try again.',
          };
          logError(err);
        });
    },
    resendConfirmation($e) {
      if (!this.$q.cookies.get('code_username') && !this.codeModel.username) {
        return (this.error = { message: 'You must provide the email that you used during sign up.' });
      }
      this.loading = true;
      const data = { username: this.hasUsername ? this.codeModel.username : this.$q.cookies.get('code_username') };
      AuthService.resendConfirmation(data)
        .then((result) => {
          this.loading = false;
        })
        .catch((err) => {
          this.loading = false;
          this.error = {
            message: 'We are unable to complete your request. Please try again.',
          };
          logError(err);
        });
      // to reset validations:
      this.$refs.loginForm.resetValidation();
    },
    gotoRegister() {
      this.$emit('goto:register', '');
    },
    gotoPasswordHelp() {
      this.$emit('goto:password-help', '');
    },
  },
  mounted() {
    this.$set(this.codeModel, 'username', this.$q.cookies.get('code_username'));
  },
};
</script>
