ALTER TABLE DeviceSystem
MODIFY COLUMN local_ipv4 CHAR(15) DEFAULT "",
MODIFY COLUMN mac_address CHAR(17) DEFAULT "",
MODIFY COLUMN hostname VARCHAR(255) DEFAULT "";