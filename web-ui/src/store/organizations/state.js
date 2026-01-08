/**
 * OTA Community Edition Organizations State
 * 
 * CE has a single namespace/repository, so this state is simplified.
 */

// Default CE repository - always present
const CE_REPOSITORY = {
  id: 'ce-default-repository',
  name: 'OTA CE Repository',
  description: 'OTA Community Edition default repository',
  is_host_repo: true,
};

export default {
  hostRepository: CE_REPOSITORY,
  hostRepositoryUsers: [],
  guestRepositories: [],
  guestProfiles: [],
};
