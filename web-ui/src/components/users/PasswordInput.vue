<template>
  <q-input
    outlined
    @focus="onFocus"
    @blur="onBlur"
    :type="inputType"
    :label="label"
    :aria-autocomplete="autocomplete"
    :autocomplete="autocomplete"
    :aria-label="label"
    :autofocus="autofocus"
    :id="id"
    :color="color"
    :lazy-rules="lazyRules"
    :rules="rules"
    :value="content"
    @input="handleInput"
    @change="handleChange"
    class="password"
  >
    <template v-slot:prepend>
      <q-icon :color="focused ? 'secondary' : 'default'" name="lock" />
    </template>
    <template v-slot:append>
      <div>
        <q-btn flat dense @click="showPassword = !showPassword" :icon="showPassword ? 'far fa-eye-slash' : 'far fa-eye'" />
        <tooltip>{{ tooltipText }}</tooltip>
      </div>
    </template>
  </q-input>
</template>

<script>
import { QInput } from 'quasar';
import Tooltip from '../common/Tooltip.vue';
export default {
  components: { Tooltip },
  extends: QInput,
  props: ['id', 'autocomplete', 'autofocus'],
  data() {
    return {
      focused: false,
      content: '',
      showPassword: false,
    };
  },
  computed: {
    inputType() {
      return this.showPassword ? 'text' : 'password';
    },
    tooltipText() {
      return this.showPassword ? 'Hide password' : 'Show password';
    },
  },
  methods: {
    onFocus(ev) {
      this.focused = true;
      this.$emit('focus', ev);
    },
    onBlur(ev) {
      this.focused = false;
      this.$emit('blur', ev);
    },
    handleInput(e) {
      this.content = e;
      this.$emit('input', this.content);
    },
    handleChange(e) {
      this.content = e.target.value;
      this.$emit('change', this.content);
    },
  },
};
</script>
