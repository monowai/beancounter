import winston from "winston";
import { TransformableInfo } from "logform";

const logger = winston.createLogger({
  level: "info",
  format: winston.format.combine(
    winston.format.colorize(),
    winston.format.timestamp(),
    winston.format.splat(),
    winston.format.align(),
    winston.format.printf(
      (info: TransformableInfo) => `${info.timestamp} ${info.level}: ${info.message}`
    )
  ),
  transports: [
    //
    // - Write to all logs with level `info` and below to `combined.log`
    // - Write all logs error (and below) to `error.log`.
    //
    // new winston.transports.File({ filename: 'combined.log' }),
    new winston.transports.Console({ level: "debug" }),
  ],
  //exceptionHandlers: [new transports.File({ filename: "exceptions.log" })]
});

export default logger;
