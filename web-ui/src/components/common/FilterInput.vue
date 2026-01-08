<template>
  <q-input dense :rounded="rounded" outlined class="m-0" color="none" :placeholder="placeholder" inverted v-model="content" @input="handleInput">
    <template v-slot:prepend>
      <q-icon :name="icon" />
    </template>
    <template v-slot:append v-if="content && content.length">
      <q-icon
        name="close"
        @click="
          content = '';
          handleInput('');
        "
        class="cursor-pointer"
      />
    </template>
  </q-input>
</template>

<script>
export default {
  name: 'FilterInput',
  props: {
    value: {
      type: String,
      default: null,
    },
    icon: {
      type: String,
      default: 'filter_alt',
    },
    placeholder: {
      type: String,
      default: 'Filter',
    },
    rounded: {
      type: Boolean,
      default: true,
    },
    debounceRate: {
      type: Number,
      default: 200,
    },
  },
  data() {
    return {
      content: this.value,
      debounceTimer: null,
    };
  },
  methods: {
    handleInput(e) {
      // debounce the input
      clearTimeout(this.debounceTimer);
      this.debounceTimer = setTimeout(() => {
        this.$emit('input', this.content);
      }, this.debounceRate);
    },
  },
};
</script>
