import { useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Stack,
  TextField,
} from '@mui/material';
import type { CourseCreateRequest } from '@eduhub/api-client';
import { apiErrorMessage } from '../../api/apiError';

// 분류 조회 API가 아직 없어서 고정한다 (CourseListPage와 같은 목록. 화면을 합칠 때 한 곳으로 옮긴다)
const CATEGORIES = ['BACKEND', 'FRONTEND', 'DEVOPS', 'AI', 'COMMON'];

interface Props {
  open: boolean;
  title: string; // 다이얼로그 제목 ("과정 등록" / "과정 수정")
  initial?: CourseCreateRequest;
  saving: boolean;
  onSubmit: (value: CourseCreateRequest) => Promise<unknown>;
  onClose: () => void;
}

const EMPTY: CourseCreateRequest = { title: '', category: '', description: '' };

/** 과정 등록·수정 폼. 등록과 수정의 입력 항목이 같아서 함께 쓴다. */
export function CourseFormDialog({ open, title, initial, saving, onSubmit, onClose }: Props) {
  const [value, setValue] = useState<CourseCreateRequest>(EMPTY);
  const [error, setError] = useState<string>();

  // 다이얼로그를 열 때만 초기값으로 채운다 (열려 있는 동안 입력한 값을 덮어쓰지 않도록 open만 본다)
  useEffect(() => {
    if (open) {
      setValue({ ...EMPTY, ...initial, description: initial?.description ?? '' });
      setError(undefined);
    }
  }, [open]);

  const valid = value.title.trim() !== '' && value.category !== '';

  const submit = async () => {
    setError(undefined);
    try {
      await onSubmit({
        title: value.title.trim(),
        category: value.category,
        description: value.description?.trim() || undefined,
      });
      onClose();
    } catch (e) {
      setError(apiErrorMessage(e));
    }
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {error && <Alert severity="error">{error}</Alert>}
          <TextField
            label="과정명"
            required
            value={value.title}
            onChange={(e) => setValue({ ...value, title: e.target.value })}
            slotProps={{ htmlInput: { maxLength: 200 } }}
          />
          <TextField
            select
            label="분류"
            required
            value={value.category}
            onChange={(e) => setValue({ ...value, category: e.target.value })}
          >
            {CATEGORIES.map((c) => (
              <MenuItem key={c} value={c}>
                {c}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            label="설명"
            multiline
            minRows={3}
            value={value.description}
            onChange={(e) => setValue({ ...value, description: e.target.value })}
            slotProps={{ htmlInput: { maxLength: 2000 } }}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>취소</Button>
        <Button variant="contained" onClick={submit} disabled={!valid || saving}>
          저장
        </Button>
      </DialogActions>
    </Dialog>
  );
}
