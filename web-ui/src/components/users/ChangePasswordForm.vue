<template>
  <div class="register-form p-5 flex h-100">
    <AccountCreationHeader v-if="$q.screen.lt.md"></AccountCreationHeader>
    <div class="justify-center text-center p-5 mxh-10em m-auto" v-if="finish">
      <div class="p-3 success-message">Password successfully changed</div>
      <q-btn class="p-0 full-width" @click="goBack" flat no-caps color="primary">Done</q-btn>
    </div>
    <div
      v-if="!finish"
      class
      :class="{
        'm-auto': $q.screen.gt.sm,
      }"
      style="max-width: 25rem"
    >
      <change-password-min-form @on:finish="onFinish" @on:cancel="goBack"></change-password-min-form>
    </div>
  </div>
</template>

<script>
import { AuthService } from '../../services/auth.service';
import ChangePasswordMinForm from './ChangePasswordMinForm';
import AccountCreationHeader from './AccountCreationHeader';

export default {
  name: 'ChangePasswordFormComponent',
  components: {
    ChangePasswordMinForm,
    AccountCreationHeader,
  },
  data() {
    return {
      loginModel: {
        email: '',
        oldPassword: '',
        newPassword: '',
        password2: '',
        remember: false,
        acceptTerms: false,
      },
      loading: false,
      finish: false,
      focused: {},
      emailRegex: new RegExp(
        /(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|"(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21\x23-\x5b\x5d-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])*")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\[(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21-\x5a\x53-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])+)\])/,
      ),
      passwordStrength: {
        strong: new RegExp('^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[!@#$%^&*])(?=.{8,})'),
        medium: new RegExp('^(((?=.*[a-z])(?=.*[A-Z]))|((?=.*[a-z])(?=.*[0-9]))|((?=.*[A-Z])(?=.*[0-9])))(?=.{6,})'),
      },
    };
  },
  methods: {
    onReset() {},
    onFinish() {
      this.finish = true;
    },
    goBack() {
      this.$router.go(-1);
    },
    passwordMatchCase(type) {
      let match = false;
      switch (type) {
        case 'uppercase':
          match = this.loginModel.newPassword.match(/[A-Z]/);
          break;
        case 'lowercase':
          match = this.loginModel.newPassword.match(/[a-z]/);
          break;
        case 'number':
          match = this.loginModel.newPassword.match(/[0-9]/);
          break;
        case 'symbol':
          match = this.loginModel.newPassword.match(/[$-/:-?{-~!"^_`\[\]]/);
          break;
        case 'minimum':
          match = this.loginModel.newPassword.length >= 8;
          break;
        default:
          break;
      }
      return match;
    },
    passwordMatchAll(type) {
      const match = this.passwordMatchCase('minimum') && this.passwordMatchCase('uppercase') && this.passwordMatchCase('lowercase') && this.passwordMatchCase('symbol') && this.passwordMatchCase('number');
      return match;
    },
  },
};
</script>
