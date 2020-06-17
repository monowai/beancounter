import { BcResult } from "./app";

export function isDone(bcResult: BcResult<any>): boolean {
  return bcResult.data || bcResult.error;
}
