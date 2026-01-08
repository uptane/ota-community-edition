<template>
  <div class="q-item-tile ">
    <div class="text-h6 mb-2  text-center">{{ title }}</div>
    <div class="mb-1 mxw-40em ml-auto mr-auto">
      <div v-if="loading" class="flex flex-center text-center items-center faded"><q-spinner-hourglass size="2em" class="mr-2"></q-spinner-hourglass> Loading...</div>
      <div class="text sublabel" v-else>
        <div class="pl-1 h-divide-bottom-dashed">
          <q-btn flat color="primary" :icon="value && value.length < ecus.length ? 'done_all' : 'remove_done'" :label="value && value.length < ecus.length ? 'Select all' : 'Select none'" @click="value && value.length < ecus.length ? updateSelection(ecus) : updateSelection([])" />
          <span class="faded">({{ value.length }} of {{ ecus.length }} selected)</span>
        </div>

        <q-list dense class="mxh-20em overflow-y-auto">
          <template v-for="ecu in ecus">
            <q-item v-if="!(packageUpload && ecu.hardwareId === 'docker-compose')" :key="ecu.id" clickable v-ripple class="pb-0 pt-0" tag="label">
              <q-item-section side top>
                <q-checkbox @input="updateSelection" :value="value" :val="ecu"></q-checkbox>
              </q-item-section>
              <q-item-section>
                <span class="faded">{{ ecu.hardwareId }}</span>
              </q-item-section>
            </q-item>
          </template>
          <q-item v-if="showAllHardwareIds && packageUpload" key="custom_ecu" class="pb-0 pt-0 mb-2" tag="label">
            <q-item-section side top>
              <q-checkbox v-model="addCustomEcu"></q-checkbox>
            </q-item-section>
            <q-item-section>
              <q-form v-if="addCustomEcu" ref="customEcuForm" @submit="addCustomHardwareId">
                <q-input ref="customEcuInput" outlined dense v-model="customEcuHardwareId" label="Hardware ID" class="q-mt-sm" :rules="[(val) => val.length > 0 || 'Hardware ID is required']">
                  <template v-slot:append>
                    <q-btn round dense flat icon="add" type="submit" @click="addCustomHardwareId" />
                  </template>
                </q-input>
              </q-form>
              <span v-else class="faded"> <q-icon name="add" size="1.5em"></q-icon> add custom component </span>
            </q-item-section>
          </q-item>
        </q-list>
        <div class=" h-divide-bottom-dashed"></div>
        <q-btn flat color="primary" v-if="userHardwareIds.length > 0 && (lockbox || packageUpload)" :icon="showAllHardwareIds ? 'expand_less' : 'expand_more'" @click="showAllHardwareIds = !showAllHardwareIds">Show {{ showAllHardwareIds ? 'less' : 'more' }}</q-btn>
      </div>
    </div>
  </div>
</template>

<script>
import _ from 'lodash';
import { mapActions, mapGetters } from 'vuex';
export default {
  components: {},
  name: 'EcusSelector',
  props: {
    value: {
      type: Array,
      default: () => [],
    },
    devices: {
      type: Array,
      default: () => [],
    },
    lockbox: {
      type: Boolean,
      default: false,
    },
    packageUpload: {
      type: Boolean,
      default: false,
    },
    allowComponentCreation: {
      type: Boolean,
      default: false,
    },
    title: {
      type: String,
      default: 'Select components',
    },
  },
  data() {
    return {
      showAllHardwareIds: false,
      loading: false,
      customHardwareIds: [],
      addCustomEcu: false,
      customEcuHardwareId: '',
    };
  },
  computed: {
    ...mapGetters({
      packagesInAllSources: 'packages/packagesInAllSources',
      userHardwareIds: 'hardware/hardwareIds',
    }),
    ecus() {
      let ecus = this.value || [];
      if (this.lockbox || this.packageUpload) {
        ecus = ecus.concat(this.availableHardwareIds.map((a) => ({ hardwareId: a })));
      } else {
        this.devices.forEach((device) => {
          ecus = ecus.concat(device.ecus);
        });
      }
      const uniqEcus = _.sortBy(_.uniqBy(ecus.filter((f) => !!f), 'hardwareId'), ['hardwareId']);
      if ((this.lockbox || this.packageUpload) && this.allowComponentCreation && this.showAllHardwareIds) {
        uniqEcus.concat(this.customHardwareIds);
      }
      return uniqEcus;
    },
    availableHardwareIds() {
      // Return all hardware ids if user hardware ids is empty
      if (!this.userHardwareIds || this.userHardwareIds.length < 1) {
        return this.tdxHardwareIds;
      }
      // Return all hardware ids if showAllHardwareIds is true or user hardware ids  otherwise
      return this.showAllHardwareIds ? this.userHardwareIds.concat(this.tdxHardwareIds) : this.userHardwareIds;
    },
    tdxHardwareIds() {
      return _.uniq(
        _.reduce(
          this.packagesInAllSources,
          (prev, curr, currIndex) => {
            return prev.concat(curr.hardwareIds);
          },
          [],
        ),
      );
    },
  },
  mounted() {
    if (!this.userHardwareIds || this.userHardwareIds.length < 1) {
      this.loading = true;
      this.fetchHardwareIds()
        .then((hwids) => {})
        .catch((err) => {
          // console.log(err);
        })
        .finally(() => {
          this.loading = false;
        });
    }
  },
  methods: {
    ...mapActions({
      fetchHardwareIds: 'hardware/fetchHardwareIds',
    }),
    updateSelection(e) {
      this.$emit('input', e);
    },
    addCustomHardwareId(event) {
      event.preventDefault();
      event.stopPropagation();
      this.$refs.customEcuForm
        .validate()
        .then((valid) => {
          if (valid) {
            this.$emit('input', this.value.concat([{ hardwareId: this.customEcuHardwareId }]));
            this.customHardwareIds.push({ hardwareId: this.customEcuHardwareId });
            this.addCustomEcu = false;
            this.customEcuHardwareId = '';
            setTimeout(() => {
              this.addCustomEcu = true;
              this.$refs.customEcuInput.focus();
            }, 500);
          } else {
          }
        })
        .catch((err) => {
          this.addCustomEcu = true;
        });
    },
  },
  watch: {
    addCustomEcu(val) {
      if (val) {
        this.$nextTick(() => {
          this.$refs.customEcuInput.focus();
        });
      }
    },
  },
};
</script>
