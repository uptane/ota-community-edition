<template>
  <q-card flat bordered style="max-width: 300px">
    <q-item>
      <q-item-section avatar>
        <q-skeleton type="QAvatar" animation="fade" />
      </q-item-section>

      <q-item-section>
        <q-item-label>
          <q-skeleton type="text" animation="fade" />
        </q-item-label>
        <q-item-label caption>
          <q-skeleton type="text" animation="fade" />
        </q-item-label>
      </q-item-section>
    </q-item>

    <q-skeleton height="60px" square animation="fade" />

    <q-card-section>
      <q-skeleton type="text" class="text-subtitle2" animation="fade" />
      <q-skeleton type="text" width="50%" class="text-subtitle2" animation="fade" />
    </q-card-section>
  </q-card>
</template>

<script>
export default {};
</script>

<style></style>
