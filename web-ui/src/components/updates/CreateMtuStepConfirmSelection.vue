<template>
  <div>
    <div class="text-h6 text-center pb-1">Summary of your selection for this update</div>
    <p>
      Please review your selection before you continue. You can go back to make changes if needed.
    </p>
    <p>
      <template v-if="isLockbox"
        >Update name: <i>{{ updateName }}</i></template
      >
      <i v-else-if="immediate">Update will be queued to start as soon as possible once you click "Finish".</i>
      <i v-else
        >Update will be scheduled to start at <b>{{ schedule }}</b> once you click "Finish".</i
      >
    </p>
    <q-list class="mxh-60vh overflow-auto q-ma-none q-pa-none">
      <q-item :key="ecu.id" class="" v-for="ecu in ecus">
        <q-item-section class="sublabel">
          <div class="ellipsis">
            <q-icon :color="ecu.package ? 'primary' : 'negative'" size="1.4em" :name="ecu.package ? 'task_alt' : 'not_interested'" /><span class="q-pl-xs">{{ ecu.hardwareId }}</span>
          </div>
          <div class="flex" v-if="ecu.package">
            <div class="pr-1">
              <package-icon :package-info="ecu.package" size="1.4rem" />
            </div>
            <div>
              <div class="opacity-50">Package: {{ ecu.package.name }}</div>
              <div class="opacity-50">Version: {{ ecu.package.commitSubject || ecu.package.version }}</div>
            </div>
          </div>
          <div v-else class="flex pl-2">
            <span class="opacity-40"> Skipped</span>
          </div>
        </q-item-section>
      </q-item>
    </q-list>
  </div>
</template>

<script>
import PackageIcon from '../packages/PackageIcon.vue';
export default {
  components: { PackageIcon },
  name: 'CreateMtuStepConfirmSelection',
  props: {
    ecus: {
      type: Array,
      default: () => {
        return [];
      },
    },
    immediate: {
      type: Boolean,
      required: true,
    },
    schedule: {
      type: String,
      default: '',
    },
    isLockbox: {
      type: Boolean,
      default: false,
    },
    updateName: {
      type: String,
      default: '',
    },
  },
};
</script>
