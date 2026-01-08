<template>
  <q-page
    :class="{
      'mobile-mode': $q.screen.lt.lg,
      [mode + '-mode']: true,
    }"
    class=" flex mxw login-page"
  >
    <div
      class="form-bg"
      :class="{
        'w-100vw': $q.screen.lt.lg,
      }"
    ></div>
    <div class=" flex flex-column">
      <div class="confirm-code-section animated fadeIn w-100vw  h-100vh">
        <confirm-code-form
          :class="{
            'w-100vw': $q.screen.lt.lg,
            'w-50vw': $q.screen.gt.md,
          }"
          @goto:register="gotoRegister"
          @goto:login="gotoLogin"
        ></confirm-code-form>
        <confirm-code-text v-if="$q.screen.gt.md" @goto:register="gotoRegister" @goto:login="gotoLogin"></confirm-code-text>
      </div>
      <div class="reset-password-section animated fadeIn w-100vw  h-100vh">
        <reset-password-form
          :class="{
            'w-100vw': $q.screen.lt.lg,
            'w-50vw': $q.screen.gt.md,
          }"
          @goto:register="gotoRegister"
          @goto:login="gotoLogin"
        ></reset-password-form>
        <reset-password-text v-if="$q.screen.gt.md" @goto:register="gotoRegister" @goto:login="gotoLogin"></reset-password-text>
      </div>
      <div class="password-help-section animated fadeIn w-100vw  h-100vh">
        <password-help-form
          :class="{
            'w-100vw': $q.screen.lt.lg,
            'w-50vw': $q.screen.gt.md,
          }"
          @goto:register="gotoRegister"
          @goto:login="gotoLogin"
        ></password-help-form>
        <password-help-text v-if="$q.screen.gt.md" @goto:register="gotoRegister" @goto:login="gotoLogin"></password-help-text>
      </div>
      <div class="login-section animated fadeIn w-100vw mnh-100vh">
        <login-form
          :class="{
            'w-100vw': $q.screen.lt.lg,
            'w-50vw': $q.screen.gt.md,
          }"
          @goto:register="gotoRegister"
          @goto:password-help="gotoPasswordHelp"
        ></login-form>

        <login-text v-if="$q.screen.gt.md" @goto:register="gotoRegister" @goto:password-help="gotoPasswordHelp"></login-text>
      </div>
      <div class="register-section animated fadeIn w-100vw mnh-100vh">
        <register-text
          :class="{
            'w-50vw': $q.screen.gt.md,
          }"
          v-if="$q.screen.gt.md"
          @goto:login="gotoLogin"
        ></register-text>
        <register-form
          :class="{
            'w-100vw': $q.screen.lt.lg,
            'w-50vw': $q.screen.gt.md,
          }"
          @goto:login="gotoLogin"
        ></register-form>
      </div>
    </div>
  </q-page>
</template>

<script>
import ResetPasswordForm from '../components/users/ResetPasswordForm';
import ResetPasswordText from '../components/users/ResetPasswordText';
import ConfirmCodeForm from '../components/users/ConfirmCodeForm';
import ConfirmCodeText from '../components/users/ConfirmCodeText';
import RegisterForm from '../components/users/RegisterForm';
import LoginForm from '../components/users/LoginForm';
import LoginText from '../components/users/LoginText';
import RegisterText from '../components/users/RegisterText';
import ChangePasswordForm from '../components/users/ChangePasswordForm';
import ChangePasswordText from '../components/users/ChangePasswordText';
import PasswordHelpForm from '../components/users/PasswordHelpForm';
import PasswordHelpText from '../components/users/PasswordHelpText';
import { paramCase } from 'change-case';

export default {
  name: 'PageDashboard',
  components: {
    LoginForm,
    RegisterForm,
    PasswordHelpForm,
    LoginText,
    RegisterText,
    PasswordHelpText,
    ChangePasswordForm,
    ChangePasswordText,
    ConfirmCodeForm,
    ConfirmCodeText,
    ResetPasswordForm,
    ResetPasswordText,
  },
  data() {
    return {
      loginModel: {
        email: '',
        password: '',
        remember: false,
      },
      focused: {},
      leftBg: true,
    };
  },
  created() {
    // if(this.$route.name === 'login'){
    // this.setClass = false;
    // this.setClass = true;
    // }
    // if(this.$route.name === 'register'){
    //   this.loginMode = false;
    // }
  },
  computed: {
    mode() {
      return paramCase(this.$route.name);
    },
    loginBg() {
      return require('../assets/svg/login-bg.svg');
    },
    registerBg() {
      return require('../assets/svg/register-bg.svg');
    },
  },
  methods: {
    onReset() {},
    onSubmit() {},
    gotoRegister() {
      this.$router.push({ name: 'register' });
      this.leftBg = false;
    },
    gotoLogin() {
      this.$router.push({ name: 'login' });
      this.leftBg = true;
    },
    gotoPasswordHelp() {
      this.$router.push({ name: 'passwordHelp' });
      this.leftBg = true;
    },
  },
};
</script>
