import { useCallback, useState } from 'react';
import type { CompletionRequestOutcomeEnum, CompletionResult } from '@eduhub/api-client';
import { adminSessionApi } from '../../api/clients';

/** 수료·미수료 처리(FR-08) 훅. 실패하면 오류를 그대로 던지므로 화면이 문구를 고른다. */
export function useCompletion(sessionId: number) {
  const [processing, setProcessing] = useState(false);

  const complete = useCallback(
    async (employeeIds: number[], outcome: CompletionRequestOutcomeEnum): Promise<CompletionResult> => {
      setProcessing(true);
      try {
        const response = await adminSessionApi.complete({ sessionId, completionRequest: { employeeIds, outcome } });
        return response.data;
      } finally {
        setProcessing(false);
      }
    },
    [sessionId],
  );

  return { complete, processing };
}
