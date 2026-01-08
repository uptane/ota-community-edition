export default {
  updates: [],
  visibleColumns: ['name', 'contents', 'status', 'expires', 'version', 'length', 'actions'],
  columns: [
    {
      required: true,
      label: 'Update Name',
      align: 'left',
      field: (row) => row.name,
      format: (val) => `${val}`,
      sortable: true,
      classes: 'ellipsis text-bold',
      style: 'max-width: 100px',
      headerClasses: 'text-bold',
      name: 'name',
      id: 'name',
    },
    { name: 'contents', id: 'contents', label: 'Contents', field: 'contents', sortable: false, align: 'left' },
    { name: 'status', id: 'status', label: 'Status', field: 'status', sortable: true, align: 'left' },
    { name: 'expires', id: 'expires', label: 'Expires', field: 'expires', sortable: true, align: 'left' },
    { name: 'actions', id: 'actions', label: 'Actions', field: 'actions', sortable: false, align: 'center', required: true },
  ],
};
