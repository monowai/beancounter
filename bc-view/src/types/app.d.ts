export interface BcOptions {
  bcPositions: string;
  bcData: string;
  kcUrl: string;
  kafkaUrl: string;
  kcClient: string;
  kcRealm: string;
}

export interface KcState {
  isAuthenticated: boolean;
}
