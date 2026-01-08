<template>
  <div class="row w-100 p-2 m-0 ">
    <div class="col-12 mb-1 h-auto">
      <div class="flex justify-between">
        <div class="m-0 opacity-100 text-1 w-100">
          <package-icon :package-info="updates"></package-icon>
          <span class="opacity-30 ml-1">Available Versions for </span>
          {{ updates.name }}
          <div class="absolute-right pt-1">
            <q-btn @click="closeMe" flat icon="close"></q-btn>
          </div>
          <nightly-package-warning class="mt-1" v-if="updates.buildType === 'nightly'"></nightly-package-warning>

          <q-separator class="mt-1"></q-separator>
        </div>
      </div>
    </div>
    <div class="col-12">
      <div class="row" v-if="!updates || !updates.versions || !updates.versions.length">
        <q-item>
          <q-item-label>
            <div class="row q-item-tile sublabel pt-1"><q-icon name="info" size="1.2rem" class="mr-2"></q-icon>No package version found</div>
          </q-item-label>
        </q-item>
      </div>
      <div class v-if="updates.versions && updates.versions && updates.versions.length">
        <div class="row">
          <div class="col-12 h-divide-bottom-dashed m-0" v-for="(update, index) in updates.versions" :key="index">
            <q-item class="p-1 pt-2 pb-2">
              <q-item-label>
                <div class="row q-item-tile label">
                  <div class="col-auto p-0 m-0">
                    <q-icon v-if="update.active" class="pr-1 animated bounceIn" size="1.8em" color="positive" name="check_circle" />
                    <package-version-icon v-if="!update.active" :version="update" class="mr-1"></package-version-icon>
                  </div>
                  <div class="col">
                    {{ update.versionName }}
                  </div>
                </div>
                <div>
                  <div class="row q-item-tile sublabel pt-1">
                    <div class="col-auto pr-1">Format:</div>
                    <div class="col">
                      <q-chip dense class="m-0">{{ update.targetFormat }}</q-chip>
                    </div>
                  </div>
                  <div class="row q-item-tile sublabel pt-1">
                    <div class="col-auto pr-1">Currently installed on this device:</div>
                    <div class="col">{{ update.active ? 'Yes' : 'No' }}</div>
                  </div>
                </div>
                <div class="row q-item-tile sublabel pt-1">
                  <div class="col-auto pr-1">Hash:</div>
                  <div class="col ellipsis">
                    <a no-caps class="p-0 m-0 text-primary" :href="'#/packages/?id=' + update.packageHash" @click.stop.prevent="$router.push({ name: 'packages', query: { id: update.packageHash } })">{{ update.packageHash }}</a>
                  </div>
                  <!-- <div class="col ellipsis">{{ update.packageHash }}</div> -->
                </div>
                <div>
                  <div v-if="update.commitBody" class="row q-item-tile sublabel pt-1" style="position: relative">
                    <div class="col-auto pr-1">Version Detail:</div>
                    <div
                      class="col"
                      :class="{
                        'pr-5 ellipsis': !showUpdateBody[update.version],
                      }"
                    >
                      {{ update.commitBody }}
                      <q-btn dense @click="toggleCommitBody(update)" class="absolute-right text-primary">
                        <q-icon
                          :class="{
                            'rotate-90': showUpdateBody[update.version],
                            'rotate-270': !showUpdateBody[update.version],
                          }"
                          name="chevron_left"
                          size="1.5rem"
                        />
                      </q-btn>
                    </div>
                  </div>
                </div>
                <div class="row q-item-tile sublabel pt-1">
                  <div class="col-auto pr-1">{{ update.builtAt && update.builtAt !== '//' ? 'Build date: ' : 'Date created: ' }}</div>
                  <div class="col ellipsis">{{ $date.formatDate(update.builtAt && update.builtAt !== '//' ? update.builtAt : update.createdAt, 'ddd MMM DD YYYY, h:mm:ss A') }}</div>
                </div>
                <div></div>
                <div class="row q-item-tile pt-1" v-if="!update.active">
                  <div class="col-12 ellipsis">
                    <q-btn @click="installUpdate(update)" flat color="secondary" icon="publish">&nbsp;&nbsp;Install this version</q-btn>
                  </div>
                </div>
              </q-item-label>
            </q-item>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import Vue from 'vue';
import { mapGetters } from 'vuex';
import PackageIcon from '../packages/PackageIcon';
import PackageVersionIcon from '../packages/PackageVersionIcon';
import NightlyPackageWarning from '../common/NightlyPackageWarning';
export default {
  components: { PackageIcon, PackageVersionIcon, NightlyPackageWarning },
  name: 'DevicePackageVersions',
  props: {
    device: {
      type: Object,
      default: () => {
        return {};
      },
    },
    selectedPackage: {
      type: Object,
      default: () => {
        return {};
      },
    },
    largeHistoryView: {
      type: Boolean,
      default: true,
    },
  },
  data() {
    return {
      showUpdateBody: {},
    };
  },
  computed: {
    ...mapGetters({
      packageGroupsInAllSources: 'packages/packageGroupsInAllSources',
    }),
    updates() {
      this.selectedPackage.versions = this.selectedPackage.versions.map((m) => {
        const attr = this.device.directorAttributes || {};
        m.active = JSON.stringify(attr.primary || {}).indexOf(m.uuid) != -1 || JSON.stringify(attr.secondary || {}).indexOf(m.uuid) != -1;
        return m;
      });
      return this.selectedPackage;
    },
  },
  methods: {
    closeMe() {
      this.$emit('close', null);
    },
    installUpdate(toVersion) {
      const toPackage = toVersion;
      const attr = this.device.directorAttributes || {};
      console.log('attr', attr, this.device);
      const ecus = [attr.primary || {}].concat(attr.secondary || {});
      this.$q
        .dialog({
          title: `Install Package Version`,
          message: `You are about to install version ${toVersion.versionName} on ${this.device.deviceName}. Select a component to install it on and click proceed to continue or cancel to abort.`,
          color: 'default',
          icon: 'publish',
          ok: {
            flat: true,
            cssClass: 'proceed',
            label: 'Proceed',
            color: 'secondary',
          },
          cancel: {
            label: 'Cancel',
            flat: true,
          },
          persistent: true,
          options: {
            type: 'radio',
            model: null,
            // inline: true
            items: _.filter(ecus, (f) => _.includes(toVersion.hardwareIds, f.hardwareId)).map((m) => ({ label: m.hardwareId, value: { ...m, package: toVersion }, color: 'primary' })),
          },
        })
        .onOk((selectedEcu) => {
          if (!selectedEcu) {
            return this.$q.dialog({
              title: 'Installation Canceled',
              message: 'You did not select any component to install',
              ok: {
                label: 'Dismiss',
                flat: true,
                color: 'primary',
              },
            });
          }
          const index = ecus.findIndex((i) => i.id === selectedEcu.id);
          ecus.splice(index, 1, selectedEcu);
          this.$events.$emit('dialogs:create-device-update:request', {
            update: {
              devices: [{ ...this.device, ecus }],
            },
            selectedEcus: [selectedEcu],
          });
        });
    },
    toggleCommitBody(update) {
      Vue.set(this.showUpdateBody, update.version, !this.showUpdateBody[update.version]);
    },
  },
};
</script>
