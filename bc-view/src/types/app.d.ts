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
  results: string[];
  hasHeader: boolean;
  portfolio: Portfolio;
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
