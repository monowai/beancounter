import {serverEnv} from './common/utils';

const SVC_POSITION = 'SVC_POSITION';
const SVC_DATA = 'SVC_DATA';

export function runtimeConfig(): BcOptions {
  return typeof window !== 'undefined' && window.env !== 'undefined'
    ? {
        // client
        bcPositions: window.env
          ? window.env.bcPositions
          : serverEnv(SVC_POSITION, 'http://localhost:9500'),
        bcData: window.env ? window.env.bcData : serverEnv(SVC_DATA, 'http://localhost:9600')
      }
    : {
        // server
        bcPositions: serverEnv(SVC_POSITION, 'http://localhost:9500'),
        bcData: serverEnv(SVC_DATA, 'http://localhost:9600')
      };
}
