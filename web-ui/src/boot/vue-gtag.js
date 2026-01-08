// import Vue from "vue";
// import VueGtag from "vue-gtag";
// import RouterFactory from "../router";
// import { productName } from "../../package.json";

// {/* <script async src="https://www.googletagmanager.com/gtag/js?id=G-QPLLS8PMSB"></script>
// <script>
//     window.dataLayer = window.dataLayer || [];
//   function gtag(){dataLayer.push(arguments);}
//   gtag('js', new Date());
//   gtag('config', 'G-QPLLS8PMSB');
// </script> */}

// const id = 'G-1HF695GHSX' // Test only
// const id = 'G-QPLLS8PMSB' // Production

// Vue.use(VueGtag, {
//   config: { id: process.env.GTAG_ID },
//   appName: productName,
//   pageTrackerScreenviewEnabled: true
// }, RouterFactory());

import gtm from '../services/gtm.service';

(function(w, d, s, l, i) {
  w[l] = w[l] || [];
  w[l].push({ 'gtm.start': new Date().getTime(), event: 'gtm.js' });
  var f = d.getElementsByTagName(s)[0],
    j = d.createElement(s),
    dl = l != 'dataLayer' ? '&l=' + l : '';
  j.async = true;
  j.src = 'https://www.googletagmanager.com/gtm.js?id=' + i + dl;
  f.parentNode.insertBefore(j, f);
})(window, document, 'script', 'dataLayer', process.env.GTAG_ID);

export default ({ router }) => {
  router.afterEach((to, from) => {
    gtm.logPage(to.path, to.name);
  });
};
