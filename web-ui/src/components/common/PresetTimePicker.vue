<template>
  <div class="p-1 ">
    <div class="text-1  h-divide-bottom">Preset Time Range</div>
    <div v-for="(option, index) in options" :key="'preset_opion_' + index">
      <q-radio :value="value" :val="option.value" :label="option.label" @input="$emit('input', option.value)" color="primary" />
    </div>
    <div>
      <q-radio :value="value" :val="customValue" label="Custom" @input="$emit('input', customValue)" color="primary" />
      <q-btn
        flat
        icon="event"
        @click="
          $emit('input', customValue);
          $emit('custom-picker');
        "
      ></q-btn>
    </div>
  </div>
</template>

<script>
export default {
  name: 'PresetTimePicker',
  props: ['value', 'customValue', 'options'],
  data() {
    return {
      presetRange: 60 * 60 * 1000,
    };
  },
  computed: {},
  watch: {},
};
</script>
