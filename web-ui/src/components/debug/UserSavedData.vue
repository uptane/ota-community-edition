<template>
  <div class="full-width p-1 pb-5 mb-5">
    <h4 class="m-0 ">
      My Saved Config Data
      <q-btn class="q-ml-sm" color="primary" :loading="loading" @click="updateData">
        <q-icon name="save" class="q-mr-sm"></q-icon> Save settings
        <template v-slot:loading>
          <q-spinner-hourglass class="on-left" />
          Saving changes
        </template>
      </q-btn>
    </h4>
    <div>
      <q-list bordered padding separator>
        <q-item tag="label" v-for="(item, key) in userSettings" :key="key">
          <q-item-section>
            <q-item-label>{{ key }}</q-item-label>
            <q-item-label caption> </q-item-label>
          </q-item-section>

          <template>
            <q-item-section v-if="typeof userSettings[key] === 'boolean'" side top>
              <q-checkbox v-model="userSettings[key]" />
            </q-item-section>
            <q-item-section v-else-if="typeof userSettings[key] === 'object'" side top>
              <q-input type="textarea" outlined :value="objectToString(userSettings[key])" @input="userSettings[key] = toJson($event)"></q-input>
            </q-item-section>
            <q-item-section v-else-if="toJson(userSettings[key])" side top>
              <q-input type="textarea" outlined v-model="userSettings[key]"></q-input>
            </q-item-section>
            <q-item-section v-else-if="typeof userSettings[key] === 'string'" side top>
              <q-input outlined v-model="userSettings[key]"></q-input>
            </q-item-section>
            <q-item-section v-else-if="typeof userSettings[key] === 'number'" side top>
              <q-input outlined type="number" v-model="userSettings[key]"></q-input>
            </q-item-section>
          </template>
        </q-item>
      </q-list>
    </div>
  </div>
</template>

<script>
import { mapActions, mapMutations, mapGetters } from 'vuex';
export default {
  name: 'UserSavedData',
  data() {
    return {
      loading: false,
      userSettings: {},
    };
  },
  computed: {
    ...mapGetters({
      user: 'users/user',
      userData: 'users/userData',
      userSettingsData: 'ui/userSettings',
    }),
  },
  methods: {
    toJson(jsonString) {
      try {
        var o = JSON.parse(jsonString);
        if (o && typeof o === 'object') {
          return o;
        }
      } catch (e) {}

      return false;
    },
    objectToString(obj) {
      return JSON.stringify(obj, null, 2);
    },
    updateData() {
      this.loading = true;
      this.$store
        .dispatch('ui/saveUserSettings', this.userSettings)
        .then((settings) => {
          this.userSettings = { ...this.userSettingsData };
          this.$q.notify({
            message: 'Saved',
            color: 'positive',
            icon: 'check',
            position: 'top',
          });
        })
        .catch((err) => {
          this.$q.notify({
            message: 'Error saving data',
            color: 'negative',
            icon: 'warning',
            position: 'top',
          });
        })
        .finally(() => {
          this.loading = false;
        });
    },
  },
  mounted() {
    this.userSettings = { ...this.userSettingsData };
  },
};
</script>

<style></style>
