import test, { beforeEach } from "node:test";
import assert from "node:assert/strict";
import { mock } from "node:test";

import {
  handler,
  parseEnvList,
  getProductConfig,
  createMessageAttributes,
  validateConfig,
  CONFIG
} from "./index.js";

import * as sqsModule from "@aws-sdk/client-sqs";


beforeEach(() => {
  CONFIG.trackerEnabledProducts = ["AR", "RS"];
  CONFIG.trackerDryRunProducts = ["RS"];
  CONFIG.paperChannelEnabledProducts = ["890"];
  CONFIG.queueTracker = "https://sqs.mock/tracker";
  CONFIG.queuePaperChannel = "https://sqs.mock/channel";
});

// Unit tests
test("parseEnvList returns cleaned array", () => {
  // Arrange
  const input = "AR, RS , ,890";

  // Act
  const result = parseEnvList(input);

  // Assert
  assert.deepEqual(result, ["AR", "RS", "890"]);
  assert.deepEqual(parseEnvList(""), []);
  assert.deepEqual(parseEnvList(undefined), []);
});

test("getProductConfig returns correct configuration", () => {
  // Arrange
  CONFIG.trackerEnabledProducts = ["AR", "RS"];
  CONFIG.trackerDryRunProducts = ["RS"];
  CONFIG.paperChannelEnabledProducts = ["890"];
  CONFIG.queueTracker = "https://sqs.mock/tracker";
  CONFIG.queuePaperChannel = "https://sqs.mock/channel";

  // Act
  const configAR = getProductConfig("AR");
  const configRS = getProductConfig("RS");
  const config890 = getProductConfig("890");

  // Assert
  assert.equal(configAR.isTrackerEnabled, true);
  assert.equal(configAR.isDryRun, false);
  assert.equal(configAR.isPaperChannelEnabled, false);

  assert.equal(configRS.isTrackerEnabled, true);
  assert.equal(configRS.isDryRun, true);
  assert.equal(configRS.isPaperChannelEnabled, false);

  assert.equal(config890.isTrackerEnabled, false);
  assert.equal(config890.isPaperChannelEnabled, true);
});

test("createMessageAttributes copies attributes and adds dryRun when requested", () => {
  // Arrange
  const attrs = {
    foo: { DataType: "String", StringValue: "bar" },
  };

  // Act
  const noDry = createMessageAttributes(attrs);
  const withDry = createMessageAttributes(attrs, true);

  // Assert
  assert.deepEqual(noDry.foo, attrs.foo);
  assert.equal(noDry.dryRun, undefined);
  assert.equal(withDry.dryRun.StringValue, "true");
});

test("validateConfig throws if queues are missing", () => {
  // Arrange
  CONFIG.queueTracker = undefined;
  CONFIG.queuePaperChannel = undefined;

  // Act & Assert
  assert.throws(() => validateConfig(), /Missing required queue URLs/);

  // Arrange again
  CONFIG.queueTracker = "https://sqs.mock/tracker";
  CONFIG.queuePaperChannel = "https://sqs.mock/channel";

  // Act & Assert
  assert.doesNotThrow(() => validateConfig());
});

// Handler happy path
test("handler processes a valid record and sends to both queues", async () => {
  // Arrange
  CONFIG.trackerEnabledProducts = ["AR"];
  CONFIG.trackerDryRunProducts = ["AR"];
  CONFIG.paperChannelEnabledProducts = ["AR"];
  CONFIG.queueTracker = "https://sqs.mock/tracker";
  CONFIG.queuePaperChannel = "https://sqs.mock/channel";

  const sendMock = mock.method(sqsModule.SQSClient.prototype, "send", async () => {
    return { MessageId: "12345" };
  });

  const record = {
    messageId: "1",
    body: JSON.stringify({ analogMail: { productType: "AR" } }),
    messageAttributes: {},
  };

  // Act
  const result = await handler({ Records: [record] });
  console.log(result);

  // Assert
  assert.deepEqual(result, { status: "ok" });
  assert.equal(sendMock.mock.calls.length, 2, "Should send to tracker and channel");
});

test("handler returns batchItemFailures for invalid JSON", async () => {
  // Arrange
  const badRecord = {
    messageId: "bad1",
    body: "{invalid json",
    messageAttributes: {},
  };

  // Act
  const result = await handler({ Records: [badRecord] });

  // Assert
  assert.deepEqual(result, {
    batchItemFailures: [{ itemIdentifier: "bad1" }],
  });
});

// Error scenarios
test("handler fails when sending to tracker queue throws", async () => {
  // Arrange
  CONFIG.trackerEnabledProducts = ["AR"];
  CONFIG.trackerDryRunProducts = ["AR"];
  CONFIG.paperChannelEnabledProducts = ["AR"];
  CONFIG.queueTracker = "https://sqs.mock/tracker";
  CONFIG.queuePaperChannel = "https://sqs.mock/channel";

  const sendMock = mock.method(sqsModule.SQSClient.prototype, "send", async () => {
    throw new Error("Tracker send failed");
  });

  const record = {
    messageId: "err1",
    body: JSON.stringify({ analogMail: { productType: "AR" } }),
    messageAttributes: {},
  };

  // Act
  const result = await handler({ Records: [record] });

  // Assert
  assert.deepEqual(result, {
    batchItemFailures: [{ itemIdentifier: "err1" }],
  });
  assert.equal(sendMock.mock.calls.length, 1, "Should stop after tracker failure");
});

test("handler fails when sending to paper channel queue throws", async () => {
  // Arrange
  CONFIG.trackerEnabledProducts = ["AR"];
  CONFIG.trackerDryRunProducts = ["AR"];
  CONFIG.paperChannelEnabledProducts = ["AR"];
  CONFIG.queueTracker = "https://sqs.mock/tracker";
  CONFIG.queuePaperChannel = "https://sqs.mock/channel";

  const sendMock = mock.method(sqsModule.SQSClient.prototype, "send", async (cmd) => {
    if (cmd.input.QueueUrl.includes("tracker")) {
      return { MessageId: "tracker-ok" };
    }
    throw new Error("Paper channel send failed");
  });

  const record = {
    messageId: "err2",
    body: JSON.stringify({ analogMail: { productType: "AR" } }),
    messageAttributes: {},
  };

  // Act
  const result = await handler({ Records: [record] });

  // Assert
  assert.deepEqual(result, {
    batchItemFailures: [{ itemIdentifier: "err2" }],
  });
  assert.equal(sendMock.mock.calls.length, 2, "Should try tracker first, then channel");
});
