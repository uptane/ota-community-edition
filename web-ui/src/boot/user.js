import { AuthService } from '../services/auth.service';

export default async ({ Vue }) => {
  await AuthService.init();
  Vue.prototype.$user = AuthService.getSession();
};
