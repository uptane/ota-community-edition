<template>
  <div>
    <q-dialog v-model="show" @ok="onOk" @cancel="onCancel" @show="onShow" @hide="onHide" no-backdrop-dismiss>
      <q-card class="p-2 mnw-30em">
        <q-card-section header>
          <div class="no-shadow">
            <h4 class="m-0">
              {{ (fleet || {}).id ? 'Rename' : 'Add new' }} fleet
              <q-icon name="left"></q-icon>
            </h4>
          </div>
        </q-card-section>

        <q-card-section class="pb-2">
          <div>
            <q-input label="Fleet name" @blur="$v.existing.groupName.$touch" @keyup.enter="onOk" outlined autofocus class="fleet-name-input" :error="$v.existing.groupName.$error" v-model="existing.groupName" />
          </div>
        </q-card-section>
        <q-card-actions align="right" class=" ">
          <q-btn icon="close" v-close-popup flat color="default" label="Cancel" @click="onCancel" />
          <q-btn icon="check" flat color="secondary" label="Continue" @click="onOk" />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </div>
</template>

<script>
import { mapActions } from 'vuex';
import { required } from 'vuelidate/lib/validators';

export default {
  name: 'CreateFleetDialog',
  props: {
    // show: {
    //   type: Boolean,
    //   default: false
    // },
  },
  data() {
    return {
      existing: {},
      name: '',
      id: '',
      show: false,
      fleet: {},
    };
  },
  validations: {
    existing: {
      groupName: { required },
    },
  },
  methods: {
    ...mapActions({
      renameFleet: 'fleets/renameFleet',
      createFleet: 'fleets/createFleet',
    }),
    onOk() {
      if (!(this.existing || {}).groupName || (this.existing || {}).groupName.length < 2) {
        return this.$q.notify({ message: 'Fleet name is required', color: 'negative' });
      }
      this.show = false;
      let promise;
      if (this.existing.id) {
        this.$store.commit('ui/setFleetInProcess', this.fleet);
        promise = this.renameFleet({
          id: this.existing.id,
          name: this.existing.groupName,
        });
      } else {
        const fleetData = {
          groupName: this.existing.groupName,
          groupType: this.existing.groupType || 'static',
        };
        promise = this.createFleet(fleetData);
      }
      promise
        .then((data) => {
          let message = `Fleet "${this.existing.groupName}", has been created`;
          if (this.fleet.id) {
            message = `Fleet "${this.fleet.groupName}", has been renamed to "${this.existing.groupName}"`;
            this.selectedFleet = this.existing;
            this.$store.commit('ui/setFleetInProcess', null);
          }
          this.$q.notify({ message, color: 'positive' });
        })
        .catch((err) => {
          logError('Unable to create fleet:', err);
          this.$q.notify({ message: 'Unable to create ' + this.existing.groupName + ', please try again', color: 'negative' });
        });
    },
    onCancel() {},
    onShow() {
      this.existing = { ...this.fleet };
    },
    onHide() {
      this.$v.existing.groupName.$reset();
    },
  },
  computed: {
    selectedFleet: {
      get() {
        return this.$store.getters['fleets/selectedFleet'];
      },
      set(val) {
        this.$store.commit('fleets/setSelectedFleet', val);
      },
    },
  },
  mounted() {
    this.$events.$on('dialogs:create-fleet:open', (data) => {
      Object.assign(this, data);
    });
  },
};
</script>
