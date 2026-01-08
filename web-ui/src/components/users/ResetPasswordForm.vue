<template>
  <div id="reset-password" class="register-form p-5 flex h-100">
    <account-creation-header v-if="$q.screen.lt.md"></account-creation-header>
    <div class="justify-center text-center pt-1 mxh-10em mt-0 m-auto" v-if="finish">
      <div class="p-2 success-message">Password successfully changed</div>
      <q-btn class="p-0 mt-3 full-width" @click="gotoLogin" color="primary">Sign in</q-btn>
    </div>
    <div
      v-if="!finish"
      class
      :class="{
        'm-auto': true, //$q.screen.gt.md
      }"
      style="max-width: 25rem"
    >
      <div class="justify-center text-center pt-1 mxh-10em m-auto" v-if="error">
        <div class="p-2 error-message">{{ error.message }}</div>
      </div>
      <div class="">
        <h4 class="text-center pt-0 mt-1">Reset Your Password</h4>
        <q-form @submit="onSubmit" @reset="onReset" class="q-gutter-md">
          <!-- <q-input
            outlined
            @focus="focused['code']=true"
            @blur="focused['code']=false"
            type="password"
            v-model="loginModel.code"
            label="Verification code "
            color="secondary" 
             lazy-rules
        :rules="[
          val => val !== null && val !== '' || 'Enter the code you received',
          ]">
            <template v-slot:prepend>
              <q-icon :color="focused['code']?'secondary':'default'" name="lock"/>
            </template>
          </q-input> -->
          <password-input outlined @focus="focused['code'] = true" @blur="focused['code'] = false" type="password" v-model="loginModel.code" label="Verification code " color="secondary" lazy-rules :rules="[(val) => (val !== null && val !== '') || 'Enter the code you received']" />
          <password-component v-model="loginModel.newPassword"></password-component>
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
          <!-- <q-input
            outlined
            @focus="focused['pwd']=true"
            @blur="focused['pwd']=false"
            type="password"
            v-model="loginModel.newPassword"
            label="New password "
            color="secondary"
          lazy-rules
        :rules="[
          val => passwordMatchAll() || ``
          ]"
          >
            <template v-slot:prepend>
              <q-icon :color="focused['pwd']?'secondary':'default'" name="lock"/>
            </template>
          </q-input>
          <div style="margin-top: -0.8rem;" v-if="focused['pwd']">
          <div :class="{
            'text-positive': passwordMatchCase('uppercase'),
            'text-negative': !passwordMatchCase('uppercase'),
          }"> <span class="check-mark">{{passwordMatchCase('uppercase')?'✓':'×'}}</span> At least one uppercase letter (ABCDEF)</div>
         <div :class="{
            'text-positive': passwordMatchCase('lowercase'),
            'text-negative': !passwordMatchCase('lowercase'),
          }"> <span class="check-mark">{{passwordMatchCase('lowercase')?'✓':'×'}}</span> At least one lowercase letter (abcdef)</div>
          
          <div :class="{
            'text-positive': passwordMatchCase('symbol'),
            'text-negative': !passwordMatchCase('symbol'),
          }"> <span class="check-mark">{{passwordMatchCase('symbol')?'✓':'×'}}</span> At least one Symbol (!@#$%^&amp;*) </div>
           <div :class="{
            'text-positive': passwordMatchCase('number'),
            'text-negative': !passwordMatchCase('number'),
          }"> <span class="check-mark">{{passwordMatchCase('number')?'✓':'×'}}</span> At least one number (1234567890) </div>
          <div :class="{
            'text-positive': passwordMatchCase('minimum'),
            'text-negative': !passwordMatchCase('minimum'),
          }"> <span class="check-mark">{{passwordMatchCase('minimum')?'✓':'×'}}</span> Minimum of 8 characters long</div>
          
          </div>
          <q-input
            outlined
            @focus="focused['pwd2']=true"
            @blur="focused['pwd2']=false"
            type="password"
            v-model="loginModel.password2"
            label="Same password again"
            color="secondary"
          lazy-rules
        :rules="[
          val => val !== null && val !== '' || 'Enter the same password',
          val => ( val === loginModel.newPassword) || 'It must match what you entered above.'
          ]"
          >
            <template v-slot:prepend>
              <q-icon :color="focused['pwd2']?'secondary':'default'" name="lock"/>
            </template>
          </q-input> -->

          <div>
            <q-btn :loading="loading" class="full-width" label="Reset password" type="submit" color="primary">
              <template v-slot:loading>
                <q-spinner-gears class="on-left" /> Processing...
              </template>
            </q-btn>
            <q-btn @click="gotoLogin" flat no-caps class="mt-2 full-width" label="Sign in" color="primary"> </q-btn>
          </div>
        </q-form>
      </div>
    </div>
  </div>
</template>

<script>
import { AuthService } from '../../services/auth.service';
import PasswordComponent from './PasswordComponent';
import PasswordInput from './PasswordInput';
import AccountCreationHeader from './AccountCreationHeader';

export default {
  name: 'ResetPasswordFormComponent',
  components: {
    PasswordInput,
    PasswordComponent,
    AccountCreationHeader,
  },
  data() {
    return {
      loginModel: {
        code: '',
        oldPassword: '',
        newPassword: '',
        password2: '',
        remember: false,
        acceptTerms: false,
      },
      loading: false,
      finish: false,
      error: '',
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
      AuthService.resetPassword(this.loginModel)
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
    onCancel() {},

    onFinish() {
      this.finish = true;
    },
    gotoLogin() {
      this.$router.replace({ name: 'login' });
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
