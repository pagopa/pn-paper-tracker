import { SQSClient, SendMessageCommand } from "@aws-sdk/client-sqs";

const sqs = new SQSClient({});

const VALID_PRODUCTS = ["AR", "RS", "890", "RIR", "RIS"];

/**
 * Parse delle liste separate da virgole dalle variabili di ambiente
 * @param {string} envVar - Variabile di ambiente da parsare
 * @returns {string[]} Array di prodotti
 */
export const parseEnvList = (envVar) => {
  return (envVar || "")
    .split(",")
    .map((item) => item.trim())
    .filter((item) => item !== "");
}

/**
 * Parse della configurazione tracker con modalità (RUN/DRY)
 * @param {string} envVar - Variabile di ambiente nel formato "AR:RUN,RS:DRY,890:DRY"
 * @returns {object} Oggetto con prodotti abilitati e modalità dry run
 */
export const parseTrackerConfig = (envVar) => {
  const config = {
    trackerEnabledProducts: [],
    trackerDryRunProducts: []
  };

  const items = parseEnvList(envVar);
  
  for (const item of items) {
    const [product, mode] = item.split(":").map(part => part.trim());
    
    if(!VALID_PRODUCTS.includes(product)) {
      throw new Error(`Invalid productType in tracker configuration: ${product}`);
    }
    if(!["DRY", "RUN"].includes(mode)) {
      throw new Error(`Invalid dryRun mode in tracker configuration: ${mode}`);
    }

    config.trackerEnabledProducts.push(product);
    if (mode === "DRY") {
      config.trackerDryRunProducts.push(product);
    }
  }

  return config;
}

// Confingurazione prodotti dalle variabili di ambiente
export const CONFIG = {
  ...parseTrackerConfig(process.env.PAPER_TRACKER_ENABLED_PRODUCTS),
  paperChannelEnabledProducts: parseEnvList(process.env.PAPER_CHANNEL_ENABLED_PRODUCTS),
  queueTracker: process.env.QUEUE_URL_PAPER_TRACKER,
  queuePaperChannel: process.env.QUEUE_URL_PAPER_CHANNEL
};

/**
 * Valida la getEnvConfingurazione e le variabili di ambiente
 */
export const validateConfig = () => {
  if (!CONFIG.queueTracker || !CONFIG.queuePaperChannel) {
    throw new Error(
      "Missing required queue URLs: QUEUE_PAPER_TRACKER, QUEUE_PAPER_CHANNEL"
    );
  }
}

/**
 * Determina la modalità di processing per un prodotto
 * @param {string} productType - Tipo di prodotto
 * @returns {object} getEnvConfingurazione del prodotto
 */
export const getProductConfig = (productType) => {
  const type = productType ?? "UNKNOWN";

  return {
    isTrackerEnabled: CONFIG.trackerEnabledProducts.includes(type),
    isDryRun: CONFIG.trackerDryRunProducts.includes(type),
    isPaperChannelEnabled: CONFIG.paperChannelEnabledProducts.includes(type),
  };
};

/**
 * Crea gli attributi del messaggio copiando quelli originali
 * @param {object} originalAttributes - Attributi originali del record SQS
 * @param {boolean} addDryRun - Se aggiungere l'attributo dryRun
 * @returns {object} Attributi del messaggio
 */
export const createMessageAttributes = (originalAttributes = {}, addDryRun = false) => {
  const attributes = {};

  // Copia attributi originali
    // Copia attributi originali
    for (const [key, attr] of Object.entries(originalAttributes)) {
      // Saltiamo reworkId qui perché verrà sovrascritto esplicitamente dopo
      if (key === 'dryRun') continue;

      attributes[key] = {
        DataType: attr.dataType || attr.DataType || 'String', // Gestisce sia input camelCase che PascalCase
        StringValue: attr.stringValue || attr.StringValue
      };
    }

  // Aggiunge dryRun se richiesto
  if (addDryRun) {
    attributes["dryRun"] = {
      DataType: "String",
      StringValue: "true",
    };
  }

  return attributes;
}

/**
 * Invia un messaggio a una coda SQS
 * @param {string} queueUrl - URL della coda
 * @param {object} messageBody - Corpo del messaggio
 * @param {object} messageAttributes - Attributi del messaggio
 * @returns {Promise} Promise dell'invio
 */
 const sendToQueue = async (queueUrl, messageBody, messageAttributes) => {
  return await sqs.send(
    new SendMessageCommand({
      QueueUrl: queueUrl,
      MessageBody: JSON.stringify(messageBody),
      MessageAttributes: messageAttributes,
    })
  );
}

/**
 * Log strutturato per operazioni
 * @param {string} level - Livello di log
 * @param {string} messageId - ID del messaggio
 * @param {object} details - Dettagli aggiuntivi
 */
const logOperation = (level, messageId, details = {}) =>{
  const logData = {
    level,
    messageId,
    ...details,
  };

  if (level === "ERROR") {
    console.error(JSON.stringify(logData));
  } else if (level === "DEBUG") {
    console.debug(JSON.stringify(logData));
  } else {
    console.log(JSON.stringify(logData));
  }
}

/**
 * Processa un singolo record SQS
 */
const processRecord = async (record) => {
  const { messageId } = record;

  try {
    logOperation("INFO", messageId, {
      message: "Processing message",
      record
    });

    // Parse del JSON
    let body;
    try {
      body = JSON.parse(record.body);
    } catch (err) {
      logOperation("ERROR", messageId, {
        error: "Invalid JSON",
        rawBody: record.body,
      });
      throw err;
    }

    const productType = body?.analogMail?.productType;

//    if (!VALID_PRODUCTS.includes(productType)) {
//      logOperation("ERROR", messageId, {
//        error: `Invalid productType: ${productType}`,
//      });
//      throw new Error("Invalid productType");
//    }

    let config = getProductConfig(productType);

    // config per il prodotto ricevuto
    const { isPaperChannelEnabled, isTrackerEnabled, isDryRun } = config;

    // Header base
    const baseAttributes = createMessageAttributes(record.messageAttributes);

    // Inoltra il messaggio a pn-paper-tracker se abilitato
    if (isTrackerEnabled) {
      let trackerAttributes = baseAttributes;
      if (isDryRun) {
        trackerAttributes = createMessageAttributes(baseAttributes, true);
      }
      await sendToQueue(CONFIG.queueTracker, body, trackerAttributes);
    }

    // Inoltra il messaggio a pn-paper-channel se abilitato
    if (isPaperChannelEnabled) {
      await sendToQueue(CONFIG.queuePaperChannel, body, baseAttributes);
    }

    logOperation("DEBUG", messageId, {
      message: "Message processed successfully",
    });
  } catch (error) {
    // Determina quale coda è fallita per il logging
    const failedQueue = error.message?.toLowerCase().includes("tracker")
      ? CONFIG.queueTracker.split("/").pop()
      : CONFIG.queuePaperChannel.split("/").pop();

    logOperation("ERROR", messageId, {
      queue: failedQueue,
      status: "KO",
      config: CONFIG,
      record,
      error: error.message || String(error),
    });

    throw new Error(
      `Errore nell'inoltro del messaggio ${messageId}: ${error.message}`
    );
  }
}

/**
 * Handler principale AWS Lambda con supporto per Partial Batch Response
 */
export const handler = async (event) => {
  validateConfig();

  const batchItemFailures = [];

  // Processa tutti i record, collezionando i fallimenti
  for (const record of event.Records) {
    try {
      await processRecord(record);
    } catch (error) {
      batchItemFailures.push({
        itemIdentifier: record.messageId,
      });

      logOperation("ERROR", record.messageId, {
        message: "Record added to batch failures for retry",
        error: error.message,
      });
    }
  }

  // Se ci sono fallimenti, ritorna la lista per retry parziale
  if (batchItemFailures.length > 0) {
    return {
      batchItemFailures: batchItemFailures,
    };
  }

  return { status: "ok" };
};
