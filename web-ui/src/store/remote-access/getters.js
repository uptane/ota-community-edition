import { AuthService } from 'src/services/auth.service';

export function publicKeys(state) {
  return state.publicKeys || {};
}
