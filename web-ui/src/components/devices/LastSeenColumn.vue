<template>
  <div class="q-item-tile sublabel">
    <div class="opacity-50" v-if="row.deviceStatus === 'NotSeen'">
      Never
    </div>
    <formatted-date v-else :date="lastSeen"></formatted-date>
  </div>
</template>

<script>
import FormattedDate from 'src/components/common/FormattedDate.vue';
export default {
  components: {
    FormattedDate,
  },
  name: 'LastSeenColumn',
  props: {
    row: {
      type: Object,
      required: true,
    },
  },
  data() {
    return {
      lastSeen: '',
    };
  },
  methods: {
    setLastSeen() {
      this.lastSeen = this.row.lastSeen;
    },
  },

  mounted() {
    this.setLastSeen();
  },
};
</script>
