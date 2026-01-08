<template>
  <q-form ref="loginForm" @submit="submit" class="q-gutter-md">
    <div class="login-stepper mxw-25em">
      <div class="step-email-input mb-1" v-if="loginStep === 'step-email-input'">
        <div class="mb-2">
          <q-input
            outlined
            @focus="inputFocused('email')"
            @blur="
              inputBlurred('email');
              loginModel.email = (loginModel.email || '').replaceAll(' ', '').toLowerCase();
            "
            v-model="loginModel.email"
            class="username mnw-20em"
            name="email"
            label="Your registered email"
            type="email"
            aria-autocomplete="username"
            aria-label="Your registered email"
            autocomplete="username"
            color="secondary"
            autofocus
            lazy-rules
            :rules="[(val) => (val !== null && val !== '') || 'Enter your registered email', (val) => val.match(emailRegex) || 'This is not a valid email']"
          >
            <template v-slot:prepend>
              <q-icon :color="focused['email'] ? 'secondary' : 'default'" name="alternate_email" />
            </template>
          </q-input>
        </div>
        <div>
          <q-btn id="next-step" :loading="loadingState" :disable="loadingState" type="submit" class="full-width" label="Continue" color="primary" size="lg">
            <template v-slot:loading>
              <q-spinner-gears class="on-left" /> Processing...
            </template>
          </q-btn>
          <div class="p-2 text-center">
            New user?
            <q-btn @click="gotoRegister" flat no-caps dense color="primary" id="sign-in-btn">Create new account</q-btn>
          </div>
        </div>
      </div>

      <div class="step-password-input" v-else-if="loginStep === 'step-password-input'">
        <div class="p-2 mb-2 text-center bg-amber-2" v-if="show2FAStatusWarning">
          You have enabled two-factor authentication for your account but you haven't yet set up an authenticator app.
          <q-btn flat dense color="primary" @click="resendTotpEmail" :loading="loadingState" :disable="loadingState">
            <template v-slot:loading>
              <q-spinner-gears class="on-left" /> Resend TOTP email...
            </template>
            Click here to resend the email with instructions on how to set it up
          </q-btn>
        </div>
        <q-input key="ro-email-input" borderless v-model="loginModel.email" name="email" class="username q-mb-sm ellipsis " color="secondary" :readonly="true">
          <template v-slot:prepend>
            <q-icon :color="focused['email'] ? 'secondary' : 'default'" class="q-sm" name="alternate_email" />
          </template>
          <template v-slot:append>
            <q-btn no-caps dense flat color="primary" @click="gotoEmailInput">
              <q-icon :color="focused['email'] ? 'secondary' : 'default'" name="edit" />
              <tooltip>Change email address</tooltip>
            </q-btn>
          </template>
        </q-input>

        <password-input
          outlined
          @focus="inputFocused('pwd')"
          @blur="inputBlurred('pwd')"
          type="password"
          v-model="loginModel.password"
          class="password"
          label="Password *"
          autocomplete="password"
          aria-autocomplete="password"
          aria-label="Password *"
          name="password"
          autofocus
          color="secondary"
          lazy-rules
          id="password-input"
          ref="myPwd"
          :rules="[(val) => (val !== null && val !== '') || 'Enter your password']"
        />

        <q-input
          key="rw-2fa-input"
          v-if="show2FAInput"
          outlined
          @focus="inputFocused('totp')"
          @blur="
            inputBlurred('totp');
            loginModel.totp = (loginModel.totp || '').replaceAll(' ', '').toLowerCase();
          "
          v-model="loginModel.totp"
          autocomplete="off"
          name="totp"
          mask="### ###"
          unmasked-value
          class="two-fa q-pb-lg"
          color="secondary"
          label="Two-Factor Code"
          lazy-rules
          :rules="[(val) => (val !== null && val !== '') || 'Enter the two-factor code from your authenticator app']"
        >
          <template v-slot:prepend>
            <q-icon :color="focused['totp'] ? 'secondary' : 'default'" class="q-sm" name="lock_clock" />
          </template>
        </q-input>

        <q-toggle v-model="loginModel.remember" label="Remember me" />

        <div class="q-mt-md">
          <q-btn id="submit" :loading="loadingState" :disable="loadingState" class="full-width" label="Sign In" size="lg" color="primary" type="submit">
            <template v-slot:loading>
              <q-spinner-gears class="on-left" /> Processing...
            </template>
          </q-btn>
        </div>

        <div class="text-center mt-2">
          <q-btn @click="gotoPasswordHelp" flat no-caps color="primary">
            Password help
          </q-btn>
          |
          <q-btn @click="gotoRegister" flat no-caps color="primary" id="sign-in-btn">Create new account</q-btn>
        </div>
      </div>
    </div>
  </q-form>
</template>

<script>
import { EMAIL_REGEX } from 'src/constants';
import { AuthService } from 'src/services/auth.service';
import Tooltip from '../common/Tooltip.vue';
import PasswordInput from './PasswordInput.vue';
export default {
  name: 'LoginInputComponent',
  components: {
    PasswordInput,
    Tooltip,
  },
  props: {
    // rawUsername: {
    //   type: String,
    //   default: ''
    // },
    value: {
      type: Object,
      default: () => ({
        email: '',
        password: '',
        remember: false,
        totp: '',
      }),
    },
    loading: {
      type: Boolean,
      default: false,
    },
    error: {
      type: Object,
      default: () => ({
        message: '',
      }),
    },
  },
  data() {
    return {
      focused: {
        email: false,
        pwd: false,
        totp: false,
      },
      loginStep: 'step-email-input',
      twoFAStatus: {},
      emailRegex: EMAIL_REGEX,
    };
  },
  computed: {
    loginModel: {
      get() {
        return this.value;
      },
      set(val) {
        this.$emit('input', val);
      },
    },
    show2FAStatusWarning() {
      return this.twoFAStatus.enabled && !this.twoFAStatus.configured;
    },
    show2FAInput() {
      return this.twoFAStatus.enabled && this.twoFAStatus.configured;
    },
    loadingState: {
      get() {
        return this.loading;
      },
      set(value) {
        this.$emit('update:loading', value);
      },
    },
    errorData: {
      get() {
        return this.error;
      },
      set(value) {
        this.$emit('update:error', value);
      },
    },
  },
  mounted() {
    this.prepareRememberedUser();
  },
  methods: {
    prepareRememberedUser() {
      const remembered = this.$q.localStorage.getItem('remembered_email');
      if (remembered) {
        this.$set(this.loginModel, 'remember', true);
        this.loginStep = 'step-email-input';
        this.loginModel = { ...this.loginModel, email: remembered };
        setTimeout(() => {
          this.submit();
        }, 200);
      }
    },
    resendTotpEmail() {
      this.loadingState = true;
      AuthService.resendTotpEmail(this.loginModel.email)
        .then((res) => {})
        .catch((err) => {
          logError(err);
        })
        .finally(() => {
          this.loadingState = false;
        });
    },
    gotoRegister() {
      this.$emit('goto:register', '');
    },
    gotoPasswordHelp() {
      this.$emit('goto:password-help', '');
    },
    gotoEmailInput() {
      this.loginStep = 'step-email-input';
      this.errorData = { message: '' };
    },
    verifyEmail() {
      this.loginStep = 'step-password-input';
    },
    verifyPassword() {
      this.loginStep = 'step-2fa-input';
    },
    resetValidation() {
      this.$refs.loginForm.resetValidation();
    },
    inputFocused(field) {
      this.focused[field] = true;
      this.errorData = { ...this.errorData, message: '' };
      this.$refs.loginForm.resetValidation();
    },
    inputBlurred(field) {
      this.focused[field] = false;
    },
    submit() {
      if (this.loginStep === 'step-email-input') {
        this.loadingState = true;
        AuthService.is2FactorEnabled(this.loginModel.email)
          .then((res) => {
            this.twoFAStatus = res;
            this.loginStep = 'step-password-input';
          })
          .catch((err) => {
            logError(err);
          })
          .finally(() => {
            this.loadingState = false;
          });
      } else if (this.loginStep === 'step-password-input') {
        this.$emit('login', this.loginModel);
        // to reset validations:
        this.resetValidation();
      }
    },
    postLoginSuccess() {
      if (this.loginModel.remember) {
        this.$q.localStorage.set('remembered_email', this.loginModel.email);
      } else {
        this.$q.localStorage.remove('remembered_email');
      }
    },
  },
};
</script>

<style></style>
