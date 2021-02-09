create index devices_namespace_primary_ecu_id_idx on devices (namespace, primary_ecu_id);

create index ecu_serial_hardware_identifier_idx on ecus (ecu_serial, hardware_identifier);

