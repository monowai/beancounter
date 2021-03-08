import { BcResult } from "./app";

export function isDone(bcResult: BcResult<any>): boolean {
  return bcResult && (bcResult.data || bcResult.error);
}
