<template>
  <div id="register-form" class="register-form p-5 flex h-100 justify-center">
    <account-creation-header v-if="$q.screen.lt.lg"></account-creation-header>
    <div
      :class="{
        'm-auto': $q.screen.gt.md,
        'mt-1': $q.screen.lt.lg,
      }"
      style="max-width: 25rem"
    >
      <h4 class="text-center pt-0 mt-5">Create Account</h4>
      <q-form @submit="onSubmit" @reset="onReset" class="q-gutter-md">
        <div v-if="error.message" class="error-message p-1 text-center">{{ error.message }}</div>
        <q-input
          outlined
          @focus="
            focused['name'] = true;
            error.message = '';
          "
          @blur="
            focused['name'] = false;
            error.message = '';
          "
          v-model="userModel.name"
          label="Name "
          color="secondary"
          class="fullname"
          lazy-rules
          :rules="[(val) => (val !== null && val !== '') || 'Enter your full name (First and Last)']"
        >
          <template v-slot:prepend>
            <q-icon :color="focused['name'] ? 'secondary' : 'default'" name="person" />
          </template>
        </q-input>
        <q-input
          outlined
          @focus="
            focused['email'] = true;
            error.message = '';
          "
          @blur="
            focused['email'] = false;
            error.message = '';
          "
          v-model="userModel.username"
          label="Work Email *"
          color="secondary"
          class="username email"
          lazy-rules
          :rules="[(val) => (val !== null && val !== '') || 'Enter your email', (val) => val.match(emailRegex) || 'This is not a valid email']"
        >
          <template v-slot:prepend>
            <q-icon :color="focused['email'] ? 'secondary' : 'default'" name="alternate_email" />
          </template>
        </q-input>
        <q-input
          outlined
          @focus="
            focused['company'] = true;
            error.message = '';
          "
          @blur="
            focused['company'] = false;
            error.message = '';
          "
          v-model="userModel['company_name']"
          label="Company *"
          color="secondary"
          class="company"
          lazy-rules
          :rules="[(val) => (val !== null && val !== '') || 'Enter your company name']"
        >
          <template v-slot:prepend>
            <q-icon :color="focused['company'] ? 'secondary' : 'default'" name="fas fa-building" />
          </template>
        </q-input>
        <q-select
          name="country"
          id="country"
          class="country"
          outlined
          @filter="filterFn"
          :options="filteredCountries"
          @focus="
            focused['country'] = true;
            error.message = '';
          "
          @blur="
            focused['country'] = false;
            error.message = '';
          "
          v-model="countryValue"
          label="Country *"
          color="secondary"
          use-input
          lazy-rules
          :rules="[(val) => (val !== null && val !== '') || 'Enter your country']"
        >
          <template v-slot:prepend>
            <q-icon :color="focused['country'] ? 'secondary' : 'default'" name="fas fa-flag" />
          </template>
        </q-select>

        <password-component class="reg-password" v-model="userModel.password"></password-component>

        <div class="text-left mt-0">
          <q-checkbox :true-value="true" :false-value="false" v-model="userModel['receive_notifications']" label="I would like to receive notifications relating to this service." />
        </div>

        <div class="text-left mt-0">
          <q-checkbox :true-value="true" :false-value="false" v-model="userModel['receive_marketing']" label="I would like to receive newsletter and marketing emails from Toradex." />
        </div>

        <div class="text-left mt-0 mb-0">
          <q-checkbox :true-value="true" :false-value="false" v-model="userModel['accept_tos']" label="I accept " />
          <q-btn class="pl-0 ml-0 p-0" flat no-caps color="primary" type="a" :href="termsLink" padding="xs" target="_blank">Torizon Platform Terms of Services.</q-btn>
        </div>
        <div class="p-0 m-0 text-center">
          <q-btn class="pl-0 ml-0" flat no-caps color="primary" type="a" :href="privacyLink" target="_blank">Click here to read our privacy policy.</q-btn>
        </div>
        <div>
          <q-btn :loading="loading" :disable="loading" class="full-width" label="Create account" type="submit" id="continue-create-btn" color="primary">
            <template v-slot:loading>
              <q-spinner-gears class="on-left" />Processing...
            </template>
          </q-btn>
        </div>
      </q-form>
      <div class="text-center mt-2 mb-5">
        If you already have an account, you can
        <q-btn flat id="sign-in-btn" no-caps padding="xs" color="primary" @click="gotoLogin" class="pl-0 pr-0">sign in</q-btn> instead.
      </div>
    </div>
  </div>
</template>

<script>
import { AuthService } from '../../services/auth.service';
import PasswordInput from './PasswordInput';
import PasswordComponent from './PasswordComponent';
import { PRIVACY_POLICY_LINK } from '../../config';
import AccountCreationHeader from './AccountCreationHeader';
import { countries } from 'countries-list';
import { EMAIL_REGEX } from 'src/constants';

export default {
  name: 'RegisterFormComponent',
  components: {
    PasswordInput,
    PasswordComponent,
    AccountCreationHeader,
  },
  data() {
    return {
      filteredCountries: [],
      countryValue: null,
      userModel: {
        username: '',
        password: '',
        password2: '',
        remember: false,
        name: '',
        receive_marketing: false,
        accept_tos: false,
        company_name: '',
        country: '',
        receive_notifications: false,
        hide_intro: false,
      },
      privacyLink: PRIVACY_POLICY_LINK,
      termsLink: '/statics/html-contents/terms-2021-01-01.html',
      loading: false,
      error: {
        message: false,
      },
      focused: {},
      emailRegex: EMAIL_REGEX,
      passwordStrength: {
        strong: new RegExp('^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[!@#$%^&*])(?=.{8,})'),
        medium: new RegExp('^(((?=.*[a-z])(?=.*[A-Z]))|((?=.*[a-z])(?=.*[0-9]))|((?=.*[A-Z])(?=.*[0-9])))(?=.{6,})'),
      },

      countries: Object.values(countries).map((c) => {
        return {
          value: c.name,
          label: c.emoji + ' ' + c.name,
        };
      }),
    };
  },
  watch: {
    countryValue(n, o) {
      this.userModel['country'] = n.value;
    },
  },
  methods: {
    onReset() {},
    onSubmit() {
      if (!this.userModel['accept_tos']) {
        this.error.message = 'You must accept the license and terms to continue';
        return;
      }
      this.userModel['email'] = this.userModel.username;
      this.userModel.first_name = this.userModel.name;
      this.loading = true;
      AuthService.signUp(this.userModel)
        .then((result) => {
          this.loading = false;
          this.$q.sessionStorage.set('email_verification_username', this.userModel.username);
          this.$router.push({ name: 'confirmCode' });
        })
        .catch((err) => {
          this.loading = false;
          this.error = err;
          logError(err);
        });
      // to reset validations:
      // this.$refs.loginForm.resetValidation();
    },
    gotoLogin() {
      this.$emit('goto:login');
    },
    filterFn(val, update) {
      if (val === '') {
        update(() => {
          this.filteredCountries = this.countries;
        });
        return;
      } else {
        update(() => {
          const filtered = this.countries.filter((a) => a.label.match(new RegExp(val, 'i')) || a.value.match(new RegExp(val, 'i')));
          this.filteredCountries = filtered;
        });
      }
    },
  },
};
</script>
