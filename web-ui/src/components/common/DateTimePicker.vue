<template>
  <div class="row  justify-end q-card">
    <q-date v-model="dateModel" today-btn flat minimal class="light-card" mask="YYYY-MM-DD HH:mm" :no-unset="noUnset" />
    <q-time minimal now-btn v-model="dateModel" mask="YYYY-MM-DD HH:mm" :no-unset="noUnset" flat />
  </div>
</template>

<script>
export default {
  name: 'DateTimePicker',
  props: {
    value: {
      type: String,
      default: '',
    },
    noUnset: {
      type: Boolean,
      default: true,
    },
  },
  computed: {
    dateModel: {
      get() {
        return this.value;
      },
      set(v) {
        this.$emit('input', v);
      },
    },
  },
};
</script>
