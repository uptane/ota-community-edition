<template>
  <div class>
    <h4 class="text-center pt-0 mt-5">Change Your Password</h4>
    <q-form action="/" @submit="onSubmit" @reset="onReset" class="q-gutter-md">
      <password-input outlined @focus="focused['opwd'] = true" @blur="focused['opwd'] = false" type="password" autofocus v-model="loginModel.oldPassword" label="Current password " color="secondary" lazy-rules :rules="[(val) => (val !== null && val !== '') || 'Enter your current password']" />

      <password-component v-model="loginModel.newPassword" label="New password "></password-component>
      <password-input
        outlined
        @focus="focused['pwd2'] = true"
        @blur="focused['pwd2'] = false"
        type="password"
        v-model="loginModel.password2"
        label="Same password again"
        color="secondary"
        lazy-rules
        :rules="[(val) => (val !== null && val !== '') || 'Enter the same password', (val) => val === loginModel.newPassword || 'It must match what you entered above.']"
      />

      <div>
        <q-btn :loading="loading" class="full-width" label="Change password" type="submit" color="primary">
          <template v-slot:loading>
            <q-spinner-gears class="on-left" />Processing...
          </template>
        </q-btn>
        <q-btn @click="onCancel" flat class="mt-2 full-width" label="Cancel" color="primary"></q-btn>
      </div>
    </q-form>
  </div>
</template>

<script>
import { AuthService } from '../../services/auth.service';
import PasswordComponent from './PasswordComponent';
import PasswordInput from './PasswordInput';
export default {
  name: 'ChangePasswordMinFormComponent',
  components: {
    PasswordInput,
    PasswordComponent,
  },
  data() {
    return {
      loginModel: {
        oldPassword: '',
        newPassword: '',
        password2: '',
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
    onSubmit() {
      this.loading = true;
      AuthService.changePassword(this.loginModel)
        .then((result) => {
          this.loading = false;
          this.finish = true;
          this.onFinish();
          //   this.$router.go();
        })
        .catch((err) => {
          this.loading = false;
          this.error = err;
          logError(err);
        });
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
    onCancel() {
      this.$emit('on:cancel');
    },
    onFinish() {
      this.$emit('on:finish');
    },
  },
};
</script>
