import { mount, createLocalVue } from '@vue/test-utils';
import AddToFleetDialog from '../../../../../src/components/fleets/AddToFleetDialog';

import * as All from 'quasar';

const { Quasar, date, format } = All;

const components = Object.keys(All).reduce((object, key) => {
  const val = All[key];
  if (val && val.component && val.component.name != null) {
    object[key] = val;
  }
  return object;
}, {});

describe.skip('AddToFleetDialog Snapshot', () => {
  const localVue = createLocalVue();
  localVue.use(Quasar, { components }); // , lang: langEn

  const wrapper = mount(AddToFleetDialog, {
    // mocks, store, stubs,
    localVue,
  });
  it('renders correctly', () => {
    expect(wrapper.element).toMatchSnapshot();
  });
});
