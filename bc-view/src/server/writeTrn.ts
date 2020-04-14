import { TransactionUpload } from "../types/beancounter";
import logger from "../common/configLogging";
import express from "express";
import kafka from "kafka-node";
import { bcConfig } from "../common/config";

const topic = "bc-trn-csv";

function writeTrn(trnRequest: TransactionUpload): void {
  try {
    const client = new kafka.KafkaClient({ kafkaHost: bcConfig.kafkaUrl });
    const HighLevelProducer = kafka.HighLevelProducer;
    const producer = new HighLevelProducer(client);

    const payloads = [
      {
        topic: topic,
        messages: JSON.stringify(trnRequest),
      },
    ];
    producer.send(payloads, (err) => {
      if (err) {
        logger.error("[kafka-producer -> %s]: broker send failed. %s", topic, err.message);
      }
    });
  } catch (e) {
    logger.error("%s", e.toString());
  }
}

export function postKafkaTrn(req: express.Request, res: express.Response): void {
  writeTrn(req.body);
  res.send("OK");
}
