import { TransactionUpload } from "../types/beancounter";
import logger from "../common/configLogging";
import express from "express";
import kafka from "kafka-node";

const topic = "bc-trn-csv";

function writeTrn(trnRequest: TransactionUpload): void {
  try {
    const client = new kafka.KafkaClient({ kafkaHost: "kafka:9092" });
    const HighLevelProducer = kafka.HighLevelProducer;
    const producer = new HighLevelProducer(client);

    const payloads = [
      {
        topic: topic,
        messages: JSON.stringify(trnRequest),
      },
    ];
    //producer.on("ready", () => {
    producer.send(payloads, (err) => {
      if (err) {
        logger.error("[kafka-producer -> %s]: broker send failed. %s", topic, err.message);
      }
    });
    //});
  } catch (e) {
    logger.error("%s", e.toString());
  }
}

export function postKafkaTrn(req: express.Request, res: express.Response) {
  writeTrn(req.body);
  res.send("OK");
}
