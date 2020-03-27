import { BcResult } from "./beancounter";

export function isDone(bcResult: BcResult<any>): boolean {
  return bcResult.data || bcResult.error;
}
