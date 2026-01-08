let rules = [];
[
  { dest: '/device-registry/api/v1/devices', src: '/api/v1/devices' },
  { dest: '/director/api/v1/admin/devices', src: '/api/v1/admin/devices' },
  {
    dest: '/director/api/v1/admin/devices/hardware_identifiers',
    src: '/api/v1/admin/devices/hardware_identifiers',
  },
  {
    dest: '/reposerver/api/v1/user_repo/targets.json',
    src: '/api/v1/user_repo/targets.json',
  },
  {
    dest: '/device-registry/api/v1/device_packages',
    src: '/api/v1/device_packages',
  },
  { dest: '/device-registry/api/v1/device_count', src: '/api/v1/device_count' },
  {
    dest: '/device-registry/api/v1/device_groups',
    src: '/api/v1/device_groups',
  },
  {
    dest: '/director/api/v1/admin/images/installed_count',
    src: '/api/v1/admin/images/installed_count',
  },
  {
    dest: '/director/api/v1/multi_target_updates',
    src: '/api/v1/multi_target_updates',
  },
  {
    dest: '/core/devices',
    src: '/api/core/devices',
  },
  {
    dest: '/provision',
    src: '/api/provision',
  },
].forEach(function(rule) {
  rules.push({
    path: rule.src,
    rule: {
      target: 'http://' + process.env.API_ENDPOINT_HOST + '/',
      pathRewrite: {
        [rule.src]: rule.dest,
      },
      changeOrigin: true,
    },
  });
});

module.exports = rules;
// [
//   {
//     path: '/api/v1/devices',
//     rule: {
//       target: 'http://10.12.1.79/',
//       pathRewrite: {
//         '^/api/v1/devices': '/device-registry/api/v1/devices',
//       },
//       changeOrigin: true,
//     },
//   },
// ];

// 10.12.1.79/device-registry/api/v1/devices
// 10.12.1.79/director/api/v1/admin/devices
// 10.12.1.79/director/api/v1/admin/devices/hardware_identifiers
// 10.12.1.79/reposerver/api/v1/user_repo/targets.json
// 10.12.1.79/device-registry/api/v1/device_packages
// 10.12.1.79/device-registry/api/v1/device_count
// 10.12.1.79/device-registry/api/v1/device_groups
// 10.12.1.79/device-registry/api/v1/admin/images/installed_count
