-- V2 CloudBase relational tables.
-- Run in the CloudBase relational database console before testing multi-device V2 sync.
-- Existing master tables are not changed here.

CREATE TABLE IF NOT EXISTS delivery_tasks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customerName VARCHAR(120) NOT NULL DEFAULT '',
    phoneNumber VARCHAR(40) NOT NULL DEFAULT '',
    address VARCHAR(255) NOT NULL DEFAULT '',
    areaTag VARCHAR(80) NOT NULL DEFAULT '',
    taskType VARCHAR(40) NOT NULL DEFAULT 'DELIVERY',
    deliveryQuantity INT NOT NULL DEFAULT 0,
    pickupQuantity INT NOT NULL DEFAULT 0,
    assignedEmployeeId BIGINT NULL,
    assignedEmployeeName VARCHAR(80) NOT NULL DEFAULT '',
    paymentStatus VARCHAR(40) NOT NULL DEFAULT 'UNPAID',
    amountToCollect DOUBLE NOT NULL DEFAULT 0,
    amountPaid DOUBLE NOT NULL DEFAULT 0,
    debtReminder DOUBLE NOT NULL DEFAULT 0,
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    dueLabel VARCHAR(80) NOT NULL DEFAULT '',
    note TEXT NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    createdByEmployeeId BIGINT NULL,
    createdByName VARCHAR(80) NOT NULL DEFAULT '',
    createdAt BIGINT NOT NULL,
    completedAt BIGINT NULL,
    updatedAt BIGINT NOT NULL
);

CREATE INDEX idx_delivery_tasks_assignee_status
    ON delivery_tasks (assignedEmployeeId, status);
CREATE INDEX idx_delivery_tasks_updated
    ON delivery_tasks (updatedAt);

-- The following V2 tables are already modeled locally. Their CloudBase sync is added gradually.
CREATE TABLE IF NOT EXISTS bottle_details (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    deliveryRecordId BIGINT NOT NULL,
    bottleType VARCHAR(40) NOT NULL,
    quantity INT NOT NULL DEFAULT 0,
    productionMark VARCHAR(80) NOT NULL DEFAULT '',
    bottleCondition VARCHAR(40) NOT NULL DEFAULT 'NORMAL',
    customerName VARCHAR(120) NOT NULL DEFAULT '',
    unitPrice DOUBLE NOT NULL DEFAULT 0,
    policyId BIGINT NULL,
    note TEXT NOT NULL,
    updatedAt BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS products (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL,
    unit VARCHAR(20) NOT NULL DEFAULT '个',
    defaultPrice DOUBLE NOT NULL DEFAULT 0,
    enabled TINYINT NOT NULL DEFAULT 1,
    sortOrder INT NOT NULL DEFAULT 0,
    updatedAt BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS product_sale_items (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    deliveryRecordId BIGINT NOT NULL,
    productId BIGINT NULL,
    productName VARCHAR(120) NOT NULL,
    quantity DOUBLE NOT NULL DEFAULT 0,
    unit VARCHAR(20) NOT NULL DEFAULT '个',
    unitPrice DOUBLE NOT NULL DEFAULT 0,
    updatedAt BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS policies (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(160) NOT NULL,
    policyType VARCHAR(40) NOT NULL DEFAULT 'BOTTLE_REPLACEMENT',
    amount DOUBLE NOT NULL DEFAULT 0,
    conditionText TEXT NOT NULL,
    startAt BIGINT NULL,
    endAt BIGINT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    updatedAt BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS station_duties (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dutyType VARCHAR(40) NOT NULL DEFAULT 'UNLOADING',
    dutyDate BIGINT NOT NULL,
    assignedEmployeeId BIGINT NOT NULL,
    assignedEmployeeName VARCHAR(80) NOT NULL,
    expectedReturnAt BIGINT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'ASSIGNED',
    swapWithEmployeeId BIGINT NULL,
    note TEXT NOT NULL,
    updatedAt BIGINT NOT NULL
);
