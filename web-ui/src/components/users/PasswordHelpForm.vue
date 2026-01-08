<template>
  <div id="password-help" class="login-form p-5 flex h-100">
    <account-creation-header v-if="$q.screen.lt.lg"></account-creation-header>
    <div
      class=""
      :class="{
        'm-auto': true, //$q.screen.gt.md
        'mt-0': $q.screen.lt.lg,
      }"
      style="max-width: 25rem"
    >
      <h1 class="text-center pt-0 mt-0">Password Help</h1>
      <div v-if="linkSent">
        <p>Password reset instruction was sent to your email.</p>
      </div>
      <div v-else>
        <q-form style="max-width: 18rem; width: 100%" @submit="onSubmit" @reset="onReset" class="q-gutter-md">
          <q-input
            outlined
            @focus="focused['email'] = true"
            @blur="focused['email'] = false"
            v-model="loginModel.username"
            label="Email "
            class="mnw-20em"
            color="secondary"
            lazy-rules
            :rules="[(val) => (val !== null && val !== '') || 'Enter your registered email address', (val) => val.match(emailRegex) || 'This is not a valid email address format']"
          >
            <template v-slot:prepend>
              <q-icon :color="focused['email'] ? 'secondary' : 'default'" name="person" />
            </template>
          </q-input>
          <div>
            <q-btn :loading="loading" class="full-width" label="Continue" type="submit" color="primary">
              <template v-slot:loading>
                <q-spinner-gears class="on-left" /> Processing...
              </template>
            </q-btn>
          </div>
        </q-form>
      </div>
      <div class="text-center mt-2">
        <q-btn @click="gotoRegister" flat no-caps color="primary">Create new account</q-btn>
        <q-btn @click="gotoLogin" flat no-caps color="primary">Sign in</q-btn>
      </div>
    </div>
  </div>
</template>

<script>
import { AuthService } from '../../services/auth.service';
import AccountCreationHeader from './AccountCreationHeader';

export default {
  name: 'PasswordHelp',
  components: {
    AccountCreationHeader,
  },
  data() {
    return {
      loginModel: {
        username: '',
        password: '',
        remember: false,
      },
      focused: {},
      loading: false,
      linkSent: false,
      emailRegex: new RegExp(
        /(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|"(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21\x23-\x5b\x5d-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])*")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\[(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21-\x5a\x53-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])+)\])/,
      ),
      passwordStrength: {
        strong: new RegExp('^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[!@#\$%\^&\*])(?=.{8,})'),
        medium: new RegExp('^(((?=.*[a-z])(?=.*[A-Z]))|((?=.*[a-z])(?=.*[0-9]))|((?=.*[A-Z])(?=.*[0-9])))(?=.{6,})'),
      },
    };
  },
  methods: {
    onReset() {},
    onSubmit() {
      this.loading = true;
      AuthService.forgotPassword(this.loginModel)
        .then((result) => {
          this.loading = false;
          this.linkSent = true;
          // this.$router.push({ name: 'resetPassword' });
          //   this.$router.go();
        })
        .catch((err) => {
          console.log('ERR', err);
          this.loading = false;
        });
    },
    gotoRegister() {
      this.$emit('goto:register', '');
    },
    gotoLogin() {
      this.$emit('goto:login', '');
    },
  },
};
</script>
