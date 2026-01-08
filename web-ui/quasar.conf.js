/**
 * OTA Community Edition - Quasar Configuration
 * 
 * Simplified configuration without Keycloak and multi-tenant features.
 */

const fs = require('fs');

// API endpoint host - default to localhost for development
let apiHost = process.env.API_ENDPOINT_HOST || 'http://localhost:8080';

// Proxy configuration for development
// In production, nginx handles the routing
let rules = [
  {
    targetHost: apiHost,
    src: '/api',
    dest: '/api',
  },
  {
    targetHost: apiHost,
    dest: '/device-registry/api',
    src: '/device-registry/api',
  },
];

let proxy = {};
rules.forEach(function(rule) {
  proxy[rule.src] = {
    target: rule.targetHost,
    changeOrigin: true,
    pathRewrite: {
      [rule.src]: rule.dest,
    },
    headers: {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Credentials': 'true',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization, x-id, Content-Length, X-Requested-With',
      'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
    },
  };
});

module.exports = function(ctx) {
  return {
    // Boot files (src/boot)
    boot: [
      'i18n',
      'axios',
      'date',
      'timeago',
      'jquery',
      'masonry',
      'croppie',
      'size',
      'vuelidate',
      'access-manager',
      'event-bus',
      'notify-defaults',
      'logger',
      'user',
      'addressbar-color',
      'change-case',
      'codemirror',
      'version',
      'showdown',
      'feature-teaser',
      // Removed: 'vue-gtag' (Google Analytics - not needed for CE)
    ],
    css: ['app.scss', '../statics/fonts/iconmoon/style.css'],
    extras: [
      'roboto-font',
      'material-icons',
      'ionicons-v4',
      'mdi-v3',
      'fontawesome-v5',
    ],
    supportIE: true,
    build: {
      modern: true,
      scopeHoisting: true,
      devtool: 'source-map',
      env: {
        // CE mode - no external auth or analytics
        GUEST_MODE: process.env.GUEST_MODE || '0',
        LIMITED_ACCESS: process.env.LIMITED_ACCESS || '0',
        CREDENTIALS_DOWNLOAD_LINK: JSON.stringify(process.env.CREDENTIALS_DOWNLOAD_LINK) || '"/api/accounts/credentials.zip"',
        E2E_MODE: process.env.E2E_MODE || '0',
        WS_HOST: process.env.WS_HOST || '""',
        GTAG_ID: '""', // Disabled for CE
        DEMO_MODE: '0',
        // Auth config - not used in CE mode
        ID_PROVIDER_REALM: '""',
        ID_PROVIDER_CLIENT_ID: '""',
        ID_PROVIDER_URI: '""',
        // SugarCRM - not used in CE mode
        SUGARCRM_COMMERCIAL_ACCESS_CAMPAIGN_ID: '""',
        SUGARCRM_ONBOARDING_WALKTHROUGH_CAMPAIGN_ID: '""',
        SUGARCRM_NEW_USER_CAMPAIGN_ID: '""',
      },
      extendWebpack(cfg) {},
    },
    vendor: {
      disable: false,
      add: [],
      remove: ['pdfmake', 'xlsx'],
    },
    framework: {
      components: [
        'QLayout',
        'QItemSection',
        'QItemLabel',
        'QHeader',
        'QFooter',
        'QDrawer',
        'QPageContainer',
        'QPage',
        'QToolbar',
        'QToolbarTitle',
        'QBtn',
        'QBtnGroup',
        'QBtnDropdown',
        'QIcon',
        'QList',
        'QItem',
        'QSeparator',
        'QCard',
        'QCardActions',
        'QCardSection',
        'QToggle',
        'QBtnToggle',
        'QInnerLoading',
        'QSpinnerGears',
        'QSpinnerHourglass',
        'QDialog',
        'QInput',
        'QSelect',
        'QTable',
        'QScrollArea',
        'QTooltip',
        'QSlider',
        'QSpace',
        'QTh',
        'QTr',
        'QTd',
        'QField',
        'QLinearProgress',
        'QChip',
        'QMenu',
        'QCheckbox',
        'QAvatar',
        'QPopupProxy',
        'QStepper',
        'QStep',
        'QStepperNavigation',
        'QForm',
        'QTimeline',
        'QTimelineEntry',
        'QTime',
        'QDate',
        'QImg',
        'QBanner',
        'QBadge',
        'QOptionGroup',
        'QRadio',
        'QTab',
        'QTabs',
        'QTabPanel',
        'QTabPanels',
        'QCarousel',
        'QCarouselSlide',
        'QCarouselControl',
        'QExpansionItem',
        'QFile',
        'QVideo',
        'QSkeleton',
        'QMarkupTable',
        'QResizeObserver',
      ],
      directives: ['Ripple', 'ClosePopup', 'TouchHold'],
      plugins: ['Notify', 'Screen', 'Dialog', 'LocalStorage', 'SessionStorage', 'Cookies', 'AddressbarColor', 'LoadingBar'],
      config: {
        loadingBar: {
          position: 'bottom',
          color: 'secondary',
          size: '.1rem',
        },
        dark: 'auto',
      },
    },
    animations: 'all',
    ssr: {
      pwa: false,
    },
    pwa: {
      manifest: {
        name: 'OTA Community Edition',
        short_name: 'OTA-CE',
        description: 'Open-source OTA update system',
        display: 'standalone',
        orientation: 'portrait',
        background_color: '#ffffff',
        theme_color: '#027be3',
        icons: [
          {
            src: 'statics/icons/icon-128x128.png',
            sizes: '128x128',
            type: 'image/png',
          },
          {
            src: 'statics/icons/icon-192x192.png',
            sizes: '192x192',
            type: 'image/png',
          },
          {
            src: 'statics/icons/icon-256x256.png',
            sizes: '256x256',
            type: 'image/png',
          },
          {
            src: 'statics/icons/icon-384x384.png',
            sizes: '384x384',
            type: 'image/png',
          },
          {
            src: 'statics/icons/icon-512x512.png',
            sizes: '512x512',
            type: 'image/png',
          },
        ],
      },
    },
    cordova: {},
    electron: {
      extendWebpack(cfg) {},
      packager: {},
      builder: {},
    },
    devServer: {
      // For development without HTTPS certs, use HTTP
      // https: {
      //   key: fs.readFileSync('./certs/localhost-key.pem'),
      //   cert: fs.readFileSync('./certs/localhost.pem'),
      // },
      port: 8080,
      open: true,
      proxy: proxy,
    },
  };
};
