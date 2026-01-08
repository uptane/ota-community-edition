<template>
  <div
    class="h-100 mnh-100vh w-100vw text-white"
    :class="{
      'p-2 bg-dark-theme-bg': $q.screen.lt.md,
      'p-5 bg-dark-theme-bg-dark': $q.screen.gt.sm,
    }"
    style="position:relative;
      margin-top: -0rem;"
  >
    <div style="z-index:100; position:relative;" class="mxw-90vw m-auto">
      <div class>
        <div
          :class="{
            'text-center mb-2': $q.screen.lt.md,
          }"
        >
          <img
            id="logo"
            class="animated slideInUp self-center w-20em mxw-80"
            :style="{
              width: '20rem',
            }"
            src="~assets/svg/toradex-logo-1.svg"
          />
        </div>
        <h2
          class="m-0 text-thin animated slideInLeft"
          :class="{
            'text-center text-h5 mb-2': $q.screen.lt.md,
          }"
        >
          Simplifying the development and operation of Linux loT Devices
        </h2>

        <div v-if="$q.screen.lt.md">
          <q-img :src="bgImage" />
        </div>
      </div>
      <div class="animated fadeIn">
        <h3 class="m-0 p-0">Highlights</h3>
        <div>
          <ul class="q-pl-md m-0">
            <li>Secure and easy-to-use device provisioning workflow,</li>
            <li>Fault tolerant (from power outage, etc),</li>
            <li>Works well with limited bandwidth like cell data,</li>
            <li>Receive regular updates to device BSP from Toradex,</li>
            <li>Fleet Management,</li>
            <li>Seamless integration with TorizonCore,<br />and so much more.</li>
          </ul>

          <div
            :class="{
              'text-center mb-5 pb-5': $q.screen.lt.md,
            }"
          >
            <h3 class="mt-1 mb-1">Ready to get started?</h3>
            <div class="row">
              <div class="col-auto">
                <q-btn to="/register" color="primary">Create account</q-btn>
              </div>
              <div class="col-auto">
                <q-btn type="a" flat no-caps href="https://toradex.com/torizon" color="primary" target="_blank">Learn more about Torizon Platform</q-btn>
              </div>
            </div>
            <p class="mt-1 mb-1">
              <q-btn flat id="sign-in-btn" class="p-0" to="/login" color="primary">sign in here</q-btn>
            </p>
            <p class="text-h6 ">
              Visit our <a href="https://www.toradex.com/order-now/?source=ota-landing-page" flat no-caps target="_blank" class="m-0 text-primary">webshop</a>&nbsp;|&nbsp; <a href="https://bit.ly/2UOKg9l" flat no-caps target="_blank" class="m-0 text-primary">Free Consultation</a>&nbsp;
            </p>
          </div>
        </div>

        <div></div>
      </div>
    </div>
    <div
      v-if="$q.screen.gt.sm"
      class="animated pulse"
      :class="{
        'mxw-50em mxh-40em': $q.screen.gt.sm && $q.screen.lt.lg,
        'mxw-60em mxh-50em': $q.screen.gt.md && $q.screen.lt.xl,
        'mxw-80em mxh-70em': $q.screen.xl,
      }"
      :style="{
        position: 'absolute',
        right: 0,
        top: 0,
        width: '60%',
        height: '70%',

        'margin-top': 0,
        backgroundImage: `url('${bgImage}')`,
        backgroundSize: 'cover',
        backgroundPosition: '0% 100%',
        'z-index': 50,
      }"
    ></div>
    <div class="p-2 absolute-bottom text-center">
      <div class="mb-2 "></div>

      <div>&copy; {{ thisYear }} by Toradex</div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'Landing',
  computed: {
    thisYear() {
      return new Date().getFullYear('YYYY');
    },
    bgImage() {
      /* eslint-disable global-require */
      return this.$q.screen.lt.md ? 'statics/devices-app-demo-phone.png' : 'statics/devices-app-demo.png';
    },
  },
};
</script>
