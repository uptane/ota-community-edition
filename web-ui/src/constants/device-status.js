export const device_update_event_message_types = {
  EcuDownloadStarted: 'EcuDownloadStarted',
  EcuDownloadCompleted: 'EcuDownloadCompleted',
  EcuInstallationStarted: 'EcuInstallationStarted',
  EcuInstallationApplied: 'EcuInstallationApplied',
  EcuInstallationCompleted: 'EcuInstallationCompleted',
};

export const update_states = {
  Scheduled: {
    key: 'scheduled',
    summary: 'Scheduled',
    label: 'Scheduled',
    message: 'An update has been scheduled',
  },
  Outdated: {
    key: 'queued',
    summary: 'Pending',
    label: 'Update pending',
    message: "An update has been initiated, but the device hasn't seen it yet.",
  },
  UpdatePending: {
    key: 'updating',
    summary: 'In progress',
    label: 'Update in progress',
    message: 'The device has received its update instructions, and is downloading or installing the selected update.',
  },
  UpToDate: {
    key: 'success',
    summary: 'Up to date',
    label: 'Up to date',
    message: 'No updates pending and no failures reported since the last update.',
  },
  Error: {
    key: 'failed',
    summary: 'Update failed',
    label: 'Update failed',
    message: 'The device tried to install an update, but failed.',
  },
  Failed: {
    key: 'failed',
    summary: 'Update failed',
    label: 'Update failed',
    message: 'The device tried to install an update, but failed.',
  },
};
