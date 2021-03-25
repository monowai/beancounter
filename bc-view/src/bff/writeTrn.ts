import express from "express";
import kafka from "kafka-node";
import { bcConfig } from "../common/config";
import { TransactionUpload } from "../types/app";

function writeTrn(trnRequest: TransactionUpload): void {
  try {
    const client = new kafka.KafkaClient({ kafkaHost: bcConfig.kafkaUrl });
    const HighLevelProducer = kafka.HighLevelProducer;
    const producer = new HighLevelProducer(client);
    const payloads = [
      {
        topic: bcConfig.topicCsvTrn,
        messages: JSON.stringify(trnRequest),
      },
    ];
    producer.send(payloads, (err) => {
      if (err) {
        console.error(
          "[kafka-producer -> %s]: broker send failed. %s",
          bcConfig.topicCsvTrn,
          err.message
        );
      }
    });
  } catch (e) {
    console.error("%s", e.toString());
  }
}

export function postKafkaTrn(req: express.Request, res: express.Response): void {
  writeTrn(req.body);
  res.send("OK");
}
