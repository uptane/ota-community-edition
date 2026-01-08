<template>
  <div>
    <password-input
      outlined
      @focus="
        focused = true;
        error.message = '';
      "
      @blur="
        focused = false;
        error.message = '';
      "
      type="password"
      :value="password"
      @change="handleChange"
      @input="handleInput"
      :label="label"
      color="secondary"
      :rules="[(val) => passwordMatchAll() || ``]"
    />
    <div style="margin-top: -0.8rem;" class=" password-prompt" v-if="showCompliance">
      <div
        :class="{
          'text-positive': passwordMatchCase('uppercase'),
          'text-negative': !passwordMatchCase('uppercase'),
        }"
      >
        <span class="check-mark">{{ passwordMatchCase('uppercase') ? '✓' : '×' }}</span> At least one uppercase letter (ABC...Z)
      </div>
      <div
        :class="{
          'text-positive': passwordMatchCase('lowercase'),
          'text-negative': !passwordMatchCase('lowercase'),
        }"
      >
        <span class="check-mark">{{ passwordMatchCase('lowercase') ? '✓' : '×' }}</span> At least one lowercase letter (abc...z)
      </div>

      <div
        :class="{
          'text-positive': passwordMatchCase('symbol'),
          'text-negative': !passwordMatchCase('symbol'),
        }"
      >
        <span class="check-mark">{{ passwordMatchCase('symbol') ? '✓' : '×' }}</span> At least one Symbol (!@#$%^&amp;*&gt;!&lt;/\?)
      </div>
      <div
        :class="{
          'text-positive': passwordMatchCase('number'),
          'text-negative': !passwordMatchCase('number'),
        }"
      >
        <span class="check-mark">{{ passwordMatchCase('number') ? '✓' : '×' }}</span> At least one number (1234567890)
      </div>
      <div
        :class="{
          'text-positive': passwordMatchCase('minimum'),
          'text-negative': !passwordMatchCase('minimum'),
        }"
      >
        <span class="check-mark">{{ passwordMatchCase('minimum') ? '✓' : '×' }}</span> Minimum of 8 characters long
      </div>
    </div>
  </div>
</template>

<script>
import PasswordInput from './PasswordInput';
export default {
  name: 'PasswordComponent',
  components: {
    PasswordInput,
  },
  props: {
    label: {
      type: String,
      default: 'Password *',
    },
  },
  data() {
    return {
      password: '',
      focused: false,
      showCompliance: false,
      error: {
        message: '',
      },
    };
  },
  methods: {
    handleInput(e) {
      this.password = e;
      this.$emit('input', e);
    },
    handleChange(e) {
      this.password = e;
      this.$emit('change', e);
    },
    passwordMatchCase(type) {
      let match = false;
      switch (type) {
        case 'uppercase':
          match = this.password.match(/[A-Z]/);
          break;
        case 'lowercase':
          match = this.password.match(/[a-z]/);
          break;
        case 'number':
          match = this.password.match(/[0-9]/);
          break;
        case 'symbol':
          match = this.password.match(/[@#$-/:-?{-~!"^_`\\\[\]]/);
          break;
        case 'minimum':
          match = this.password.length >= 8;
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
  watch: {
    focused() {
      if (!this.focused) {
        setTimeout(() => {
          this.showCompliance = this.focused;
        }, 2000);
      } else {
        this.showCompliance = this.focused;
      }
    },
  },
};
</script>
