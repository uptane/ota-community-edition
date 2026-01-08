import * as rrweb from 'rrweb';
export default ({ Vue }) => {
  let events = [];

  rrweb.record({
    emit(event) {
      // push event into the events array
      events.push(event);
    },
  });

  // this function will send events to the backend and reset the events array
  function save() {
    const body = JSON.stringify({ events });
    events = [];
    fetch('https://to-be-determined', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body,
    });
  }

  // save events every 10 seconds
  // setInterval(save, 10 * 1000);
};

// export { rec};
