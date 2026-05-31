export interface ActionPayload {
  actionId: string;
  protocol: 'REST' | 'SHELL' | 'GRPC';
  instruction: string;
  method: string;
  payload: string;
  authMechanism: string;
  timeoutSeconds: number;
  isIdempotent: boolean;
  maxRetries: number;
  expectedStatusCode: number;
}

export interface StatusPayload {
  actionId: string;
  status: 'IN_PROGRESS' | 'COMPLETED' | 'FAILED_HTTP' | 'FAILED_TIMEOUT' | 'FAILED_AUTH' | 'FAILED_INTERNAL';
  errorMessage: string | null;
  observedStatusCode: number;
}

export interface GraphUpdatePayload {
  resourceIri: string;
  ontologyType: string;
  changeKind: 'CREATED' | 'UPDATED' | 'STATE_CHANGED' | 'DELETED';
  correlationId: string;
}

export type TelemetryPayload = ActionPayload | StatusPayload | GraphUpdatePayload | string;

export interface TelemetryEvent {
  type: 'action' | 'status' | 'graph.updates' | 'system' | 'monitor' | 'analyze' | 'execute';
  payload: TelemetryPayload;
  timestamp: number;
}

export interface LogEntry {
  time: string;
  tag: 'system' | 'monitor' | 'analyze' | 'execute' | 'plan';
  msg: string;
}
