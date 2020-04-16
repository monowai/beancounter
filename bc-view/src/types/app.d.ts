export interface BcOptions {
  bcPositions: string;
  bcData: string;
  kafkaUrl: string;
  topicCsvTrn: string;
  kcUrl: string;
  kcClient: string;
  kcRealm: string;
}

export interface KcState {
  isAuthenticated: boolean;
}
