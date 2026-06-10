export const LAYER_TYPES = {
  BASE: 'base',
  LIQUIDITY: 'liquidity',
  LATENCY: 'latency',
  HEALTH: 'health',
  AI: 'ai',
  EXECUTION: 'execution',
};

export const LAYER_DECORATOR_NAMES = {
  base: 'FundingApiCandidateSourceService',
  liquidity: 'LiquidityAssessmentService',
  latency: 'VenueLatencyService',
  health: 'EngineExecutionService',
  ai: 'AiSignalAdvisorService',
  execution: 'EngineExecutionService',
};
