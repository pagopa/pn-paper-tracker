import { expect } from "chai";
import sinon from "sinon";

import {
  handler,
  parseEnvList,
  getProductConfig,
  createMessageAttributes,
  validateConfig,
  parseTrackerConfig,
  CONFIG
} from "./index.js";

import * as sqsModule from "@aws-sdk/client-sqs";

// Unit tests per le utility functions
describe("Utility functions", () => {
  beforeEach(() => {
    // Reset config prima di ogni test
    CONFIG.trackerEnabledProducts = ["AR", "RS"];
    CONFIG.trackerDryRunProducts = ["RS"];
    CONFIG.paperChannelEnabledProducts = ["890"];
    CONFIG.queueTracker = "https://sqs.mock/tracker";
    CONFIG.queuePaperChannel = "https://sqs.mock/channel";

    // Ripristina eventuali stub/mocking di Sinon
    sinon.restore();
  });

  it("parseEnvList returns cleaned array", () => {
    // Arrange
    const input = "AR, RS , ,890";

    // Act & Assert
    expect(parseEnvList(input)).to.deep.equal(["AR", "RS", "890"]);
    expect(parseEnvList("")).to.deep.equal([]);
    expect(parseEnvList(undefined)).to.deep.equal([]);
  });

  it("parseTrackerConfig parses tracker configuration with modes", () => {
    // Test valid configurations
    const config1 = parseTrackerConfig("AR:RUN,RS:DRY,890:DRY");
    expect(config1.trackerEnabledProducts).to.deep.equal(["AR", "RS", "890"]);
    expect(config1.trackerDryRunProducts).to.deep.equal(["RS", "890"]);

    const config2 = parseTrackerConfig("AR:RUN,RS:RUN");
    expect(config2.trackerEnabledProducts).to.deep.equal(["AR", "RS"]);
    expect(config2.trackerDryRunProducts).to.deep.equal([]);

    const config3 = parseTrackerConfig("RIR:DRY");
    expect(config3.trackerEnabledProducts).to.deep.equal(["RIR"]);
    expect(config3.trackerDryRunProducts).to.deep.equal(["RIR"]);

    // Test edge cases
    const configEmpty = parseTrackerConfig("");
    expect(configEmpty.trackerEnabledProducts).to.deep.equal([]);
    expect(configEmpty.trackerDryRunProducts).to.deep.equal([]);

    const configUndefined = parseTrackerConfig(undefined);
    expect(configUndefined.trackerEnabledProducts).to.deep.equal([]);
    expect(configUndefined.trackerDryRunProducts).to.deep.equal([]);

    // Test with spaces
    const configSpaces = parseTrackerConfig(" AR : RUN , RS : DRY ");
    expect(configSpaces.trackerEnabledProducts).to.deep.equal(["AR", "RS"]);
    expect(configSpaces.trackerDryRunProducts).to.deep.equal(["RS"]);
  });

  it("parseTrackerConfig throws error for invalid product types", () => {
    expect(() => parseTrackerConfig("INVALID:RUN")).to.throw(/Invalid productType in tracker configuration: INVALID/);
    expect(() => parseTrackerConfig("AR:RUN,INVALID:DRY")).to.throw(/Invalid productType in tracker configuration: INVALID/);
    expect(() => parseTrackerConfig("XYZ:DRY,RS:RUN")).to.throw(/Invalid productType in tracker configuration: XYZ/);
  });

  it("parseTrackerConfig throws error for invalid modes", () => {
    expect(() => parseTrackerConfig("AR:INVALID")).to.throw(/Invalid dryRun mode in tracker configuration: INVALID/);
    expect(() => parseTrackerConfig("AR:RUN,RS:UNKNOWN")).to.throw(/Invalid dryRun mode in tracker configuration: UNKNOWN/);
    expect(() => parseTrackerConfig("AR:run")).to.throw(/Invalid dryRun mode in tracker configuration: run/);
    expect(() => parseTrackerConfig("RS:dry")).to.throw(/Invalid dryRun mode in tracker configuration: dry/);
  });

  it("parseTrackerConfig throws error for malformed entries", () => {
    expect(() => parseTrackerConfig("AR")).to.throw(/Invalid dryRun mode in tracker configuration: undefined/);
    expect(() => parseTrackerConfig("AR:")).to.throw(/Invalid dryRun mode in tracker configuration: /);
    expect(() => parseTrackerConfig(":RUN")).to.throw(/Invalid productType in tracker configuration: /);
  });

  it("getProductConfig returns correct configuration", () => {
    // Act
    const configAR = getProductConfig("AR");
    const configRS = getProductConfig("RS");
    const config890 = getProductConfig("890");

    // Assert
    expect(configAR.isTrackerEnabled).to.be.true;
    expect(configAR.isDryRun).to.be.false;
    expect(configAR.isPaperChannelEnabled).to.be.false;

    expect(configRS.isTrackerEnabled).to.be.true;
    expect(configRS.isDryRun).to.be.true;
    expect(configRS.isPaperChannelEnabled).to.be.false;

    expect(config890.isTrackerEnabled).to.be.false;
    expect(config890.isPaperChannelEnabled).to.be.true;
  });

  it("createMessageAttributes copies attributes and adds dryRun when requested", () => {
    // Arrange
    const attrs = {
      foo: { DataType: "String", StringValue: "bar" },
    };

    // Act
    const noDry = createMessageAttributes(attrs);
    const withDry = createMessageAttributes(attrs, true);

    // Assert
    expect(noDry.foo).to.deep.equal(attrs.foo);
    expect(noDry.dryRun).to.be.undefined;
    expect(withDry.dryRun.StringValue).to.equal("true");
  });

  it("validateConfig throws if queues are missing", () => {
    // Arrange
    CONFIG.queueTracker = undefined;
    CONFIG.queuePaperChannel = undefined;

    // Act & Assert
    expect(() => validateConfig()).to.throw(/Missing required queue URLs/);

    // Restore queues
    CONFIG.queueTracker = "https://sqs.mock/tracker";
    CONFIG.queuePaperChannel = "https://sqs.mock/channel";

    expect(() => validateConfig()).to.not.throw();
  });

  it("getProductConfig uses UNKNOWN fallback when productType is null or undefined", () => {
    // Arrange
    CONFIG.trackerEnabledProducts = ["UNKNOWN"];
    CONFIG.trackerDryRunProducts = ["UNKNOWN"];
    CONFIG.paperChannelEnabledProducts = ["UNKNOWN"];

    // Act
    const configNull = getProductConfig(null);
    const configUndefined = getProductConfig(undefined);

    // Assert
    expect(configNull.isTrackerEnabled).to.be.true;
    expect(configNull.isDryRun).to.be.true;
    expect(configNull.isPaperChannelEnabled).to.be.true;

    expect(configUndefined.isTrackerEnabled).to.be.true;
    expect(configUndefined.isDryRun).to.be.true;
    expect(configUndefined.isPaperChannelEnabled).to.be.true;
  });

  it("getProductConfig returns false if UNKNOWN is not configured", () => {
    // Arrange
    CONFIG.trackerEnabledProducts = ["AR"];
    CONFIG.trackerDryRunProducts = ["RS"];
    CONFIG.paperChannelEnabledProducts = ["890"];

    // Act
    const configNull = getProductConfig(null);

    // Assert
    expect(configNull.isTrackerEnabled).to.be.false;
    expect(configNull.isDryRun).to.be.false;
    expect(configNull.isPaperChannelEnabled).to.be.false;
  });
});

// Unit tests per il handler
describe("Handler", () => {
  beforeEach(() => {
    // Reset config e restore dei mock
    CONFIG.trackerEnabledProducts = ["AR"];
    CONFIG.trackerDryRunProducts = ["AR"];
    CONFIG.paperChannelEnabledProducts = ["AR"];
    CONFIG.queueTracker = "https://sqs.mock/tracker";
    CONFIG.queuePaperChannel = "https://sqs.mock/channel";

    sinon.restore();
  });

  // Handler happy path
  it("processes a valid record and sends to both queues", async () => {
    // Arrange
    const sendStub = sinon.stub(sqsModule.SQSClient.prototype, "send")
      .resolves({ MessageId: "12345" });

    const record = {
      messageId: "1",
      body: JSON.stringify({ analogMail: { productType: "AR" } }),
      messageAttributes: {},
    };

    // Act
    const result = await handler({ Records: [record] });

    // Assert
    expect(result).to.deep.equal({ status: "ok" });
    expect(sendStub.callCount).to.equal(2, "Should send to tracker and channel");
  });

  it("returns batchItemFailures for invalid JSON", async () => {
    // Arrange
    const badRecord = {
      messageId: "bad1",
      body: "{invalid json",
      messageAttributes: {},
    };

    // Act
    const result = await handler({ Records: [badRecord] });

    // Assert
    expect(result).to.deep.equal({
      batchItemFailures: [{ itemIdentifier: "bad1" }],
    });
  });

  // Error scenarios
  it("fails when sending to tracker queue throws", async () => {
    // Arrange
    const sendStub = sinon.stub(sqsModule.SQSClient.prototype, "send")
      .rejects(new Error("Tracker send failed"));

    const record = {
      messageId: "err1",
      body: JSON.stringify({ analogMail: { productType: "AR" } }),
      messageAttributes: {},
    };

    // Act
    const result = await handler({ Records: [record] });

    // Assert
    expect(result).to.deep.equal({
      batchItemFailures: [{ itemIdentifier: "err1" }],
    });
    expect(sendStub.callCount).to.equal(1, "Should stop after tracker failure");
  });

  it("fails when sending to paper channel queue throws", async () => {
    // Arrange
    const sendStub = sinon.stub(sqsModule.SQSClient.prototype, "send")
      .callsFake(async (cmd) => {
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
    expect(result).to.deep.equal({
      batchItemFailures: [{ itemIdentifier: "err2" }],
    });
    expect(sendStub.callCount).to.equal(2, "Should try tracker first, then channel");
  });
});