<template>
  <span>
    {{ $date.formatDate(date, x_format) }}
  </span>
</template>

<script>
export default {
  name: 'FormattedDate',
  props: {
    date: {
      default: '',
    },
    withTime: {
      type: Boolean,
      default: true,
    },
    format: {
      type: String,
      default: null,
    },
  },
  computed: {
    x_format() {
      return this.format ? this.format : this.withTime ? 'MM/DD/YYYY h:mm:ss A' : 'MM/DD/YYYY';
    },
  },
};
</script>
