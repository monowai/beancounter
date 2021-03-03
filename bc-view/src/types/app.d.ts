import { Portfolio } from "./beancounter";

export interface BcOptions {
  bcPositions: string;
  bcData: string;
  kafkaUrl: string;
  topicCsvTrn: string;
  kcUrl: string;
  kcClient: string;
  kcRealm: string;
}

interface TransactionUpload {
  portfolio: Portfolio;
  row: string[];
}

export interface DelimitedImport {
  hasHeader: boolean;
  portfolio: Portfolio;
  purge: boolean;
  results: string[];
  token: string | undefined;
}

declare global {
  interface Window {
    initialI18nStore: any;
    initialLanguage: any;
    env: any;
  }
}

interface BcResult<T> {
  data: T | any;
  error: AxiosError | any;
}

export interface DevMessage {
  debug: boolean;
  errorMessage: string;
  token: string;
}
