import { useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
} from '@mui/material';
import type { SessionCreateRequest } from '@eduhub/api-client';
import { apiErrorMessage } from '../../api/apiError';

interface Props {
  open: boolean;
  saving: boolean;
  onSubmit: (value: SessionCreateRequest) => Promise<unknown>;
  onClose: () => void;
}

/** 회차 추가 폼 (FR-03). 정원은 1 이상의 정수, 시작일은 오늘 이후. 서버도 같은 규칙으로 다시 검사한다. */
export function SessionFormDialog({ open, saving, onSubmit, onClose }: Props) {
  const [startDate, setStartDate] = useState('');
  const [place, setPlace] = useState('');
  const [capacity, setCapacity] = useState('20');
  const [error, setError] = useState<string>();

  useEffect(() => {
    if (open) {
      setStartDate('');
      setPlace('');
      setCapacity('20');
      setError(undefined);
    }
  }, [open]);

  const capacityNumber = Number(capacity);
  const capacityValid = Number.isInteger(capacityNumber) && capacityNumber >= 1;
  const valid = startDate !== '' && place.trim() !== '' && capacityValid;

  const submit = async () => {
    setError(undefined);
    try {
      await onSubmit({ startDate, place: place.trim(), capacity: capacityNumber });
      onClose();
    } catch (e) {
      setError(apiErrorMessage(e));
    }
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>회차 추가</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {error && <Alert severity="error">{error}</Alert>}
          <TextField
            label="시작일"
            type="date"
            required
            value={startDate}
            onChange={(e) => setStartDate(e.target.value)}
            slotProps={{ inputLabel: { shrink: true } }}
          />
          <TextField
            label="장소"
            required
            value={place}
            onChange={(e) => setPlace(e.target.value)}
            slotProps={{ htmlInput: { maxLength: 100 } }}
          />
          <TextField
            label="정원"
            type="number"
            required
            value={capacity}
            onChange={(e) => setCapacity(e.target.value)}
            error={capacity !== '' && !capacityValid}
            helperText={capacity !== '' && !capacityValid ? '정원은 1 이상이어야 합니다.' : ' '}
            slotProps={{ htmlInput: { min: 1, step: 1 } }}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>취소</Button>
        <Button variant="contained" onClick={submit} disabled={!valid || saving}>
          추가
        </Button>
      </DialogActions>
    </Dialog>
  );
}
