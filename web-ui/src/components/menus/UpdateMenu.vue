<template>
  <div>
    <q-list link separator class="p-0" style="min-width: 10em; max-height: 35em;">
      <!-- <q-list-header class="bg-grey text-black text-center p-0 m-0">
        <div class="pt-1 pb-1">{{device.deviceName}}</div>
      </q-list-header>-->
      <q-item class="device-update" v-close-popup @click.native="$emit('create-update', false)">
        <q-item-section avatar>
          <img style="width:2rem" src="statics/svg/icons/som.svg" />
        </q-item-section>
        <q-item-label>
          <div>Device update</div>
        </q-item-label>
      </q-item>
      <q-item class="fleet-update" v-close-popup @click.native="$emit('create-update', true)">
        <q-item-section avatar>
          <img style="width:2rem" src="statics/svg/icons/soc-fleet.svg" />
        </q-item-section>
        <q-item-label>
          <div>Fleet update</div>
        </q-item-label>
      </q-item>
    </q-list>
  </div>
</template>

<script>
export default {
  components: {},
  props: {
    device: {
      type: Object,
      default: () => {
        return {};
      },
    },
  },
  methods: {
    // createUpdate(isFleet) {
    //   this.$refs.createDlg.show = true;
    //   this.$refs.createDlg.isFleetUpdate = isFleet;
    // }
  },
  computed: {},
};
</script>
