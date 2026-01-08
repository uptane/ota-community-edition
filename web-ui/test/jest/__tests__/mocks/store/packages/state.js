export default {
  preparedPackages: [],
  packagesUploading: [],
  tableData: [],
  packages: [],
  preparedOndevicePackages: [],
  delegationTypes: [
    {
      label: 'LTS',
      value: 'tdx/lts',
      source: 'toradex',
    },
    {
      label: 'Quarterly',
      value: 'tdx/quarterly',
      source: 'toradex',
    },
    {
      label: 'Monthly',
      value: 'tdx/monthly',
      source: 'toradex',
    },
    {
      label: 'Nightly',
      value: 'tdx/nightly',
      source: 'toradex',
    },
    {
      label: 'Legacy',
      value: 'custom',
      source: 'custom',
    },
    {
      label: 'All build types',
      value: 'all',
    },
  ],
};
