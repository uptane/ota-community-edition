<template>
  <div class="">
    <div class="row justify-center items-center q-gutter-sm">
      <div v-for="index in length" :key="'input_div_' + index" class="col-auto " style="text-align: center;">
        <q-input
          :ref="'input_' + (index - 1)"
          outlined
          style="width: 1.5em; text-align: center; font-size: 3em;"
          class="text-center"
          :autofocus="index === 1"
          :maxlength="1"
          :readonly="disabled"
          :value="value[index - 1]"
          :color="color"
          :input-class="{
            'text-positive': success,
            'text-negative': error,
            'text-uppercase': uppercase,
          }"
          :class="{
            'code-error': error,
            'code-success': success,
          }"
          @focus="selectChar(index - 1)"
          @keydown="onKeyDown(index - 1, $event)"
          @paste="onPaste(index - 1, $event)"
          input-style="text-transform: uppercase; text-align: center;"
        >
        </q-input>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'CodeInput',
  props: {
    value: {
      type: String,
      default: '',
    },
    length: {
      type: Number,
      default: 6,
    },
    disabled: {
      type: Boolean,
      default: false,
    },
    error: {
      type: Boolean,
      default: false,
    },
    success: {
      type: Boolean,
      default: false,
    },
    uppercase: {
      type: Boolean,
      default: true,
    },
    acceptedCharsRegExp: {
      type: RegExp,
      default: () => {
        // return /^[a-zA-Z]$/ // only letters
        // return /^[a-fA-F0-9]$/ // only hex
        return /^[a-zA-Z0-9]$/; // letters and numbers
      },
    },
    complete: {
      type: Boolean,
      default: true,
    },
  },
  data() {
    return {};
  },
  computed: {
    valueArray: {
      get() {
        const spaces = Array(this.length)
          .fill(' ')
          .join('');
        return (this.value || spaces).split('');
      },
      set(val) {
        this.$emit('input', val.join('').trim());
      },
    },
    color() {
      if (this.error) {
        return 'negative';
      } else if (this.success) {
        return 'positive';
      } else {
        return 'default';
      }
    },
  },
  methods: {
    setChar(index, val) {
      if (this.uppercase) {
        val = val.toUpperCase();
      }
      this.valueArray[index] = val;
      this.$nextTick(() => {
        this.focusInputAtIndex(index + 1);
        this.onChange();
      });
    },
    focusNext(index) {
      if (index < this.length - 1) {
        this.$refs['input_' + (index + 2)][0].focus();
      }
    },
    focusInputAtIndex(index) {
      if (index >= 0 && index < this.length) {
        this.$refs['input_' + index][0].focus();
      }
    },
    selectChar(index) {
      if (this.valueArray[index] === ' ') {
        return;
      }
      this.$nextTick(() => {
        this.$refs['input_' + index][0].select();
      });
    },
    onPaste(index, event) {
      // if pasting from clipboard, get the clipboard data and set the value
      event.preventDefault();
      const pastedData = (event.clipboardData || window.clipboardData).getData('text/plain');
      if (pastedData.length === this.length) {
        pastedData.split('').forEach((char, i) => {
          if (char.match(this.acceptedCharsRegExp)) {
            this.setChar(i, char);
          }
        });
      }
      return;
    },
    onKeyDown(index, event) {
      // if pasting from clipboard, get the clipboard data and set the value
      if (event.ctrlKey || event.metaKey) {
        return;
      }

      if (event.key === 'Backspace') {
        // if key is backspace, clear the input and focus the previous input
        event.preventDefault();
        this.valueArray[index] = ' ';
        this.onChange();
        this.$nextTick(() => {
          if (index > 0) this.focusInputAtIndex(index - 1);
        });
      } else if (event.key === 'ArrowLeft') {
        // if key is left arrow, focus the previous input
        event.preventDefault();
        this.$nextTick(() => {
          if (index > 0) {
            this.focusInputAtIndex(index - 1);
          }
        });
      } else if (event.key === 'ArrowRight') {
        // if key is right arrow, focus the next input
        event.preventDefault();
        if (this.valueArray[index + 1] === ' ') {
          return;
        }
        this.$nextTick(() => {
          if (index < this.length - 1) this.focusInputAtIndex(index + 1);
        });
      } else if (event.key === 'Enter') {
        // if key is enter, emit the value
        event.preventDefault();
        this.$emit('enter', this.value);
      } else if (event.code === 'Delete') {
        // if key is delete, clear the input and focus the next input
        event.preventDefault();
        this.valueArray[index] = '';
        this.$nextTick(() => {
          if (index < this.length - 1) this.focusInputAtIndex(index + 1);
        });
      } else if (event.code === 'Space') {
        // if key is space, emit the value
        event.preventDefault();
        this.$emit('enter', this.value);
      } else if (event.key.match(this.acceptedCharsRegExp)) {
        // if key is alphanumeric, set the input and focus the next input
        event.preventDefault();
        this.setChar(index, event.key);
      } else {
        event.preventDefault();
      }
    },
    onChange() {
      let val = this.valueArray.join('');
      this.$emit('input', val);
      this.$nextTick(() => {
        if (this.value.length === this.length && this.valueArray.every((e) => e.match(this.acceptedCharsRegExp))) {
          this.$emit('update:complete', true);
        } else {
          this.$emit('update:complete', false);
        }
      });
    },
  },
  mounted() {},
  //   watch: {
  //     valueArray: {
  //       handler: (val, oldVal) => {
  //         debugger;
  //         this.onChange();
  //       },
  //       deep: true
  //     }
  //   }
};
</script>

<style lang="scss">
.code-error {
  &.q-field--outlined .q-field__control:before {
    border: 2px solid $negative !important;
  }
}
.code-success {
  &.q-field--outlined .q-field__control:before {
    border: 2px solid $positive !important;
  }
}
</style>
